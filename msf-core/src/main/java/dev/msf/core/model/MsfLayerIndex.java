package dev.msf.core.model;

import dev.msf.core.MsfCompressionException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfWarning;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.compression.RegionCompressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The layer index block — defines all semantic construction layers and their
 * inline
 * region data. Required in all MSF files.
 *
 * <h2>Block layout (Section 6.2)</h2>
 * 
 * <pre>
 *     u32     block length
 *     u8      layer count (1–255)
 *       [per layer — see {@link MsfLayer}]
 * </pre>
 *
 * <h2>Key invariants (Section 6.4)</h2>
 * <ul>
 * <li>Layer count is a u8; minimum 1, maximum 255.</li>
 * <li>Layer IDs MUST be unique within a file.</li>
 * <li>Each layer MUST contain at least one region.</li>
 * </ul>
 *
 * @param layers unmodifiable list of layers; at least one required
 *
 * @see MsfSpec Section 6 — layer index block
 * @see MsfSpec Section 7 — region data
 */
public record MsfLayerIndex(List<MsfLayer> layers) {

    /** Maximum number of layers (u8 ceiling). */
    public static final int MAX_LAYERS = 255;

    public MsfLayerIndex {
        layers = Collections.unmodifiableList(new ArrayList<>(layers));
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Serializes this layer index block to bytes, including the 4-byte
     * {@code block_length} prefix.
     *
     * @param paletteSize     the global palette entry count (passed through to
     *                        region encoding)
     * @param compressionType the compression algorithm for region payloads
     * @param hasBiomes       whether to include biome data in region payloads
     *                        (feature flag bit 2)
     * @param warningConsumer warning consumer; may be null
     * @return the complete layer index block bytes including the block_length
     *         prefix
     * @throws MsfCompressionException  if any region compression fails
     * @throws IllegalArgumentException if layer count constraints are violated
     */
    public byte[] toBytes(
            int paletteSize,
            CompressionType compressionType,
            boolean hasBiomes,
            Consumer<MsfWarning> warningConsumer) throws MsfCompressionException {
        return toBytes(paletteSize, compressionType, RegionCompressor.DEFAULT_ZSTD_LEVEL,
                hasBiomes, warningConsumer);
    }

    /**
     * Serializes the layer index block to bytes using the given compression type and level.
     *
     * @param compressionLevel compression level (meaningful for ZSTD only; ignored otherwise)
     */
    public byte[] toBytes(
            int paletteSize,
            CompressionType compressionType,
            int compressionLevel,
            boolean hasBiomes,
            Consumer<MsfWarning> warningConsumer) throws MsfCompressionException {
        int layerCount = layers.size();
        if (layerCount < 1) {
            throw new IllegalArgumentException(
                    "MSF file must contain at least one layer (Section 6.4)");
        }
        if (layerCount > MAX_LAYERS) {
            throw new IllegalArgumentException(String.format(
                    "Field 'layerCount' value %d exceeds the maximum permitted value of %d",
                    layerCount, MAX_LAYERS));
        }

        // Build the block body
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            // u8 layer count
            body.write(layerCount & 0xFF);
            // Per-layer data
            for (MsfLayer layer : layers) {
                body.write(layer.toBytes(paletteSize, compressionType, compressionLevel,
                        hasBiomes, warningConsumer));
            }
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream threw unexpectedly", e);
        }

        byte[] bodyBytes = body.toByteArray();

        // Prefix with block_length (u32)
        ByteBuffer result = ByteBuffer.allocate(4 + bodyBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(bodyBytes.length);
        result.put(bodyBytes);
        return result.array();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses a layer index block from the given byte array, starting at the block
     * offset.
     *
     * @param data            the file bytes
     * @param offset          absolute byte offset of the start of the layer index
     *                        block
     * @param paletteSize     the global palette entry count (for region validation)
     * @param hasBiomes       whether biome data is expected in regions (feature
     *                        flag bit 2)
     * @param warningConsumer warning consumer; may be null
     * @return the parsed {@link MsfLayerIndex}
     * @throws MsfParseException       if the block is malformed
     * @throws MsfCompressionException if region decompression fails
     */
    public static MsfLayerIndex fromBytes(
            byte[] data,
            int offset,
            int paletteSize,
            boolean hasBiomes,
            Consumer<MsfWarning> warningConsumer) throws MsfParseException, MsfCompressionException {
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset)
                .order(ByteOrder.LITTLE_ENDIAN);

        // u32 block_length
        @SuppressWarnings("unused")
        long blockLength = Integer.toUnsignedLong(buf.getInt());

        // u8 layer count
        int layerCount = Byte.toUnsignedInt(buf.get());
        if (layerCount == 0) {
            throw new MsfParseException(
                    "Layer index block has layer count 0 — MSF files must contain at least one layer (Section 6.4)");
        }

        List<MsfLayer> layers = new ArrayList<>(layerCount);
        Set<Integer> seenLayerIds = new HashSet<>();

        for (int i = 0; i < layerCount; i++) {
            MsfLayer layer = MsfLayer.fromBuffer(buf, paletteSize, hasBiomes, warningConsumer);
            int id = layer.layerId();
            if (!seenLayerIds.add(id)) {
                throw new MsfParseException(
                        "Duplicate layer ID " + id + " at layer index " + i
                                + " — layer IDs must be unique (Section 6.4)");
            }
            layers.add(layer);
        }

        return new MsfLayerIndex(layers);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates an {@link MsfLayerIndex} from a list of layers.
     *
     * @param layers the layers; must not be empty; layer IDs must be unique
     * @return a new {@link MsfLayerIndex}
     * @throws IllegalArgumentException if the list is empty, has more than 255
     *                                  layers,
     *                                  or has duplicate layer IDs
     */
    public static MsfLayerIndex of(List<MsfLayer> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException(
                    "MSF file must contain at least one layer (Section 6.4)");
        }
        if (layers.size() > MAX_LAYERS) {
            throw new IllegalArgumentException(String.format(
                    "Field 'layerCount' value %d exceeds the maximum permitted value of %d",
                    layers.size(), MAX_LAYERS));
        }
        Set<Integer> seen = new HashSet<>();
        for (MsfLayer layer : layers) {
            if (!seen.add(layer.layerId())) {
                throw new IllegalArgumentException(
                        "Duplicate layer ID " + layer.layerId() + " — layer IDs must be unique (Section 6.4)");
            }
        }
        return new MsfLayerIndex(layers);
    }
}

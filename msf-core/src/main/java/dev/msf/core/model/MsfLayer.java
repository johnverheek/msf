package dev.msf.core.model;

import dev.msf.core.MsfCompressionException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfWarning;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.compression.RegionCompressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A semantic construction layer containing one or more regions.
 *
 * <h2>Layer layout within the layer index block (Section 6.2)</h2>
 * 
 * <pre>
 *     u8      layer ID
 *     str     layer name
 *     u8      construction order index
 *     u8      dependency count
 *       u8[]  dependent layer IDs
 *     u8      flags
 *     u8      region count
 *       ...   regions (see Section 7)
 * </pre>
 *
 * <h2>Layer flags (Section 6.3)</h2>
 * 
 * <pre>
 *     Bit 0:  optional layer
 *     Bit 1:  draft
 *     Bits 2–7: reserved, MUST be 0
 * </pre>
 *
 * @see MsfSpec Section 6 — layer index block
 */
public final class MsfLayer {

    // -------------------------------------------------------------------------
    // Flag constants
    // -------------------------------------------------------------------------

    /** Layer flag bit 0 — optional layer (may be skipped during placement). */
    public static final int FLAG_OPTIONAL = 1 << 0;

    /** Layer flag bit 1 — draft (incomplete, not ready for placement). */
    public static final int FLAG_DRAFT = 1 << 1;

    /** Mask of all defined layer flag bits (0–1). */
    public static final int FLAGS_DEFINED_MASK = 0x03;

    /** Mask of reserved layer flag bits (2–7). */
    public static final int FLAGS_RESERVED_MASK = 0xFC;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Author-assigned layer ID; MUST be unique within a file (Section 6.4). */
    private final int layerId;

    /** Human-readable layer name. */
    private final String name;

    /** Construction order index — lower values are placed first (Section 6.4). */
    private final int constructionOrderIndex;

    /** IDs of layers that MUST be placed before this layer (Section 6.4). */
    private final List<Integer> dependencyIds;

    /**
     * Layer flags bitmask — stored as the sanitized value (reserved bits cleared).
     * Valid bits: 0 (optional), 1 (draft).
     */
    private final int flags;

    /** Regions belonging to this layer; at least one required (Section 6.4). */
    private final List<MsfRegion> regions;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private MsfLayer(
            int layerId,
            String name,
            int constructionOrderIndex,
            List<Integer> dependencyIds,
            int flags,
            List<MsfRegion> regions) {
        this.layerId = layerId;
        this.name = name;
        this.constructionOrderIndex = constructionOrderIndex;
        this.dependencyIds = Collections.unmodifiableList(new ArrayList<>(dependencyIds));
        this.flags = flags;
        this.regions = Collections.unmodifiableList(new ArrayList<>(regions));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int layerId() {
        return layerId;
    }

    public String name() {
        return name;
    }

    public int constructionOrderIndex() {
        return constructionOrderIndex;
    }

    public List<Integer> dependencyIds() {
        return dependencyIds;
    }

    public int flags() {
        return flags;
    }

    public boolean isOptional() {
        return (flags & FLAG_OPTIONAL) != 0;
    }

    public boolean isDraft() {
        return (flags & FLAG_DRAFT) != 0;
    }

    public List<MsfRegion> regions() {
        return regions;
    }

    // -------------------------------------------------------------------------
    // Factory (builder)
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int layerId;
        private String name = "";
        private int constructionOrderIndex;
        private List<Integer> dependencyIds = new ArrayList<>();
        private int flags;
        private List<MsfRegion> regions = new ArrayList<>();

        private Builder() {
        }

        public Builder layerId(int id) {
            this.layerId = id;
            return this;
        }

        public Builder name(String n) {
            this.name = n;
            return this;
        }

        public Builder constructionOrderIndex(int i) {
            this.constructionOrderIndex = i;
            return this;
        }

        public Builder dependencyIds(List<Integer> deps) {
            this.dependencyIds = new ArrayList<>(deps);
            return this;
        }

        public Builder addDependency(int id) {
            this.dependencyIds.add(id);
            return this;
        }

        public Builder flags(int f) {
            this.flags = f;
            return this;
        }

        public Builder regions(List<MsfRegion> r) {
            this.regions = new ArrayList<>(r);
            return this;
        }

        public Builder addRegion(MsfRegion r) {
            this.regions.add(r);
            return this;
        }

        public MsfLayer build() {
            if (regions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Layer '" + name + "' must have at least one region (Section 6.4)");
            }
            if (regions.size() > 255) {
                throw new IllegalArgumentException(String.format(
                        "Field 'regionCount' value %d exceeds the maximum permitted value of 255",
                        regions.size()));
            }
            // Store flags as-is; masking and warning happen during toBytes() per spec
            // Section 6.3.
            // The flags() accessor returns the raw value so callers can inspect what was
            // provided.
            return new MsfLayer(layerId, name, constructionOrderIndex, dependencyIds,
                    flags, regions);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Serializes this layer (header + inline region data) to bytes.
     * The serialized form is embedded within the layer index block body.
     *
     * @param paletteSize     the global palette size (passed to region
     *                        serialization)
     * @param compressionType the compression algorithm for region payloads
     * @param hasBiomes       whether to include biome data (feature flag bit 2)
     * @param warningConsumer warning consumer; may be null
     * @return the layer bytes
     * @throws MsfCompressionException if region compression fails
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
     * Serializes this layer to bytes using the given compression type and level.
     *
     * @param compressionLevel compression level (meaningful for ZSTD only; ignored otherwise)
     */
    public byte[] toBytes(
            int paletteSize,
            CompressionType compressionType,
            int compressionLevel,
            boolean hasBiomes,
            Consumer<MsfWarning> warningConsumer) throws MsfCompressionException {
        // Validate and sanitize flags (Section 6.3)
        int rawFlags = flags;
        int sanitized = rawFlags & FLAGS_DEFINED_MASK;
        if (sanitized != rawFlags && warningConsumer != null) {
            warningConsumer.accept(MsfWarning.writeWarning(
                    MsfWarning.Code.RESERVED_FLAG_CLEARED,
                    String.format(
                            "Layer '%s' (ID %d): reserved layer flag bits were cleared: "
                                    + "provided 0x%02X, writing 0x%02X",
                            name, layerId, rawFlags & 0xFF, sanitized & 0xFF)));
        }

        // Validate region count
        if (regions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Layer '" + name + "' has 0 regions — MUST have at least 1 (Section 6.4)");
        }
        if (regions.size() > 255) {
            throw new IllegalArgumentException(String.format(
                    "Field 'regionCount' value %d exceeds the maximum permitted value of 255",
                    regions.size()));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // u8 layer ID
            out.write(layerId & 0xFF);
            // str layer name
            writeStr(out, name);
            // u8 construction order index
            out.write(constructionOrderIndex & 0xFF);
            // u8 dependency count + u8[] dependency IDs
            out.write(dependencyIds.size() & 0xFF);
            for (int dep : dependencyIds) {
                out.write(dep & 0xFF);
            }
            // u8 flags (sanitized)
            out.write(sanitized & 0xFF);
            // u8 region count
            out.write(regions.size() & 0xFF);
            // Inline region data
            for (MsfRegion region : regions) {
                out.write(region.toBytes(paletteSize, compressionType, compressionLevel, hasBiomes));
            }
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream threw unexpectedly", e);
        }
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses a layer from the given buffer, positioned at the start of the layer
     * data
     * (u8 layer ID). The buffer position is advanced past all consumed bytes.
     *
     * @param buf             the buffer positioned at the layer header
     * @param paletteSize     the global palette entry count
     * @param hasBiomes       whether biome data is expected in regions
     * @param warningConsumer warning consumer; may be null
     * @return the parsed {@link MsfLayer}
     * @throws MsfParseException       if the layer data is malformed
     * @throws MsfCompressionException if region decompression fails
     */
    public static MsfLayer fromBuffer(
            ByteBuffer buf,
            int paletteSize,
            boolean hasBiomes,
            Consumer<MsfWarning> warningConsumer) throws MsfParseException, MsfCompressionException {
        // u8 layer ID
        int layerId = Byte.toUnsignedInt(buf.get());
        // str layer name
        String name = readStr(buf);
        // u8 construction order index
        int constructionOrderIndex = Byte.toUnsignedInt(buf.get());
        // u8 dependency count + u8[] dependency IDs
        int depCount = Byte.toUnsignedInt(buf.get());
        List<Integer> dependencyIds = new ArrayList<>(depCount);
        for (int i = 0; i < depCount; i++) {
            dependencyIds.add(Byte.toUnsignedInt(buf.get()));
        }
        // u8 flags
        int rawFlags = Byte.toUnsignedInt(buf.get());
        int sanitizedFlags = rawFlags;
        if ((rawFlags & FLAGS_RESERVED_MASK) != 0) {
            if (warningConsumer != null) {
                warningConsumer.accept(MsfWarning.atOffset(
                        MsfWarning.Code.RESERVED_FLAG_SET,
                        String.format(
                                "Layer ID %d ('%s'): reserved layer flag bits are set: 0x%02X — "
                                        + "treating reserved bits as 0 (Section 6.3)",
                                layerId, name, rawFlags),
                        buf.position()));
            }
            sanitizedFlags = rawFlags & FLAGS_DEFINED_MASK;
        }

        // u8 region count
        int regionCount = Byte.toUnsignedInt(buf.get());
        if (regionCount == 0) {
            throw new MsfParseException(
                    "Layer ID " + layerId + " ('" + name + "'): region count is 0, which is invalid (Section 6.4)");
        }

        List<MsfRegion> regions = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) {
            regions.add(MsfRegion.fromBuffer(buf, paletteSize, hasBiomes, warningConsumer));
        }

        return new MsfLayer(layerId, name, constructionOrderIndex, dependencyIds, sanitizedFlags, regions);
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    static void writeStr(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes);
    }

    static String readStr(ByteBuffer buf) throws MsfParseException {
        if (buf.remaining() < 2) {
            throw new MsfParseException("Buffer underflow reading string length");
        }
        int len = Short.toUnsignedInt(buf.getShort());
        if (len > buf.remaining()) {
            throw new MsfParseException("String length " + len + " exceeds remaining buffer " + buf.remaining());
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

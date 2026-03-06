package dev.msf.core.model;

import dev.msf.core.MsfCompressionException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfWarning;
import dev.msf.core.codec.BitPackedArray;
import dev.msf.core.codec.BlockDataCodec;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.compression.RegionCompressor;
import dev.msf.core.compression.RegionDecompressor;
import dev.msf.core.util.YzxOrder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A three-dimensional subvolume of block data associated with a layer.
 *
 * <h2>Region header layout (Section 7.1 — V1_N)</h2>
 * <pre>
 *     i32     origin X (relative to schematic anchor)
 *     i32     origin Y (relative to schematic anchor)
 *     i32     origin Z (relative to schematic anchor)
 *     u32     size X
 *     u32     size Y
 *     u32     size Z
 *     u8      compression type
 *     u32     compressed data length
 *     u32     uncompressed data length
 *     u8[]    compressed region payload
 * </pre>
 *
 * <h2>Region payload (Section 7.3)</h2>
 * After decompression:
 * <pre>
 *     u8      bits per entry
 *     u32     packed array length (count of u64 words)
 *     u64[]   packed block data
 *     [biome data — present iff feature flag bit 2 is set]
 * </pre>
 *
 * @see MsfSpec Section 7 — region data
 */
public final class MsfRegion {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Origin X, relative to schematic anchor (Section 7.7). */
    private final int originX;

    /** Origin Y, relative to schematic anchor (Section 7.7). */
    private final int originY;

    /** Origin Z, relative to schematic anchor (Section 7.7). */
    private final int originZ;

    /** Size on the X axis. MUST be ≥ 1 (Section 7.1). */
    private final int sizeX;

    /** Size on the Y axis. MUST be ≥ 1 (Section 7.1). */
    private final int sizeY;

    /** Size on the Z axis. MUST be ≥ 1 (Section 7.1). */
    private final int sizeZ;

    /**
     * Compression type as parsed from the region header (Section 7.2).
     * For regions built programmatically, defaults to {@link CompressionType#ZSTD}.
     */
    private final CompressionType compressionType;

    /**
     * Block data in YZX order. Each entry is a palette ID (0-based index into the global palette).
     * Array length equals {@code sizeX * sizeY * sizeZ}.
     */
    private final int[] blockData;

    /**
     * Biome IDs in YZX-quarter order, or {@code null} when biome data is absent.
     * When present, each entry is a 0-based index into {@link #biomePalette}.
     * Array length equals {@code ceil(sizeX/4) * ceil(sizeY/4) * ceil(sizeZ/4)}.
     */
    private final int[] biomeData;

    /**
     * Local biome palette for this region, or empty when biome data is absent.
     * Biome palette IDs are indices into this list.
     */
    private final List<String> biomePalette;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private MsfRegion(
        int originX, int originY, int originZ,
        int sizeX, int sizeY, int sizeZ,
        CompressionType compressionType,
        int[] blockData,
        int[] biomeData,
        List<String> biomePalette
    ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.compressionType = compressionType;
        this.blockData = Arrays.copyOf(blockData, blockData.length);
        this.biomeData = biomeData == null ? null : Arrays.copyOf(biomeData, biomeData.length);
        this.biomePalette = biomeData == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(biomePalette));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }

    /**
     * Returns the compression type as parsed from the region header (Section 7.2).
     *
     * @return the compression type; never {@code null}
     */
    public CompressionType compressionType() { return compressionType; }

    /** Palette IDs in YZX order. Never null; length = sizeX * sizeY * sizeZ. */
    public int[] blockData() { return Arrays.copyOf(blockData, blockData.length); }

    /** True when this region carries biome data (feature flag bit 2 was set). */
    public boolean hasBiomeData() { return biomeData != null; }

    /**
     * Biome IDs in YZX-quarter order, or {@code null} when {@link #hasBiomeData()} is false.
     * Length = ceil(sizeX/4) * ceil(sizeY/4) * ceil(sizeZ/4).
     */
    public int[] biomeData() { return biomeData == null ? null : Arrays.copyOf(biomeData, biomeData.length); }

    /** Local biome palette; empty when {@link #hasBiomeData()} is false. */
    public List<String> biomePalette() { return biomePalette; }

    // -------------------------------------------------------------------------
    // Factory (builder)
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int originX, originY, originZ;
        private int sizeX = 1, sizeY = 1, sizeZ = 1;
        private CompressionType compressionType = CompressionType.ZSTD;
        private int[] blockData;
        private int[] biomeData;
        private List<String> biomePalette;

        private Builder() {}

        public Builder origin(int x, int y, int z) {
            this.originX = x; this.originY = y; this.originZ = z; return this;
        }
        public Builder size(int x, int y, int z) {
            this.sizeX = x; this.sizeY = y; this.sizeZ = z; return this;
        }
        public Builder compressionType(CompressionType ct) {
            this.compressionType = ct; return this;
        }
        public Builder blockData(int[] data) { this.blockData = Arrays.copyOf(data, data.length); return this; }
        public Builder biomeData(int[] data, List<String> palette) {
            this.biomeData = Arrays.copyOf(data, data.length);
            this.biomePalette = new ArrayList<>(palette);
            return this;
        }

        public MsfRegion build() {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
                throw new IllegalArgumentException(
                    "Region size must be at least 1 on each axis; got: " + sizeX + "x" + sizeY + "x" + sizeZ
                );
            }
            long expected = YzxOrder.blockCount(sizeX, sizeY, sizeZ);
            if (blockData == null) {
                blockData = new int[(int) expected];
            } else if (blockData.length != expected) {
                throw new IllegalArgumentException(
                    "blockData length " + blockData.length + " != expected " + expected
                    + " for region " + sizeX + "x" + sizeY + "x" + sizeZ
                );
            }
            return new MsfRegion(originX, originY, originZ, sizeX, sizeY, sizeZ,
                compressionType, blockData, biomeData, biomePalette);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Serializes this region (header + compressed payload) to bytes.
     *
     * <p>The serialized region is embedded directly inside the layer index block —
     * there is no separate block_length prefix for individual regions. The region header
     * is written starting at the current position in the containing block.
     *
     * @param paletteSize     the global palette entry count (used to compute bits_per_entry)
     * @param compressionType the compression algorithm to use; ZSTD is RECOMMENDED
     * @param hasBiomes       whether to write biome data (must match feature flag bit 2)
     * @return the complete bytes for this region (header + payload)
     * @throws MsfCompressionException if compression fails
     * @throws IllegalArgumentException if hasBiomes is true but biome data is absent
     */
    public byte[] toBytes(int paletteSize, CompressionType compressionType, boolean hasBiomes)
            throws MsfCompressionException {
        if (hasBiomes && !hasBiomeData()) {
            throw new IllegalArgumentException(
                "hasBiomes=true but no biome data is present in this region"
            );
        }

        // Build the uncompressed payload
        byte[] uncompressedPayload = buildPayload(paletteSize, hasBiomes);
        byte[] compressedPayload = RegionCompressor.compress(uncompressedPayload, compressionType);

        // Build the region header bytes (everything before the payload)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // i32: origin X, Y, Z
            writeI32(out, originX);
            writeI32(out, originY);
            writeI32(out, originZ);
            // u32: size X, Y, Z — validated ≥ 1 by builder
            writeU32(out, sizeX);
            writeU32(out, sizeY);
            writeU32(out, sizeZ);
            // u8: compression type
            out.write(compressionType.byteValue & 0xFF);
            // u32: compressed data length
            writeU32(out, compressedPayload.length);
            // u32: uncompressed data length
            writeU32(out, uncompressedPayload.length);
            // u8[]: compressed region payload
            out.write(compressedPayload);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream threw unexpectedly", e);
        }
        return out.toByteArray();
    }

    /**
     * Builds the uncompressed region payload bytes.
     * Layout: bits_per_entry (u8) | packed_array_length (u32) | packed_block_data (u64[])
     * [optional biome section]
     */
    private byte[] buildPayload(int paletteSize, boolean hasBiomes) {
        byte[] blockSection = BlockDataCodec.encode(blockData, paletteSize);

        if (!hasBiomes) {
            return blockSection;
        }

        // Biome section
        byte[] biomeSection = buildBiomeSection();

        byte[] result = new byte[blockSection.length + biomeSection.length];
        System.arraycopy(blockSection, 0, result, 0, blockSection.length);
        System.arraycopy(biomeSection, 0, result, blockSection.length, biomeSection.length);
        return result;
    }

    /**
     * Builds the biome data section of the region payload (Section 7.6).
     */
    private byte[] buildBiomeSection() {
        int bpe = BitPackedArray.bitsPerEntry(biomePalette.size());
        long[] words = BitPackedArray.pack(biomeData, bpe);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeU16(out, biomePalette.size());
            for (String biome : biomePalette) {
                writeStr(out, biome);
            }
            out.write(bpe & 0xFF);
            writeU32(out, words.length);
            ByteBuffer wordBuf = ByteBuffer.allocate(words.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long w : words) wordBuf.putLong(w);
            out.write(wordBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream threw unexpectedly", e);
        }
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses a region from the given buffer, positioned at the start of the region header.
     * The buffer position is advanced past all consumed bytes.
     *
     * @param buf             the buffer positioned at the region header (i32: origin X)
     * @param paletteSize     the global palette entry count (for range validation)
     * @param hasBiomes       whether biome data is expected (feature flag bit 2)
     * @param warningConsumer warning consumer; may be null
     * @return the parsed {@link MsfRegion}
     * @throws MsfParseException       if the region data is malformed
     * @throws MsfCompressionException if decompression fails
     */
    public static MsfRegion fromBuffer(
        ByteBuffer buf,
        int paletteSize,
        boolean hasBiomes,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfParseException, MsfCompressionException {
        // i32: origin X, Y, Z
        int originX = buf.getInt();
        int originY = buf.getInt();
        int originZ = buf.getInt();

        // u32: size X, Y, Z
        long rawSizeX = Integer.toUnsignedLong(buf.getInt());
        long rawSizeY = Integer.toUnsignedLong(buf.getInt());
        long rawSizeZ = Integer.toUnsignedLong(buf.getInt());

        // Validate sizes ≥ 1 (Section 7.1)
        if (rawSizeX < 1 || rawSizeY < 1 || rawSizeZ < 1) {
            throw new MsfParseException(String.format(
                "Region has invalid size %dx%dx%d — each axis must be ≥ 1 (Section 7.1)",
                rawSizeX, rawSizeY, rawSizeZ
            ));
        }
        int sizeX = (int) Math.min(rawSizeX, Integer.MAX_VALUE);
        int sizeY = (int) Math.min(rawSizeY, Integer.MAX_VALUE);
        int sizeZ = (int) Math.min(rawSizeZ, Integer.MAX_VALUE);

        // u8: compression type
        int comprTypeByte = Byte.toUnsignedInt(buf.get());
        CompressionType comprType = CompressionType.fromByte(comprTypeByte);

        // u32: compressed data length
        long compressedLen = Integer.toUnsignedLong(buf.getInt());
        // u32: uncompressed data length
        long uncompressedLen = Integer.toUnsignedLong(buf.getInt());

        if (compressedLen > buf.remaining()) {
            throw new MsfParseException(String.format(
                "Region: compressed_data_length %d exceeds remaining buffer %d",
                compressedLen, buf.remaining()
            ));
        }
        if (uncompressedLen > Integer.MAX_VALUE) {
            throw new MsfParseException(
                "Region: uncompressed_data_length " + uncompressedLen + " exceeds Integer.MAX_VALUE"
            );
        }

        // Read compressed payload
        byte[] compressed = new byte[(int) compressedLen];
        buf.get(compressed);

        // Decompress
        byte[] payload = RegionDecompressor.decompress(compressed, comprType, (int) uncompressedLen);
        if (payload.length != (int) uncompressedLen) {
            throw new MsfParseException(String.format(
                "Region: decompressed length %d != declared uncompressed_data_length %d",
                payload.length, uncompressedLen
            ));
        }

        // Parse payload
        ByteBuffer payloadBuf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        long blockCount = YzxOrder.blockCount(sizeX, sizeY, sizeZ);
        int[] blockData = BlockDataCodec.decode(payloadBuf, blockCount, paletteSize);

        // Biome data (Section 7.3 / Section 7.6)
        int[] biomeData = null;
        List<String> biomePalette = null;

        if (hasBiomes) {
            if (!payloadBuf.hasRemaining()) {
                throw new MsfParseException(
                    "Region: feature flag bit 2 (HAS_BIOME_DATA) is set but "
                    + "no biome data found in region payload (Section 7.3)"
                );
            }
            int biomePaletteCount = Short.toUnsignedInt(payloadBuf.getShort());
            if (biomePaletteCount == 0) {
                throw new MsfParseException(
                    "Region: biome palette entry count is 0, which is invalid (Section 7.6)"
                );
            }
            biomePalette = new ArrayList<>(biomePaletteCount);
            for (int i = 0; i < biomePaletteCount; i++) {
                biomePalette.add(readStr(payloadBuf));
            }

            int biomeBpe = Byte.toUnsignedInt(payloadBuf.get());
            if (biomeBpe == 0) {
                throw new MsfParseException(
                    "Region: biome bits_per_entry is 0, which is invalid (Section 7.6)"
                );
            }
            long storedBiomeWordCount = Integer.toUnsignedLong(payloadBuf.getInt());

            long beCX = divCeil(sizeX, 4);
            long beCY = divCeil(sizeY, 4);
            long beCZ = divCeil(sizeZ, 4);
            long biomeEntryCount = beCX * beCY * beCZ;
            long expectedBiomeWords = BitPackedArray.wordsRequired(biomeEntryCount, biomeBpe);

            if (storedBiomeWordCount != expectedBiomeWords) {
                throw new MsfParseException(String.format(
                    "Region: biome_packed_array_length mismatch: stored %d, expected %d "
                    + "(biomeEntryCount=%d, biomeBpe=%d)",
                    storedBiomeWordCount, expectedBiomeWords, biomeEntryCount, biomeBpe
                ));
            }
            if (storedBiomeWordCount > Integer.MAX_VALUE) {
                throw new MsfParseException(
                    "Region: biome packed array length exceeds allocatable size"
                );
            }
            long[] biomeWords = new long[(int) storedBiomeWordCount];
            for (int i = 0; i < biomeWords.length; i++) {
                biomeWords[i] = payloadBuf.getLong();
            }
            if (biomeEntryCount > Integer.MAX_VALUE) {
                throw new MsfParseException(
                    "Region: biome entry count exceeds int[] maximum"
                );
            }
            biomeData = BitPackedArray.unpack(biomeWords, (int) biomeEntryCount, biomeBpe);

            for (int i = 0; i < biomeData.length; i++) {
                if (biomeData[i] >= biomePaletteCount) {
                    throw new MsfParseException(String.format(
                        "Region: biome data at index %d contains out-of-range biome ID %d "
                        + "(biome palette has %d entries)",
                        i, biomeData[i], biomePaletteCount
                    ));
                }
            }
        }

        return new MsfRegion(originX, originY, originZ, sizeX, sizeY, sizeZ,
            comprType, blockData, biomeData, biomePalette);
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    private static void writeStr(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes);
    }

    private static void writeI32(ByteArrayOutputStream out, int v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(v);
        out.write(b.array());
    }

    private static void writeU32(ByteArrayOutputStream out, long v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt((int) v);
        out.write(b.array());
    }

    private static void writeU16(ByteArrayOutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static String readStr(ByteBuffer buf) throws MsfParseException {
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

    private static long divCeil(long a, long b) {
        return (a + b - 1) / b;
    }
}

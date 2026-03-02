package dev.msf.core.model;

import dev.msf.core.NotNull;

/**
 * Immutable representation of the MSF file header.
 *
 * <p>The header is exactly 48 bytes, begins at file offset 0, and is permanently frozen.
 * No future version of the MSF specification under major version 1 will alter any field.
 * See MSF Spec Section 3 and Section 12.2.
 *
 * <p>All multi-byte integer fields are little-endian in the binary format. Java long values
 * are used for u32 fields to preserve the full unsigned range, per MSF Spec Appendix E.
 *
 * <h2>Header layout</h2>
 * <pre>
 * Offset  Size  Type    Field
 * ------  ----  ------  -----
 * 0       4     u8[4]   Magic bytes (0x4D 0x53 0x46 0x21 — "MSF!")
 * 4       2     u16     Major version
 * 6       2     u16     Minor version
 * 8       4     u32     Feature flags
 * 12      4     u32     MC data version
 * 16      4     u32     Metadata block offset
 * 20      4     u32     Global palette offset
 * 24      4     u32     Layer index offset
 * 28      4     u32     Entity block offset
 * 32      4     u32     Block entity block offset
 * 36      4     u32     File size
 * 40      8     u64     Header checksum (xxHash3 of bytes 0–39)
 * </pre>
 *
 * @param majorVersion         the MSF major version; must be 1 for V1 files
 * @param minorVersion         the MSF minor version; 0 for V1.0 files
 * @param featureFlags         bitmask of optional capabilities present in this file
 * @param mcDataVersion        Minecraft data version the schematic was authored in
 * @param metadataBlockOffset  absolute byte offset to the metadata block; MUST NOT be 0
 * @param globalPaletteOffset  absolute byte offset to the global palette block; MUST NOT be 0
 * @param layerIndexOffset     absolute byte offset to the layer index block; MUST NOT be 0
 * @param entityBlockOffset    absolute byte offset to the entity block; 0 if absent
 * @param blockEntityBlockOffset absolute byte offset to the block entity block; 0 if absent
 * @param fileSize             total byte length of the file
 * @param headerChecksum       xxHash3 digest of header bytes 0–39
 */
public record MsfHeader(
    int majorVersion,
    int minorVersion,
    long featureFlags,
    long mcDataVersion,
    long metadataBlockOffset,
    long globalPaletteOffset,
    long layerIndexOffset,
    long entityBlockOffset,
    long blockEntityBlockOffset,
    long fileSize,
    long headerChecksum
) {
    /** The expected magic bytes at offset 0: {@code MSF!} (0x4D 0x53 0x46 0x21). */
    public static final byte[] MAGIC = {0x4D, 0x53, 0x46, 0x21};

    /** The major version this reader/writer implements. */
    public static final int SUPPORTED_MAJOR_VERSION = 1;

    /** The minor version this writer produces for V1.0 files. */
    public static final int CURRENT_MINOR_VERSION = 0;

    /** Total size of the header in bytes — frozen permanently. */
    public static final int HEADER_SIZE = 48;

    // -------------------------------------------------------------------------
    // Feature flag bit positions
    // -------------------------------------------------------------------------

    /** Feature flag bit 0: file contains entity data. */
    public static final long FLAG_HAS_ENTITIES = 1L;

    /** Feature flag bit 1: file contains block entity data. */
    public static final long FLAG_HAS_BLOCK_ENTITIES = 1L << 1;

    /** Feature flag bit 2: file contains biome data. */
    public static final long FLAG_HAS_BIOME_DATA = 1L << 2;

    /** Feature flag bit 3: file contains lighting hints. */
    public static final long FLAG_HAS_LIGHTING_HINTS = 1L << 3;

    /** Feature flag bit 4: file is a multi-region schematic. */
    public static final long FLAG_MULTI_REGION = 1L << 4;

    /** Feature flag bit 5: file is in delta/diff format. */
    public static final long FLAG_DELTA_FORMAT = 1L << 5;

    /** Feature flag bit 6: file contains signal ports. */
    public static final long FLAG_HAS_SIGNAL_PORTS = 1L << 6;

    /** Feature flag bit 7: file uses construction layers. */
    public static final long FLAG_HAS_CONSTRUCTION_LAYERS = 1L << 7;

    /** Feature flag bit 8: file uses the variant system. */
    public static final long FLAG_HAS_VARIANT_SYSTEM = 1L << 8;

    /** Feature flag bit 9: file contains palette substitution rules. */
    public static final long FLAG_HAS_PALETTE_SUBSTITUTION = 1L << 9;

    /**
     * Returns {@code true} if the given feature flag bit is set in this header's feature flags.
     *
     * @param flag one of the {@code FLAG_*} constants defined on this record
     * @return {@code true} if the flag is set
     */
    public boolean hasFlag(long flag) {
        return (featureFlags & flag) != 0;
    }

    /**
     * Creates a {@link Builder} pre-populated from this header's fields.
     *
     * @return a builder initialised with this header's values
     */
    @NotNull
    public Builder toBuilder() {
        return new Builder()
            .majorVersion(majorVersion)
            .minorVersion(minorVersion)
            .featureFlags(featureFlags)
            .mcDataVersion(mcDataVersion)
            .metadataBlockOffset(metadataBlockOffset)
            .globalPaletteOffset(globalPaletteOffset)
            .layerIndexOffset(layerIndexOffset)
            .entityBlockOffset(entityBlockOffset)
            .blockEntityBlockOffset(blockEntityBlockOffset)
            .fileSize(fileSize)
            .headerChecksum(headerChecksum);
    }

    /**
     * Returns a new {@link Builder} with V1.0 defaults applied.
     *
     * @return a fresh builder
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link MsfHeader} instances.
     *
     * <p>The builder enforces no constraints — validation is the responsibility of
     * {@link dev.msf.core.io.MsfWriter} on write and {@link dev.msf.core.io.MsfReader} on read.
     */
    public static final class Builder {

        private int majorVersion = SUPPORTED_MAJOR_VERSION;
        private int minorVersion = CURRENT_MINOR_VERSION;
        private long featureFlags = 0L;
        private long mcDataVersion = 0L;
        private long metadataBlockOffset = 0L;
        private long globalPaletteOffset = 0L;
        private long layerIndexOffset = 0L;
        private long entityBlockOffset = 0L;
        private long blockEntityBlockOffset = 0L;
        private long fileSize = 0L;
        private long headerChecksum = 0L;

        private Builder() {}

        /** @return this builder */
        @NotNull
        public Builder majorVersion(int majorVersion) {
            this.majorVersion = majorVersion;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder minorVersion(int minorVersion) {
            this.minorVersion = minorVersion;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder featureFlags(long featureFlags) {
            this.featureFlags = featureFlags;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder mcDataVersion(long mcDataVersion) {
            this.mcDataVersion = mcDataVersion;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder metadataBlockOffset(long metadataBlockOffset) {
            this.metadataBlockOffset = metadataBlockOffset;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder globalPaletteOffset(long globalPaletteOffset) {
            this.globalPaletteOffset = globalPaletteOffset;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder layerIndexOffset(long layerIndexOffset) {
            this.layerIndexOffset = layerIndexOffset;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder entityBlockOffset(long entityBlockOffset) {
            this.entityBlockOffset = entityBlockOffset;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder blockEntityBlockOffset(long blockEntityBlockOffset) {
            this.blockEntityBlockOffset = blockEntityBlockOffset;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        /** @return this builder */
        @NotNull
        public Builder headerChecksum(long headerChecksum) {
            this.headerChecksum = headerChecksum;
            return this;
        }

        /**
         * Builds the {@link MsfHeader}.
         *
         * @return a new immutable {@code MsfHeader}
         */
        @NotNull
        public MsfHeader build() {
            return new MsfHeader(
                majorVersion,
                minorVersion,
                featureFlags,
                mcDataVersion,
                metadataBlockOffset,
                globalPaletteOffset,
                layerIndexOffset,
                entityBlockOffset,
                blockEntityBlockOffset,
                fileSize,
                headerChecksum
            );
        }
    }
}

package dev.msf.core.model;

/**
 * Immutable representation of the 48-byte MSF file header.
 *
 * <p>The header is permanently frozen at 48 bytes and is always located at file offset 0.
 * No future V1 version of the specification will alter the size or meaning of any field.
 *
 * <p>All offset fields are stored as {@code long} values to allow correct unsigned u32
 * interpretation per Appendix E. A value of {@code 0} for any optional block offset
 * indicates the block is absent (Section 2.2).
 *
 * <p>Feature flags are stored as an {@code int}; callers should interpret bits using
 * {@link FeatureFlags} constants. All reserved bits (10–31) must be 0 in V1.0 files.
 *
 * @param majorVersion          u16 — major format version; must be 1 for this reader
 * @param minorVersion          u16 — minor format version; capability advertisement, not a gate
 * @param featureFlags          u32 — bitmask of optional capabilities present in this file
 * @param mcDataVersion         u32 — Minecraft data version the schematic was authored in
 * @param metadataOffset        u32 — absolute byte offset to the metadata block; MUST NOT be 0
 * @param globalPaletteOffset   u32 — absolute byte offset to the global palette block; MUST NOT be 0
 * @param layerIndexOffset      u32 — absolute byte offset to the layer index block; MUST NOT be 0
 * @param entityBlockOffset     u32 — absolute byte offset to the entity block; 0 if absent
 * @param blockEntityBlockOffset u32 — absolute byte offset to the block entity block; 0 if absent
 * @param fileSize              u32 — total byte length of the complete file including the 8-byte checksum
 * @param headerChecksum        u64 — xxHash3-64 digest of header bytes 0–39 with seed 0
 *
 * @see MsfSpec Section 3 — header
 * @see MsfSpec Appendix E — unsigned integer handling in Java
 */
public record MsfHeader(
    int majorVersion,
    int minorVersion,
    int featureFlags,
    long mcDataVersion,
    long metadataOffset,
    long globalPaletteOffset,
    long layerIndexOffset,
    long entityBlockOffset,
    long blockEntityBlockOffset,
    long fileSize,
    long headerChecksum
) {

    /** The MSF format major version this implementation supports. */
    public static final int SUPPORTED_MAJOR_VERSION = 1;

    /** The MSF format minor version this implementation supports. */
    public static final int SUPPORTED_MINOR_VERSION = 0;

    /** The size of the header in bytes. This value is permanently frozen. */
    public static final int HEADER_SIZE = 48;

    /** The expected magic bytes at the start of every MSF file (ASCII: {@code MSF!}). */
    public static final byte[] MAGIC = { 0x4D, 0x53, 0x46, 0x21 };

    /**
     * Feature flag bit constants for use with {@link #featureFlags()}.
     *
     * @see MsfSpec Section 3.3 — feature flags
     */
    public static final class FeatureFlags {
        /** Bit 0 — file contains an entity block. */
        public static final int HAS_ENTITIES = 1 << 0;
        /** Bit 1 — file contains a block entity block. */
        public static final int HAS_BLOCK_ENTITIES = 1 << 1;
        /** Bit 2 — file contains biome data in region payloads. */
        public static final int HAS_BIOME_DATA = 1 << 2;
        /** Bit 3 — file contains lighting hints. */
        public static final int HAS_LIGHTING_HINTS = 1 << 3;
        /** Bit 4 — file uses multi-region format. */
        public static final int MULTI_REGION = 1 << 4;
        /** Bit 5 — file is a delta/diff format file. */
        public static final int DELTA_DIFF_FORMAT = 1 << 5;
        /** Bit 6 — file contains signal ports. */
        public static final int HAS_SIGNAL_PORTS = 1 << 6;
        /** Bit 7 — file contains construction layers. */
        public static final int HAS_CONSTRUCTION_LAYERS = 1 << 7;
        /** Bit 8 — file uses the variant system. */
        public static final int HAS_VARIANT_SYSTEM = 1 << 8;
        /** Bit 9 — file contains palette substitution rules. */
        public static final int HAS_PALETTE_SUBSTITUTION_RULES = 1 << 9;
        /** Mask covering all defined V1.0 feature flag bits (0–9). */
        public static final int DEFINED_BITS_MASK = 0x3FF;
        /** Mask covering reserved bits 10–31. */
        public static final int RESERVED_BITS_MASK = ~DEFINED_BITS_MASK;

        private FeatureFlags() {}
    }

    /**
     * Returns {@code true} if the given feature flag bit is set in this header.
     *
     * @param flag one of the {@link FeatureFlags} constants
     * @return {@code true} if the flag bit is set
     */
    public boolean hasFlag(int flag) {
        return (featureFlags & flag) != 0;
    }

    /**
     * Returns {@code true} if the entity block is declared present by feature flags.
     *
     * @return {@code true} if {@link FeatureFlags#HAS_ENTITIES} is set
     */
    public boolean hasEntities() {
        return hasFlag(FeatureFlags.HAS_ENTITIES);
    }

    /**
     * Returns {@code true} if the block entity block is declared present by feature flags.
     *
     * @return {@code true} if {@link FeatureFlags#HAS_BLOCK_ENTITIES} is set
     */
    public boolean hasBlockEntities() {
        return hasFlag(FeatureFlags.HAS_BLOCK_ENTITIES);
    }

    /**
     * Returns {@code true} if biome data is present in region payloads.
     *
     * @return {@code true} if {@link FeatureFlags#HAS_BIOME_DATA} is set
     */
    public boolean hasBiomeData() {
        return hasFlag(FeatureFlags.HAS_BIOME_DATA);
    }

    // Convenience constants matching test expectations
    public static final int FLAG_HAS_ENTITIES = FeatureFlags.HAS_ENTITIES;
    public static final int FLAG_HAS_BLOCK_ENTITIES = FeatureFlags.HAS_BLOCK_ENTITIES;
    public static final int FLAG_HAS_BIOME_DATA = FeatureFlags.HAS_BIOME_DATA;
    public static final int FLAG_HAS_LIGHTING_HINTS = FeatureFlags.HAS_LIGHTING_HINTS;
    public static final int FLAG_MULTI_REGION = FeatureFlags.MULTI_REGION;
    public static final int FLAG_HAS_SIGNAL_PORTS = FeatureFlags.HAS_SIGNAL_PORTS;
    public static final int FLAG_HAS_CONSTRUCTION_LAYERS = FeatureFlags.HAS_CONSTRUCTION_LAYERS;
    public static final int FLAG_HAS_VARIANT_SYSTEM = FeatureFlags.HAS_VARIANT_SYSTEM;
    public static final int FLAG_HAS_PALETTE_SUBSTITUTION = FeatureFlags.HAS_PALETTE_SUBSTITUTION_RULES;

    /**
     * Creates a new builder for constructing MsfHeader instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link MsfHeader} instances.
     */
    public static final class Builder {
        private int majorVersion = SUPPORTED_MAJOR_VERSION;
        private int minorVersion = SUPPORTED_MINOR_VERSION;
        private int featureFlags = 0;
        private long mcDataVersion = 0;
        private long metadataBlockOffset = 0;
        private long globalPaletteOffset = 0;
        private long layerIndexOffset = 0;
        private long entityBlockOffset = 0;
        private long blockEntityBlockOffset = 0;
        private long fileSize = 0;
        private long headerChecksum = 0;

        /**
         * Sets the major version.
         */
        public Builder majorVersion(int version) {
            this.majorVersion = version;
            return this;
        }

        /**
         * Sets the minor version.
         */
        public Builder minorVersion(int version) {
            this.minorVersion = version;
            return this;
        }

        /**
         * Sets the feature flags.
         */
        public Builder featureFlags(long flags) {
            this.featureFlags = (int) flags;
            return this;
        }

        /**
         * Sets the Minecraft data version.
         */
        public Builder mcDataVersion(long version) {
            this.mcDataVersion = version;
            return this;
        }

        /**
         * Sets the metadata block offset.
         */
        public Builder metadataBlockOffset(long offset) {
            this.metadataBlockOffset = offset;
            return this;
        }

        /**
         * Sets the global palette offset.
         */
        public Builder globalPaletteOffset(long offset) {
            this.globalPaletteOffset = offset;
            return this;
        }

        /**
         * Sets the layer index offset.
         */
        public Builder layerIndexOffset(long offset) {
            this.layerIndexOffset = offset;
            return this;
        }

        /**
         * Sets the entity block offset.
         */
        public Builder entityBlockOffset(long offset) {
            this.entityBlockOffset = offset;
            return this;
        }

        /**
         * Sets the block entity block offset.
         */
        public Builder blockEntityBlockOffset(long offset) {
            this.blockEntityBlockOffset = offset;
            return this;
        }

        /**
         * Sets the file size.
         */
        public Builder fileSize(long size) {
            this.fileSize = size;
            return this;
        }

        /**
         * Sets the header checksum.
         */
        public Builder headerChecksum(long checksum) {
            this.headerChecksum = checksum;
            return this;
        }

        /**
         * Builds and returns the MsfHeader instance.
         *
         * @return a new MsfHeader with the configured values
         */
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

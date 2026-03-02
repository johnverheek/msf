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
}

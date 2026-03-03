package dev.msf.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable in-memory representation of a complete MSF V1 file.
 *
 * <p>Composes all blocks defined in the MSF specification:
 * <ul>
 *   <li>{@link MsfHeader} — 48-byte frozen header with version, flags, and block offsets</li>
 *   <li>{@link MsfMetadata} — descriptive and placement metadata (required)</li>
 *   <li>{@link MsfPalette} — global blockstate palette (required)</li>
 *   <li>{@link MsfLayerIndex} — layer definitions and inline region data (required)</li>
 *   <li>{@code Optional<List<MsfEntity>>} — entity block (Section 8); present iff feature flag bit 0 is set</li>
 *   <li>{@code Optional<List<MsfBlockEntity>>} — block entity block (Section 9); present iff feature flag bit 1 is set</li>
 * </ul>
 *
 * <h2>Building a new file</h2>
 * <p>Use {@link Builder} to construct an {@code MsfFile} for writing. The builder
 * derives feature flags from the provided content — callers MUST NOT set feature flags directly.
 *
 * <pre>{@code
 * MsfFile file = MsfFile.builder()
 *     .mcDataVersion(3953L)
 *     .metadata(metadata)
 *     .palette(palette)
 *     .layerIndex(layerIndex)
 *     .entities(entityList)         // optional
 *     .blockEntities(blockEntityList) // optional
 *     .build();
 * }</pre>
 *
 * <h2>Reading a file</h2>
 * <p>Use {@link dev.msf.core.io.MsfReader#readFile(byte[], dev.msf.core.io.MsfReaderConfig, java.util.function.Consumer)}
 * to parse an MSF file into an {@code MsfFile}.
 *
 * <h2>Feature flag derivation</h2>
 * <p>The builder sets:
 * <ul>
 *   <li>Bit 0 ({@link MsfHeader.FeatureFlags#HAS_ENTITIES}) iff an entity list is provided</li>
 *   <li>Bit 1 ({@link MsfHeader.FeatureFlags#HAS_BLOCK_ENTITIES}) iff a block entity list is provided</li>
 *   <li>Bit 2 ({@link MsfHeader.FeatureFlags#HAS_BIOME_DATA}) iff any region in the layer index
 *       carries biome data ({@link MsfRegion#hasBiomeData()} returns {@code true})</li>
 * </ul>
 * All other feature flag bits are 0 in the placeholder header produced by the builder.
 * The {@link dev.msf.core.io.MsfWriter} computes the canonical header with correct offsets
 * and checksums at write time.
 *
 * @see MsfSpec Section 2 — file structure
 * @see MsfSpec Section 3.3 — feature flags
 * @see MsfSpec Section 8 — entity block
 * @see MsfSpec Section 9 — block entity block
 */
public final class MsfFile {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The MSF file header. May contain placeholder offsets/checksums if built via builder. */
    private final MsfHeader header;

    /** The metadata block; always required. */
    private final MsfMetadata metadata;

    /** The global palette block; always required. */
    private final MsfPalette palette;

    /** The layer index block; always required. */
    private final MsfLayerIndex layerIndex;

    /**
     * The entity list, or {@link Optional#empty()} if the entity block is absent.
     * When present, the list is non-empty (Section 8.2).
     */
    private final Optional<List<MsfEntity>> entities;

    /**
     * The block entity list, or {@link Optional#empty()} if the block entity block is absent.
     * When present, the list is non-empty (Section 9.2).
     */
    private final Optional<List<MsfBlockEntity>> blockEntities;

    // -------------------------------------------------------------------------
    // Constructor (private)
    // -------------------------------------------------------------------------

    private MsfFile(
        MsfHeader header,
        MsfMetadata metadata,
        MsfPalette palette,
        MsfLayerIndex layerIndex,
        Optional<List<MsfEntity>> entities,
        Optional<List<MsfBlockEntity>> blockEntities
    ) {
        this.header = header;
        this.metadata = metadata;
        this.palette = palette;
        this.layerIndex = layerIndex;
        this.entities = entities;
        this.blockEntities = blockEntities;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the MSF file header.
     *
     * <p>When this {@code MsfFile} was produced by {@link Builder}, the header contains
     * the derived feature flags and {@code mcDataVersion}, but all block offsets,
     * {@code fileSize}, and {@code headerChecksum} are {@code 0} (placeholder values).
     * The writer fills in the canonical header at write time.
     *
     * <p>When this {@code MsfFile} was produced by the reader, the header contains the
     * actual values as read from the file.
     *
     * @return the header; never {@code null}
     */
    public MsfHeader header() {
        return header;
    }

    /**
     * Returns the metadata block.
     *
     * @return the metadata; never {@code null}
     */
    public MsfMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the global palette block.
     *
     * @return the palette; never {@code null}
     */
    public MsfPalette palette() {
        return palette;
    }

    /**
     * Returns the layer index block.
     *
     * @return the layer index; never {@code null}
     */
    public MsfLayerIndex layerIndex() {
        return layerIndex;
    }

    /**
     * Returns the entity list, or {@link Optional#empty()} if no entity block is present.
     *
     * <p>When present, the list is always non-empty (see Section 8.2 — writers MUST NOT
     * write an entity block with an entity count of 0).
     *
     * @return the entity list wrapped in an {@link Optional}; never {@code null}
     */
    public Optional<List<MsfEntity>> entities() {
        return entities;
    }

    /**
     * Returns the block entity list, or {@link Optional#empty()} if no block entity block is present.
     *
     * <p>When present, the list is always non-empty (see Section 9.2 — writers MUST NOT
     * write a block entity block with a block entity count of 0).
     *
     * @return the block entity list wrapped in an {@link Optional}; never {@code null}
     */
    public Optional<List<MsfBlockEntity>> blockEntities() {
        return blockEntities;
    }

    // -------------------------------------------------------------------------
    // Parsed-file factory (for MsfReader use)
    // -------------------------------------------------------------------------

    /**
     * Constructs an {@code MsfFile} from fully parsed block data.
     *
     * <p>This factory is intended for use by {@link dev.msf.core.io.MsfReader} when
     * constructing an {@code MsfFile} from an on-disk file. The header is taken as-is
     * from the parsed file — no flag derivation is performed. Block offsets, checksums,
     * and all other header fields reflect the actual values read from the file.
     *
     * <p>Entity and block entity lists, if non-null, are wrapped in unmodifiable views.
     * {@code null} maps to {@link Optional#empty()}.
     *
     * @param header          the parsed header
     * @param metadata        the parsed metadata block
     * @param palette         the parsed palette block
     * @param layerIndex      the parsed layer index block
     * @param entities        the parsed entity list, or {@code null} if absent
     * @param blockEntities   the parsed block entity list, or {@code null} if absent
     * @return a new {@code MsfFile}
     */
    public static MsfFile ofParsed(
        MsfHeader header,
        MsfMetadata metadata,
        MsfPalette palette,
        MsfLayerIndex layerIndex,
        List<MsfEntity> entities,
        List<MsfBlockEntity> blockEntities
    ) {
        Optional<List<MsfEntity>> entityOpt = entities == null
            ? Optional.empty()
            : Optional.of(Collections.unmodifiableList(new ArrayList<>(entities)));
        Optional<List<MsfBlockEntity>> blockEntityOpt = blockEntities == null
            ? Optional.empty()
            : Optional.of(Collections.unmodifiableList(new ArrayList<>(blockEntities)));
        return new MsfFile(header, metadata, palette, layerIndex, entityOpt, blockEntityOpt);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for constructing an {@code MsfFile} for writing.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing a new {@link MsfFile} for writing.
     *
     * <p>Feature flags are derived from the provided content at {@link #build()} time.
     * Callers MUST NOT set feature flags directly; instead, provide the optional blocks
     * whose presence implies the corresponding flags.
     */
    public static final class Builder {

        private long mcDataVersion = 0L;
        private MsfMetadata metadata;
        private MsfPalette palette;
        private MsfLayerIndex layerIndex;
        private List<MsfEntity> entities; // null = absent
        private List<MsfBlockEntity> blockEntities; // null = absent

        private Builder() {}

        /**
         * Sets the Minecraft data version (u32 field, header offset 12).
         * This value is sourced from Minecraft's {@code version.json} (e.g. {@code 3953} for MC 1.21).
         *
         * @param version the data version
         * @return this builder
         */
        public Builder mcDataVersion(long version) {
            this.mcDataVersion = version;
            return this;
        }

        /**
         * Sets the metadata block (required).
         *
         * @param metadata the metadata; must not be {@code null}
         * @return this builder
         */
        public Builder metadata(MsfMetadata metadata) {
            this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
            return this;
        }

        /**
         * Sets the global palette block (required).
         *
         * @param palette the palette; must not be {@code null}
         * @return this builder
         */
        public Builder palette(MsfPalette palette) {
            this.palette = Objects.requireNonNull(palette, "palette must not be null");
            return this;
        }

        /**
         * Sets the layer index block (required).
         *
         * @param layerIndex the layer index; must not be {@code null}
         * @return this builder
         */
        public Builder layerIndex(MsfLayerIndex layerIndex) {
            this.layerIndex = Objects.requireNonNull(layerIndex, "layerIndex must not be null");
            return this;
        }

        /**
         * Sets the entity list (optional). Providing a non-null list causes feature flag bit 0
         * ({@link MsfHeader.FeatureFlags#HAS_ENTITIES}) to be set in the derived header.
         *
         * <p>A defensive copy is made.
         *
         * @param entities the entity list; must not be {@code null} if called; may not be empty
         * @return this builder
         * @throws IllegalArgumentException if {@code entities} is not {@code null} but is empty
         */
        public Builder entities(List<MsfEntity> entities) {
            Objects.requireNonNull(entities, "entities must not be null; omit the call to leave absent");
            if (entities.isEmpty()) {
                throw new IllegalArgumentException(
                    "Entity list must not be empty — writers MUST NOT write an entity block with "
                    + "entity count 0 (Section 8.2); omit entities() to leave the entity block absent"
                );
            }
            this.entities = new ArrayList<>(entities);
            return this;
        }

        /**
         * Sets the block entity list (optional). Providing a non-null list causes feature flag bit 1
         * ({@link MsfHeader.FeatureFlags#HAS_BLOCK_ENTITIES}) to be set in the derived header.
         *
         * <p>A defensive copy is made.
         *
         * @param blockEntities the block entity list; must not be {@code null} if called; may not be empty
         * @return this builder
         * @throws IllegalArgumentException if {@code blockEntities} is not {@code null} but is empty
         */
        public Builder blockEntities(List<MsfBlockEntity> blockEntities) {
            Objects.requireNonNull(blockEntities, "blockEntities must not be null; omit the call to leave absent");
            if (blockEntities.isEmpty()) {
                throw new IllegalArgumentException(
                    "Block entity list must not be empty — writers MUST NOT write a block entity block with "
                    + "block entity count 0 (Section 9.2); omit blockEntities() to leave the block entity block absent"
                );
            }
            this.blockEntities = new ArrayList<>(blockEntities);
            return this;
        }

        /**
         * Builds and returns the {@link MsfFile} instance.
         *
         * <p>Feature flags are derived from the provided content:
         * <ul>
         *   <li>Bit 0 set iff {@link #entities} was provided</li>
         *   <li>Bit 1 set iff {@link #blockEntities} was provided</li>
         *   <li>Bit 2 set iff any {@link MsfRegion} in the layer index has biome data</li>
         * </ul>
         *
         * <p>A placeholder {@link MsfHeader} is constructed with the derived flags and
         * {@code mcDataVersion}. Block offsets, {@code fileSize}, and {@code headerChecksum}
         * are all {@code 0} — the writer computes these at write time.
         *
         * @return a new {@link MsfFile}
         * @throws NullPointerException if {@code metadata}, {@code palette}, or {@code layerIndex}
         *                              was not set
         */
        public MsfFile build() {
            Objects.requireNonNull(metadata,   "metadata must be set before calling build()");
            Objects.requireNonNull(palette,    "palette must be set before calling build()");
            Objects.requireNonNull(layerIndex, "layerIndex must be set before calling build()");

            // Derive feature flags from content (Section 3.3 — callers must not set flags directly)
            int flags = 0;
            if (entities != null) {
                flags |= MsfHeader.FeatureFlags.HAS_ENTITIES;
            }
            if (blockEntities != null) {
                flags |= MsfHeader.FeatureFlags.HAS_BLOCK_ENTITIES;
            }
            if (hasBiomeDataInLayers(layerIndex)) {
                flags |= MsfHeader.FeatureFlags.HAS_BIOME_DATA;
            }

            // Placeholder header: correct flags and mcDataVersion; zero offsets/checksums.
            // The writer fills in canonical values at write time.
            MsfHeader placeholderHeader = MsfHeader.builder()
                .mcDataVersion(mcDataVersion)
                .featureFlags(flags)
                .build();

            Optional<List<MsfEntity>> entityOpt = entities == null
                ? Optional.empty()
                : Optional.of(Collections.unmodifiableList(new ArrayList<>(entities)));
            Optional<List<MsfBlockEntity>> blockEntityOpt = blockEntities == null
                ? Optional.empty()
                : Optional.of(Collections.unmodifiableList(new ArrayList<>(blockEntities)));

            return new MsfFile(placeholderHeader, metadata, palette, layerIndex, entityOpt, blockEntityOpt);
        }

        /** Returns {@code true} if any region in the layer index carries biome data. */
        private static boolean hasBiomeDataInLayers(MsfLayerIndex layerIndex) {
            for (MsfLayer layer : layerIndex.layers()) {
                for (MsfRegion region : layer.regions()) {
                    if (region.hasBiomeData()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}

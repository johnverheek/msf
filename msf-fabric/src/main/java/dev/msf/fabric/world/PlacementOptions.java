package dev.msf.fabric.world;

/**
 * Options controlling how {@link RegionPlacer} places a region into the world.
 *
 * @param skipAir            when {@code true}, air blocks in the region do not overwrite
 *                           existing world blocks; when {@code false}, all blocks including
 *                           air are placed unconditionally
 * @param placeBlockEntities when {@code true}, block entity NBT payloads are applied
 *                           after all blocks have been placed (two-phase placement)
 * @param spawnEntities      when {@code true}, entities from the MSF entity block are
 *                           spawned into the world after block placement
 * @param canonicalFacing    the facing direction the schematic was authored in,
 *                           sourced from {@link dev.msf.core.model.MsfMetadata#canonicalFacing()}
 * @param targetFacing       the facing direction to use when placing; rotation transform
 *                           is derived from the delta between {@code canonicalFacing} and
 *                           this value (Section 10.2)
 * @param mirror             mirror axis to apply after rotation (Section 10.3);
 *                           {@link Mirror#NONE} for no mirroring
 */
public record PlacementOptions(
    boolean skipAir,
    boolean placeBlockEntities,
    boolean spawnEntities,
    CanonicalFacing canonicalFacing,
    CanonicalFacing targetFacing,
    Mirror mirror
) {

    /** Places all blocks with no rotation or mirror, without skipping air, with entities and block entities. */
    public static final PlacementOptions DEFAULT = new PlacementOptions(
        false, true, true, CanonicalFacing.NORTH, CanonicalFacing.NORTH, Mirror.NONE
    );

    /** Places all blocks with no rotation or mirror, skips air, with entities and block entities. */
    public static final PlacementOptions SKIP_AIR = new PlacementOptions(
        true, true, true, CanonicalFacing.NORTH, CanonicalFacing.NORTH, Mirror.NONE
    );
}

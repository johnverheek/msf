package dev.msf.fabric.world;

/**
 * Options controlling how {@link RegionPlacer} places a region into the world.
 *
 * @param skipAir            when {@code true}, air blocks in the region do not overwrite
 *                           existing world blocks; when {@code false}, all blocks including
 *                           air are placed unconditionally
 * @param placeBlockEntities placeholder for Session 5 — block entities are not placed yet
 * @param canonicalFacing    the facing direction the schematic was authored in,
 *                           sourced from {@link dev.msf.core.model.MsfMetadata#canonicalFacing()}
 * @param targetFacing       the facing direction to use when placing; rotation transform
 *                           is derived from the delta between {@code canonicalFacing} and
 *                           this value (Section 10.2)
 */
public record PlacementOptions(
    boolean skipAir,
    boolean placeBlockEntities,
    CanonicalFacing canonicalFacing,
    CanonicalFacing targetFacing
) {

    /** Places all blocks with no rotation and without skipping air. */
    public static final PlacementOptions DEFAULT = new PlacementOptions(
        false, false, CanonicalFacing.NORTH, CanonicalFacing.NORTH
    );

    /** Places all blocks with no rotation but skips air. */
    public static final PlacementOptions SKIP_AIR = new PlacementOptions(
        true, false, CanonicalFacing.NORTH, CanonicalFacing.NORTH
    );
}

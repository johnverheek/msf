package dev.msf.fabric.world;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.core.util.YzxOrder;
import dev.msf.fabric.bridge.BlockStateBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

/**
 * Places {@link MsfRegion} block data into a {@link ServerWorld}.
 *
 * <h2>Air handling</h2>
 * Air blocks (palette ID 0) are placed unconditionally unless
 * {@link PlacementOptions#skipAir()} is {@code true}.
 *
 * <h2>Rotation (Section 10.2)</h2>
 * The rotation to apply is computed as the clockwise delta from
 * {@link PlacementOptions#canonicalFacing()} to {@link PlacementOptions#targetFacing()}.
 * Block states are rotated via {@link BlockState#rotate(BlockRotation)}, which
 * uses Minecraft's own property-API-based implementation for all direction-dependent
 * properties (facing, stair shape, trapdoor half, piston/observer facing, etc.).
 * Positions are rotated around the anchor using standard 2D rotation formulas.
 */
public final class RegionPlacer {

    private RegionPlacer() {}

    // =========================================================================
    // place
    // =========================================================================

    /**
     * Places all blocks from {@code region} into {@code world}.
     *
     * @param region        the region to place
     * @param globalPalette the global palette whose entry IDs match
     *                      {@link MsfRegion#blockData()}
     * @param world         the target world
     * @param anchorPos     the world position that corresponds to the schematic anchor;
     *                      region origin offsets are applied relative to this point
     * @param options       placement options (air skip, rotation)
     * @throws MsfPaletteException if any {@code blockData} entry references an ID
     *                             that is out of range in {@code globalPalette}, or if
     *                             a palette string fails to resolve to a {@link BlockState}
     */
    public static void place(
        MsfRegion region,
        MsfPalette globalPalette,
        ServerWorld world,
        BlockPos anchorPos,
        PlacementOptions options
    ) throws MsfPaletteException {
        int sizeX = region.sizeX();
        int sizeY = region.sizeY();
        int sizeZ = region.sizeZ();
        int[] blockData = region.blockData();
        int paletteSize = globalPalette.entries().size();

        BlockRotation rotation = computeRotation(options.canonicalFacing(), options.targetFacing());

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int paletteId = blockData[(int) YzxOrder.index(x, y, z, sizeX, sizeZ)];

                    if (paletteId == 0 && options.skipAir()) {
                        continue;
                    }

                    if (paletteId >= paletteSize) {
                        throw new MsfPaletteException(
                            "Palette ID " + paletteId + " is out of range (palette has "
                            + paletteSize + " entries)"
                        );
                    }

                    BlockState state = BlockStateBridge.fromString(
                        globalPalette.entries().get(paletteId)
                    );

                    // Apply blockstate rotation using Minecraft's property API (Section 10.2)
                    if (rotation != BlockRotation.NONE) {
                        state = state.rotate(rotation);
                    }

                    // Rotate position around the anchor (Section 10.2)
                    int localX = region.originX() + x;
                    int localY = region.originY() + y;
                    int localZ = region.originZ() + z;
                    int[] rotXZ = rotatePosition(localX, localZ, rotation);

                    BlockPos worldPos = new BlockPos(
                        anchorPos.getX() + rotXZ[0],
                        anchorPos.getY() + localY,
                        anchorPos.getZ() + rotXZ[1]
                    );

                    world.setBlockState(worldPos, state, Block.NOTIFY_ALL);
                }
            }
        }
    }

    // =========================================================================
    // Rotation helpers
    // =========================================================================

    /**
     * Computes the {@link BlockRotation} needed to go from {@code canonical} to
     * {@code target} in the clockwise direction.
     *
     * <p>CW ordinal mapping: North=0, East=1, South=2, West=3.
     * {@link BlockRotation#values()} is ordered NONE, CLOCKWISE_90, CLOCKWISE_180,
     * COUNTERCLOCKWISE_90 — matching delta 0, 1, 2, 3.
     */
    static BlockRotation computeRotation(CanonicalFacing canonical, CanonicalFacing target) {
        int delta = (target.cwOrdinal() - canonical.cwOrdinal() + 4) % 4;
        return BlockRotation.values()[delta];
    }

    /**
     * Rotates a local (X, Z) position around the anchor (origin) using the standard
     * 2D rotation formulas for the given {@link BlockRotation}.
     *
     * <p>Formulas (Y unchanged):
     * <ul>
     *   <li>NONE:             {@code (x,  z)}</li>
     *   <li>CLOCKWISE_90:     {@code (-z, x)}</li>
     *   <li>CLOCKWISE_180:    {@code (-x, -z)}</li>
     *   <li>COUNTERCLOCKWISE_90: {@code (z, -x)}</li>
     * </ul>
     *
     * @param localX X component relative to the anchor
     * @param localZ Z component relative to the anchor
     * @param rotation the rotation to apply
     * @return {@code [rotatedX, rotatedZ]}
     */
    static int[] rotatePosition(int localX, int localZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE                -> new int[]{ localX,  localZ};
            case CLOCKWISE_90        -> new int[]{-localZ,  localX};
            case CLOCKWISE_180       -> new int[]{-localX, -localZ};
            case COUNTERCLOCKWISE_90 -> new int[]{ localZ, -localX};
        };
    }
}

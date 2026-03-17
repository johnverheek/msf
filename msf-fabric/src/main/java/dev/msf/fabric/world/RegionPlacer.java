package dev.msf.fabric.world;

import dev.msf.core.MsfParseException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.core.util.YzxOrder;
import dev.msf.fabric.bridge.BlockEntityBridge;
import dev.msf.fabric.bridge.BlockStateBridge;
import dev.msf.fabric.bridge.EntityBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

/**
 * Places {@link MsfRegion} block data (and optionally block entities) into a
 * {@link ServerWorld}.
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
 *
 * <h2>Block entity ordering</h2>
 * When {@link PlacementOptions#placeBlockEntities()} is {@code true},
 * {@link #place(MsfFile, ServerWorld, BlockPos, PlacementOptions)} places ALL blocks
 * across all regions first, then applies block entity NBT. This satisfies the precondition
 * that the target block must already exist before NBT can be loaded.
 *
 * <h2>Entity spawning</h2>
 * When {@link PlacementOptions#spawnEntities()} is {@code true} and the file has an entity
 * block, entities are spawned after all blocks and block entities have been placed.
 */
public final class RegionPlacer {

    private RegionPlacer() {}

    // =========================================================================
    // place — file level
    // =========================================================================

    /**
     * Places all blocks from every region in every layer of {@code file} into
     * {@code world}, then — if {@link PlacementOptions#placeBlockEntities()} is
     * {@code true} and the file has a block entity block — applies block entity NBT
     * to the already-placed blocks.
     *
     * <p>Block entities are applied AFTER all block data has been placed so that
     * {@link BlockEntityBridge#applyToWorld} always finds an existing block entity at
     * the target position.
     *
     * @param file      the MSF file to place
     * @param world     the target world
     * @param anchorPos the world position corresponding to the schematic anchor
     * @param options   placement options (air skip, rotation, block entities, entities)
     * @throws MsfPaletteException if block data palette IDs are invalid
     * @throws MsfParseException   if block entity NBT cannot be deserialized, or an
     *                             entity type is unknown
     */
    public static void place(
        MsfFile file,
        ServerWorld world,
        BlockPos anchorPos,
        PlacementOptions options
    ) throws MsfPaletteException, MsfParseException {
        MsfPalette globalPalette = file.palette();
        BlockRotation rotation = computeRotation(options.canonicalFacing(), options.targetFacing());

        // Phase 1: place all blocks across all layers and regions
        for (MsfLayer layer : file.layerIndex().layers()) {
            for (MsfRegion region : layer.regions()) {
                placeBlocks(region, globalPalette, world, anchorPos, options, rotation);
            }
        }

        // Phase 2: apply block entity NBT (blocks must already be placed — see class Javadoc)
        if (options.placeBlockEntities() && file.blockEntities().isPresent()) {
            BlockMirror mirror = options.mirror();
            for (MsfBlockEntity msfBe : file.blockEntities().get()) {
                // Mirror position first, then rotate — same order as block placement (Section 10.2)
                int[] mirXZ = mirrorPosition(msfBe.positionX(), msfBe.positionZ(), mirror);
                int[] rotXZ = rotatePosition(mirXZ[0], mirXZ[1], rotation);
                BlockPos worldPos = new BlockPos(
                    anchorPos.getX() + rotXZ[0],
                    anchorPos.getY() + msfBe.positionY(),
                    anchorPos.getZ() + rotXZ[1]
                );
                BlockEntityBridge.applyToWorld(msfBe, world, worldPos);
            }
        }

        // Phase 3: spawn entities
        if (options.spawnEntities() && file.entities().isPresent()) {
            BlockPos entityAnchor = anchorPos;
            for (MsfEntity msfEntity : file.entities().get()) {
                Entity entity = EntityBridge.toEntity(msfEntity, world, entityAnchor);
                world.spawnEntity(entity);
            }
        }
    }

    // =========================================================================
    // place — region level
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
        placeBlocks(region, globalPalette, world, anchorPos, options,
            computeRotation(options.canonicalFacing(), options.targetFacing()));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Inner placement loop — places blocks for one region with a pre-computed rotation.
     * Called by both public {@code place} overloads so the rotation is computed only once
     * when placing a multi-region file.
     */
    private static void placeBlocks(
        MsfRegion region,
        MsfPalette globalPalette,
        ServerWorld world,
        BlockPos anchorPos,
        PlacementOptions options,
        BlockRotation rotation
    ) throws MsfPaletteException {
        int sizeX = region.sizeX();
        int sizeY = region.sizeY();
        int sizeZ = region.sizeZ();
        int[] blockData = region.blockData();
        int paletteSize = globalPalette.entries().size();

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

                    // Apply mirror then rotation to blockstate (Section 10.2 — mirror before rotate)
                    BlockMirror mirror = options.mirror();
                    if (mirror != BlockMirror.NONE) {
                        state = state.mirror(mirror);
                    }
                    if (rotation != BlockRotation.NONE) {
                        state = state.rotate(rotation);
                    }

                    // Mirror then rotate position around the anchor (Section 10.2)
                    int localX = region.originX() + x;
                    int localY = region.originY() + y;
                    int localZ = region.originZ() + z;
                    int[] mirXZ = mirrorPosition(localX, localZ, mirror);
                    int[] rotXZ = rotatePosition(mirXZ[0], mirXZ[1], rotation);

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

    /**
     * Mirrors a local (X, Z) position around the anchor (origin) for the given
     * {@link BlockMirror}.
     *
     * <p>Formulas (Y unchanged):
     * <ul>
     *   <li>NONE:        {@code ( x,  z)}</li>
     *   <li>LEFT_RIGHT:  {@code (-x,  z)} — reflects X axis</li>
     *   <li>FRONT_BACK:  {@code ( x, -z)} — reflects Z axis</li>
     * </ul>
     *
     * @param localX X component relative to the anchor
     * @param localZ Z component relative to the anchor
     * @param mirror the mirror to apply
     * @return {@code [mirroredX, mirroredZ]}
     */
    static int[] mirrorPosition(int localX, int localZ, BlockMirror mirror) {
        return switch (mirror) {
            case NONE        -> new int[]{ localX,  localZ};
            case LEFT_RIGHT  -> new int[]{-localX,  localZ};
            case FRONT_BACK  -> new int[]{ localX, -localZ};
        };
    }
}

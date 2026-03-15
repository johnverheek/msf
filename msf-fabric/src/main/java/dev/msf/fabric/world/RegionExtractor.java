package dev.msf.fabric.world;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.core.util.YzxOrder;
import dev.msf.fabric.bridge.BlockEntityBridge;
import dev.msf.fabric.bridge.BlockStateBridge;
import dev.msf.fabric.bridge.EntityBridge;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads block (and optionally biome) data from a {@link ServerWorld} subvolume
 * and produces an {@link MsfRegion}.
 *
 * <h2>Palette output</h2>
 * Because {@link MsfRegion#blockData()} uses global palette IDs,
 * {@link #extract} takes a caller-managed {@code paletteOut} list. The method
 * appends any newly encountered blockstate strings to this list (deduplicating
 * against what is already present). The {@code blockData} IDs are valid indices
 * into {@code paletteOut} upon return. For a single-region file the caller
 * passes an initially-empty list and wraps it via {@link MsfPalette#of(List)}.
 *
 * <h2>Block ordering (Section 7.4)</h2>
 * Blocks are stored in YZX order: Y outermost, Z middle, X innermost.
 *
 * <h2>Biome ordering (Section 7.6)</h2>
 * When biomes are requested, each 4×4×4 section cell is sampled at its
 * minimum-corner world coordinate and stored in YZX-quarter order.
 * Air blocks are always included — they MUST NOT be skipped.
 */
public final class RegionExtractor {

    private RegionExtractor() {}

    // =========================================================================
    // extract
    // =========================================================================

    /**
     * Extracts a region from the world and appends its blockstate strings to
     * {@code paletteOut}.
     *
     * @param world        the source world
     * @param bounds       the axis-aligned bounding box of the region to extract
     * @param anchorPos    the schematic anchor in world coordinates; the region
     *                     origin is computed relative to this point
     * @param includeBiomes when {@code true}, biome data is sampled and included
     *                     in the returned region (feature flag bit 2)
     * @param paletteOut   caller-provided mutable list; blockstate strings are
     *                     appended as they are encountered. Must already contain
     *                     {@link MsfPalette#AIR} at index 0 if non-empty, or be
     *                     empty (in which case the method pre-populates air first).
     * @return the extracted {@link MsfRegion} with {@code blockData} IDs valid
     *         against the final state of {@code paletteOut}
     * @throws MsfPaletteException if the palette grows beyond 65535 entries
     */
    public static MsfRegion extract(
        ServerWorld world,
        BlockBox bounds,
        BlockPos anchorPos,
        boolean includeBiomes,
        List<String> paletteOut
    ) throws MsfPaletteException {
        int sizeX = bounds.getBlockCountX();
        int sizeY = bounds.getBlockCountY();
        int sizeZ = bounds.getBlockCountZ();

        // Ensure air is always at index 0 (Section 4.3)
        if (paletteOut.isEmpty()) {
            paletteOut.add(MsfPalette.AIR);
        }

        // Build reverse-lookup map from the existing palette content
        Map<String, Integer> paletteIndex = new HashMap<>(paletteOut.size() * 2);
        for (int i = 0; i < paletteOut.size(); i++) {
            paletteIndex.put(paletteOut.get(i), i);
        }

        int minX = bounds.getMinX();
        int minY = bounds.getMinY();
        int minZ = bounds.getMinZ();

        // Block data in YZX order (Section 7.4)
        int blockCount = (int) YzxOrder.blockCount(sizeX, sizeY, sizeZ);
        int[] blockData = new int[blockCount];

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState state = world.getBlockState(new BlockPos(minX + x, minY + y, minZ + z));
                    String blockstateStr = BlockStateBridge.toString(state);

                    int id = paletteIndex.computeIfAbsent(blockstateStr, k -> {
                        if (paletteOut.size() >= MsfPalette.MAX_ENTRIES) {
                            throw new PaletteOverflowException(
                                "Palette would exceed the maximum of " + MsfPalette.MAX_ENTRIES + " entries"
                            );
                        }
                        paletteOut.add(k);
                        return paletteOut.size() - 1;
                    });

                    blockData[(int) YzxOrder.index(x, y, z, sizeX, sizeZ)] = id;
                }
            }
        }

        int originX = minX - anchorPos.getX();
        int originY = minY - anchorPos.getY();
        int originZ = minZ - anchorPos.getZ();

        MsfRegion.Builder builder = MsfRegion.builder()
            .origin(originX, originY, originZ)
            .size(sizeX, sizeY, sizeZ)
            .blockData(blockData);

        if (includeBiomes) {
            // Biome data at 4×4×4 section resolution (Section 7.6)
            int qsizeX = divCeil(sizeX, 4);
            int qsizeY = divCeil(sizeY, 4);
            int qsizeZ = divCeil(sizeZ, 4);

            int biomeEntryCount = qsizeX * qsizeY * qsizeZ;
            int[] biomeData = new int[biomeEntryCount];
            List<String> biomePalette = new ArrayList<>();
            Map<String, Integer> biomeIndex = new HashMap<>();

            for (int qy = 0; qy < qsizeY; qy++) {
                for (int qz = 0; qz < qsizeZ; qz++) {
                    for (int qx = 0; qx < qsizeX; qx++) {
                        // Sample at minimum-corner of the 4×4×4 cell (Section 7.6)
                        BlockPos queryPos = new BlockPos(
                            minX + qx * 4,
                            minY + qy * 4,
                            minZ + qz * 4
                        );
                        RegistryEntry<Biome> entry = world.getBiome(queryPos);
                        // Extract identifier; fall back to plains for unkeyed entries
                        String biomeId = entry.getKey()
                            .map(k -> k.getValue().toString())
                            .orElse("minecraft:plains");

                        int biomeIdx = biomeIndex.computeIfAbsent(biomeId, k -> {
                            biomePalette.add(k);
                            return biomePalette.size() - 1;
                        });

                        // Store in YZX-quarter order (same formula as blocks, quarter-res dims)
                        biomeData[(int) YzxOrder.index(qx, qy, qz, qsizeX, qsizeZ)] = biomeIdx;
                    }
                }
            }

            builder.biomeData(biomeData, biomePalette);
        }

        try {
            return builder.build();
        } catch (PaletteOverflowException e) {
            throw new MsfPaletteException(e.getMessage());
        }
    }

    // =========================================================================
    // Entity extraction (Section 8)
    // =========================================================================

    /**
     * Extracts all non-player entities whose position falls within {@code bounds}
     * and returns them as {@link MsfEntity} records (Section 8).
     *
     * <p>UUIDs are stripped from each entity's NBT payload, and the {@code id} tag
     * is removed, as required by Section 8.2. Player entities are excluded.
     *
     * @param world     the source world
     * @param bounds    the axis-aligned bounding box to search
     * @param anchorPos the schematic anchor in world coordinates; entity positions are
     *                  recorded relative to this point
     * @return a mutable list of extracted entities (empty if none found)
     */
    public static List<MsfEntity> extractEntities(
        ServerWorld world,
        BlockBox bounds,
        BlockPos anchorPos
    ) {
        // Expand the Box by 1 on the + side so the upper bounds are inclusive
        Box searchBox = new Box(
            bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
            bounds.getMaxX() + 1, bounds.getMaxY() + 1, bounds.getMaxZ() + 1
        );
        List<MsfEntity> result = new ArrayList<>();
        for (Entity entity : world.getEntitiesByClass(Entity.class, searchBox,
                e -> !(e instanceof PlayerEntity))) {
            result.add(EntityBridge.fromEntity(entity, anchorPos));
        }
        return result;
    }

    // =========================================================================
    // Block entity extraction (Section 9)
    // =========================================================================

    /**
     * Extracts all block entities whose position falls within {@code bounds}
     * and returns them as {@link MsfBlockEntity} records (Section 9).
     *
     * <p>Positions are stored as offsets relative to {@code anchorPos}. UUID tags
     * and the {@code id}, {@code x}, {@code y}, {@code z} tags are removed from each
     * payload per Section 9.2.
     *
     * @param world     the source world
     * @param bounds    the axis-aligned bounding box to search
     * @param anchorPos the schematic anchor in world coordinates
     * @return a mutable list of extracted block entities (empty if none found)
     */
    public static List<MsfBlockEntity> extractBlockEntities(
        ServerWorld world,
        BlockBox bounds,
        BlockPos anchorPos
    ) {
        List<MsfBlockEntity> result = new ArrayList<>();
        for (int bx = bounds.getMinX(); bx <= bounds.getMaxX(); bx++) {
            for (int by = bounds.getMinY(); by <= bounds.getMaxY(); by++) {
                for (int bz = bounds.getMinZ(); bz <= bounds.getMaxZ(); bz++) {
                    BlockEntity be = world.getBlockEntity(new BlockPos(bx, by, bz));
                    if (be != null) {
                        result.add(BlockEntityBridge.fromBlockEntity(be, anchorPos));
                    }
                }
            }
        }
        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int divCeil(int a, int b) {
        return (a + b - 1) / b;
    }

    /** Unchecked carrier for palette overflow across lambda boundaries. */
    private static final class PaletteOverflowException extends RuntimeException {
        PaletteOverflowException(String message) {
            super(message);
        }
    }
}

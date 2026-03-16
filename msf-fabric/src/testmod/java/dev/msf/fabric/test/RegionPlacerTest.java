package dev.msf.fabric.test;

import dev.msf.core.MsfParseException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.world.CanonicalFacing;
import dev.msf.fabric.world.PlacementOptions;
import dev.msf.fabric.world.RegionPlacer;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for {@link RegionPlacer} — block placement and rotation.
 */
public class RegionPlacerTest {

    /**
     * Builds a minimal single-block {@link MsfFile} with the given blockstate at
     * the region origin.
     */
    private static MsfFile singleBlockFile(String blockstate) throws MsfPaletteException {
        List<String> paletteList = new ArrayList<>();
        paletteList.add(MsfPalette.AIR);
        paletteList.add(blockstate);
        MsfPalette palette = MsfPalette.of(paletteList);

        int[] blockData = {1}; // palette ID 1 = the given blockstate
        MsfRegion region = MsfRegion.builder()
            .origin(0, 0, 0)
            .size(1, 1, 1)
            .blockData(blockData)
            .build();

        MsfLayerIndex layerIndex = MsfLayerIndex.of(List.of(
            MsfLayer.builder().layerId(1).name("layer").addRegion(region).build()
        ));

        return MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("test").build())
            .palette(palette)
            .layerIndex(layerIndex)
            .build();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placeSingleStone(TestContext ctx) throws MsfPaletteException, MsfParseException {
        MsfFile file = singleBlockFile("minecraft:stone");
        BlockPos anchor = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        RegionPlacer.place(file, ctx.getWorld(), anchor, PlacementOptions.DEFAULT);

        ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.STONE),
            "Expected STONE at (1,1,1)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void skipAirDoesNotOverwriteExistingBlock(TestContext ctx)
            throws MsfPaletteException, MsfParseException {
        // Pre-place oak log so we can verify skipAir leaves it untouched
        ctx.setBlockState(1, 1, 1, Blocks.OAK_LOG.getDefaultState());

        // Build file with AIR at origin (ID 0)
        MsfPalette palette = MsfPalette.of(List.of(MsfPalette.AIR));
        MsfRegion region = MsfRegion.builder()
            .origin(0, 0, 0)
            .size(1, 1, 1)
            .build(); // blockData defaults to all zeros (air)
        MsfFile file = MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("test").build())
            .palette(palette)
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        PlacementOptions skipAir = new PlacementOptions(
            true, false, false, CanonicalFacing.NORTH, CanonicalFacing.NORTH
        );
        BlockPos anchor = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        RegionPlacer.place(file, ctx.getWorld(), anchor, skipAir);

        ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.OAK_LOG),
            "skipAir must not overwrite existing block with air");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rotationCW90FlipsXZ(TestContext ctx) throws MsfPaletteException, MsfParseException {
        // Region: stone at origin (0,0,0), size 1x1x1. Origin is at relative (1,1,0).
        // With no rotation it goes to anchor + (0,0,0) = anchor.
        // With CW90 rotation: (x,z) -> (-z, x), so (0,0) -> (0,0). Same position for origin.
        // Use a 2-block region to observe rotation:
        // Block A at (1,0,0), Block B at (0,0,1) — after CW90: A goes to (0,1) = (0,0,-1)?
        // Actually let's do a simpler check:
        // Place a facing-dependent block (oak_stairs[facing=north]) and verify it rotates to east.
        List<String> paletteList = new ArrayList<>();
        paletteList.add(MsfPalette.AIR);
        paletteList.add("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        MsfPalette palette = MsfPalette.of(paletteList);
        MsfRegion region = MsfRegion.builder()
            .origin(0, 0, 0)
            .size(1, 1, 1)
            .blockData(new int[]{1})
            .build();
        MsfFile file = MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("test").build())
            .palette(palette)
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        // Rotate from NORTH to EAST (CW90)
        PlacementOptions opts = new PlacementOptions(
            false, false, false, CanonicalFacing.NORTH, CanonicalFacing.EAST
        );
        BlockPos anchor = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        RegionPlacer.place(file, ctx.getWorld(), anchor, opts);

        // After CW90, north-facing stairs become east-facing
        String placed = dev.msf.fabric.bridge.BlockStateBridge.toString(
            ctx.getBlockState(new BlockPos(2, 2, 2))
        );
        ctx.assertTrue(placed.contains("facing=east"),
            "CW90 rotation should produce facing=east, got: " + placed);
        ctx.complete();
    }
}

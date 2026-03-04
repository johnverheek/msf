package dev.msf.fabric.test;

import dev.msf.core.MsfException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.world.PlacementOptions;
import dev.msf.fabric.world.RegionExtractor;
import dev.msf.fabric.world.RegionPlacer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end gametest: extract blocks from the world, serialize to MSF bytes,
 * deserialize from bytes, and place back — verifying the blocks are restored.
 */
public class FullRoundTripTest implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void twoBlockRoundTrip(TestContext ctx) throws MsfPaletteException, MsfException {
        // ---- Setup: place two distinctive blocks in area A (y=1) ----
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ctx.setBlockState(2, 1, 1, Blocks.OAK_LOG.getDefaultState());

        ServerWorld world = ctx.getWorld();
        BlockPos extractAnchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos from = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        // ---- Extract ----
        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            world, BlockBox.create(from, to), "region", extractAnchor, false, palette
        );

        MsfFile file = MsfFile.builder()
            .mcDataVersion(SharedConstants.getGameVersion().getSaveVersion().getId())
            .metadata(MsfMetadata.builder().name("roundtrip-test").build())
            .palette(MsfPalette.of(new ArrayList<>(palette)))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        // ---- Serialize → deserialize ----
        byte[] bytes = MsfWriter.writeFile(file, null);
        MsfFile readFile = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);

        // ---- Clear area A so placement result is visible ----
        ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());
        ctx.setBlockState(2, 1, 1, Blocks.AIR.getDefaultState());

        // ---- Place the deserialized file back at the same anchor ----
        RegionPlacer.place(readFile, world, extractAnchor, PlacementOptions.DEFAULT);

        // ---- Verify area A was restored ----
        ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.STONE),
            "STONE must be restored at (1,1,1)");
        ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 1, 1)).isOf(Blocks.OAK_LOG),
            "OAK_LOG must be restored at (2,1,1)");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fileSizeIsNonZeroAfterWrite(TestContext ctx) throws MsfPaletteException, MsfException {
        // Minimal single-air-block file
        MsfRegion region = MsfRegion.builder()
            .name("r")
            .origin(0, 0, 0)
            .size(1, 1, 1)
            .build();
        MsfFile file = MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("minimal").build())
            .palette(MsfPalette.of(List.of(MsfPalette.AIR)))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        byte[] bytes = MsfWriter.writeFile(file, null);
        ctx.assertTrue(bytes.length > 48, "Serialized MSF file must be larger than the 48-byte header");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void palettePreservedAcrossRoundTrip(TestContext ctx) throws MsfPaletteException, MsfException {
        ctx.setBlockState(1, 1, 1, Blocks.GRASS_BLOCK.getDefaultState());

        ServerWorld world = ctx.getWorld();
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            world, BlockBox.create(worldPos, worldPos), "r", anchor, false, palette
        );

        MsfFile file = MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("test").build())
            .palette(MsfPalette.of(new ArrayList<>(palette)))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        byte[] bytes = MsfWriter.writeFile(file, null);
        MsfFile readFile = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);

        // The global palette must contain grass_block
        boolean hasGrass = readFile.palette().entries().stream()
            .anyMatch(e -> e.contains("minecraft:grass_block"));
        ctx.assertTrue(hasGrass, "Grass block must appear in deserialized palette");
        ctx.complete();
    }
}

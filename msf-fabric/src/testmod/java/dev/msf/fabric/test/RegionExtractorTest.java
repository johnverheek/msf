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
import dev.msf.fabric.world.RegionExtractor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for {@link RegionExtractor} — verifies palette building and block data extraction.
 */
public class RegionExtractorTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void airPreseededAtIndexZero(TestContext ctx) throws MsfPaletteException {
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        RegionExtractor.extract(ctx.getWorld(), bounds, anchor, false, palette);

        ctx.assertTrue(!palette.isEmpty(), "Palette must not be empty after extract");
        ctx.assertTrue(MsfPalette.AIR.equals(palette.get(0)),
            "Air must be at index 0, got " + palette.get(0));
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void singleStoneBlockInPalette(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, false, palette
        );

        ctx.assertTrue(palette.contains("minecraft:stone"), "Stone not in palette");
        int stoneId = palette.indexOf("minecraft:stone");
        ctx.assertTrue(region.blockData()[0] == stoneId,
            "blockData[0] should be stone ID " + stoneId + ", got " + region.blockData()[0]);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoIdenticalBlocksDeduplicated(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ctx.setBlockState(2, 1, 1, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos from = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 1, 1));
        BlockBox bounds = BlockBox.create(from, to);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, false, palette
        );

        int stoneId = palette.indexOf("minecraft:stone");
        ctx.assertTrue(stoneId >= 0, "Stone not in palette");

        long stoneOccurrences = palette.stream().filter("minecraft:stone"::equals).count();
        ctx.assertTrue(stoneOccurrences == 1, "Stone must appear exactly once in palette, found " + stoneOccurrences);

        ctx.assertTrue(region.blockData()[0] == stoneId, "blockData[0] != stone");
        ctx.assertTrue(region.blockData()[1] == stoneId, "blockData[1] != stone");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void regionOriginRelativeToAnchor(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(2, 1, 2, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, false, palette
        );

        ctx.assertEquals(2, region.originX(), "originX");
        ctx.assertEquals(1, region.originY(), "originY");
        ctx.assertEquals(2, region.originZ(), "originZ");
        ctx.complete();
    }

    // =========================================================================
    // Biome extraction tests
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void includeBiomesPopulatesBiomePalette(TestContext ctx) throws MsfPaletteException {
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, true, palette
        );

        ctx.assertTrue(region.hasBiomeData(),
            "hasBiomeData() must return true when includeBiomes=true");
        ctx.assertTrue(!region.biomePalette().isEmpty(),
            "Biome palette must not be empty after extract with includeBiomes=true");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void biomeDataLengthMatchesFormula(TestContext ctx) throws MsfPaletteException {
        // 1×1×1 region: ceil(1/4) × ceil(1/4) × ceil(1/4) = 1 biome entry (Section 7.6)
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, true, palette
        );

        ctx.assertTrue(region.biomeData() != null,
            "biomeData must not be null when includeBiomes=true");
        ctx.assertTrue(region.biomeData().length == 1,
            "biomeData.length must be 1 for 1×1×1 region (ceil(1/4)^3), got "
            + region.biomeData().length);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void biomeRoundTripPreservesBiomePalette(TestContext ctx)
            throws MsfPaletteException, MsfException {
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, anchor, true, palette
        );
        List<String> extractedBiomePalette = new ArrayList<>(region.biomePalette());

        MsfFile file = MsfFile.builder()
            .mcDataVersion(0L)
            .metadata(MsfMetadata.builder().name("biome-rt").build())
            .palette(MsfPalette.of(new ArrayList<>(palette)))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(region).build()
            )))
            .build();

        byte[] bytes = MsfWriter.writeFile(file, null);
        MsfFile readFile = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);

        MsfRegion readRegion = readFile.layerIndex().layers().get(0).regions().get(0);
        ctx.assertTrue(readRegion.hasBiomeData(),
            "hasBiomeData() must be true after round-trip");
        ctx.assertTrue(readRegion.biomePalette().equals(extractedBiomePalette),
            "Biome palette must be preserved. Expected: " + extractedBiomePalette
            + ", got: " + readRegion.biomePalette());
        ctx.complete();
    }
}

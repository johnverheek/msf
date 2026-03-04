package dev.msf.fabric.test;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.world.RegionExtractor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for {@link RegionExtractor} — verifies palette building and block data extraction.
 */
public class RegionExtractorTest implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void airPreseededAtIndexZero(TestContext ctx) throws MsfPaletteException {
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        RegionExtractor.extract(ctx.getWorld(), bounds, "test", anchor, false, palette);

        ctx.assertTrue(!palette.isEmpty(), "Palette must not be empty after extract");
        ctx.assertTrue(MsfPalette.AIR.equals(palette.get(0)),
            "Air must be at index 0, got " + palette.get(0));
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void singleStoneBlockInPalette(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, "test", anchor, false, palette
        );

        ctx.assertTrue(palette.contains("minecraft:stone"), "Stone not in palette");
        int stoneId = palette.indexOf("minecraft:stone");
        ctx.assertTrue(region.blockData()[0] == stoneId,
            "blockData[0] should be stone ID " + stoneId + ", got " + region.blockData()[0]);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void twoIdenticalBlocksDeduplicated(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ctx.setBlockState(2, 1, 1, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos from = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 1, 1));
        BlockBox bounds = BlockBox.create(from, to);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, "test", anchor, false, palette
        );

        int stoneId = palette.indexOf("minecraft:stone");
        ctx.assertTrue(stoneId >= 0, "Stone not in palette");

        long stoneOccurrences = palette.stream().filter("minecraft:stone"::equals).count();
        ctx.assertTrue(stoneOccurrences == 1, "Stone must appear exactly once in palette, found " + stoneOccurrences);

        ctx.assertTrue(region.blockData()[0] == stoneId, "blockData[0] != stone");
        ctx.assertTrue(region.blockData()[1] == stoneId, "blockData[1] != stone");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void regionOriginRelativeToAnchor(TestContext ctx) throws MsfPaletteException {
        ctx.setBlockState(2, 1, 2, Blocks.STONE.getDefaultState());

        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockBox bounds = BlockBox.create(worldPos, worldPos);

        List<String> palette = new ArrayList<>();
        MsfRegion region = RegionExtractor.extract(
            ctx.getWorld(), bounds, "test", anchor, false, palette
        );

        ctx.assertEquals(2, region.originX(), "originX");
        ctx.assertEquals(1, region.originY(), "originY");
        ctx.assertEquals(2, region.originZ(), "originZ");
        ctx.complete();
    }
}

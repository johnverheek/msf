package dev.msf.fabric.test;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfPalette;
import dev.msf.fabric.bridge.BlockStateBridge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Gametests for {@link BlockStateBridge} — toString / fromString round-trips.
 *
 * <p>No world blocks are used; the tests run against the registry directly.
 */
public class BlockStateBridgeTest implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stoneNoProperties(TestContext ctx) {
        String s = BlockStateBridge.toString(Blocks.STONE.getDefaultState());
        ctx.assertTrue("minecraft:stone".equals(s), "Expected minecraft:stone, got " + s);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void airRoundTrip(TestContext ctx) throws MsfPaletteException {
        BlockState state = BlockStateBridge.fromString(MsfPalette.AIR);
        String s = BlockStateBridge.toString(state);
        ctx.assertTrue(MsfPalette.AIR.equals(s), "Expected minecraft:air, got " + s);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stairsRoundTrip(TestContext ctx) throws MsfPaletteException {
        String input = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
        BlockState state = BlockStateBridge.fromString(input);
        String result = BlockStateBridge.toString(state);
        ctx.assertTrue(input.equals(result), "Expected:\n  " + input + "\nGot:\n  " + result);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unknownBlockThrows(TestContext ctx) {
        try {
            BlockStateBridge.fromString("minecraft:no_such_block_exists_at_all");
            ctx.assertTrue(false, "Expected MsfPaletteException for unknown block");
        } catch (MsfPaletteException e) {
            ctx.complete();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invalidPropertyThrows(TestContext ctx) {
        try {
            // stone has no properties
            BlockStateBridge.fromString("minecraft:stone[facing=north]");
            ctx.assertTrue(false, "Expected MsfPaletteException for invalid property on stone");
        } catch (MsfPaletteException e) {
            ctx.complete();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void logAllSixPropertiesRoundTrip(TestContext ctx) throws MsfPaletteException {
        // oak_log[axis=y] — 1 property
        String input = "minecraft:oak_log[axis=y]";
        BlockState state = BlockStateBridge.fromString(input);
        String result = BlockStateBridge.toString(state);
        ctx.assertTrue(input.equals(result), "Expected " + input + ", got " + result);
        ctx.complete();
    }
}

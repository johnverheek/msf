package dev.msf.fabric.test;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.command.MsfCommands;
import dev.msf.fabric.world.CanonicalFacing;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gametests for {@link MsfCommands} extract and place logic.
 */
public class MsfCommandsTest implements FabricGameTest {

    // =========================================================================
    // 1. Extract → write → read back: block count and palette
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void extractWriteReadBack_blockCountMatchesPaletteSize(TestContext ctx) throws Exception {
        // 2×1×2 area: stone and dirt, with one air corner
        ctx.setBlockState(0, 1, 0, Blocks.STONE.getDefaultState());
        ctx.setBlockState(1, 1, 0, Blocks.DIRT.getDefaultState());
        ctx.setBlockState(0, 1, 1, Blocks.STONE.getDefaultState());
        // (1,1,1) stays air

        ServerWorld world = ctx.getWorld();
        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-cmd-test-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, BlockBox.create(from, to), CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract must return 1 on success");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            MsfRegion region = file.layerIndex().layers().get(0).regions().get(0);

            ctx.assertTrue(region.blockData().length == 4, "2×1×2 region must have 4 blockData entries");

            boolean hasStone = file.palette().entries().stream().anyMatch(e -> e.contains("minecraft:stone"));
            boolean hasDirt  = file.palette().entries().stream().anyMatch(e -> e.contains("minecraft:dirt"));
            ctx.assertTrue(hasStone, "Palette must contain stone");
            ctx.assertTrue(hasDirt,  "Palette must contain dirt");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 2. Extract → place round-trip: block at known position
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void extractPlaceRoundTrip_blockAtKnownPosition(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ctx.setBlockState(2, 1, 1, Blocks.GOLD_BLOCK.getDefaultState());

        ServerWorld world = ctx.getWorld();
        BlockPos from = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        Path output = Files.createTempFile("msf-rtrip-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(from, to), CanonicalFacing.NORTH, output, msg -> {}
            );

            // Clear originals
            ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());
            ctx.setBlockState(2, 1, 1, Blocks.AIR.getDefaultState());

            // Place back at the same anchor (min corner of the extracted bounds)
            MsfCommands.executePlace(world, from, CanonicalFacing.NORTH, output, msg -> {});

            ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.STONE),
                "STONE must be restored at (1,1,1)");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 1, 1)).isOf(Blocks.GOLD_BLOCK),
                "GOLD_BLOCK must be restored at (2,1,1)");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 3. Place with East facing: directional block is rotated
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placeWithEastFacing_rotatesDirectionalBlock(TestContext ctx) throws Exception {
        // Oak log with axis=Z (north-south orientation)
        BlockState logAxisZ = Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z);
        ctx.setBlockState(1, 1, 1, logAxisZ);

        ServerWorld world = ctx.getWorld();
        BlockPos from = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-rot-", ".msf");
        try {
            // Extract with canonical facing NORTH
            MsfCommands.executeExtract(
                world, BlockBox.create(from, from), CanonicalFacing.NORTH, output, msg -> {}
            );

            ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());

            // Place with target facing EAST → CLOCKWISE_90 rotation → axis=Z becomes axis=X
            MsfCommands.executePlace(world, from, CanonicalFacing.EAST, output, msg -> {});

            BlockState placed = ctx.getBlockState(new BlockPos(1, 1, 1));
            ctx.assertTrue(placed.isOf(Blocks.OAK_LOG), "OAK_LOG must be placed at (1,1,1)");
            ctx.assertTrue(
                placed.get(Properties.AXIS) == Direction.Axis.X,
                "axis must be X after 90° CW rotation (NORTH→EAST)"
            );
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 4. File not found: feedback contains error message
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void place_fileNotFound_sendsFeedback(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos anchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Path nonexistent = Path.of("msf-schematics", "this-file-does-not-exist-7f3a9.msf");

        AtomicReference<String> feedbackText = new AtomicReference<>("");
        int result = MsfCommands.executePlace(
            world, anchor, CanonicalFacing.NORTH, nonexistent,
            msg -> feedbackText.set(msg.getString())
        );

        ctx.assertTrue(result == 0, "executePlace must return 0 for missing file");
        ctx.assertTrue(
            feedbackText.get().toLowerCase().contains("not found") || feedbackText.get().toLowerCase().contains("error"),
            "Feedback must describe the error; got: " + feedbackText.get()
        );
        ctx.complete();
    }
}

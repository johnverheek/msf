package dev.msf.fabric.test;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.command.MsfCommands;
import dev.msf.fabric.world.CanonicalFacing;
import dev.msf.fabric.world.Mirror;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gametests for {@link MsfCommands} extract, place, list, and info logic.
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

    // =========================================================================
    // 5. F3.1 — nameOverride sets schematic name in metadata
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void extractWithNameOverride_metadataNameMatches(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-name-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH,
                output, true, "TestSchematic", msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract with nameOverride must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            ctx.assertTrue("TestSchematic".equals(file.metadata().name()),
                "Metadata name must equal nameOverride; got: " + file.metadata().name());
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 6. F3.1 — includeEntities=false means no block entities in output
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void extractWithEntitiesFalse_noBlockEntitiesInFile(TestContext ctx) throws Exception {
        // Place a chest (has a block entity)
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-noents-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH,
                output, false, null, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract with includeEntities=false must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            ctx.assertTrue(file.blockEntities().isEmpty(),
                "No block entities must be present when includeEntities=false");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 7. F1.1 — Mirror.X flips the Z position of placed blocks
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placeWithMirrorX_blockAtMirroredZPosition(TestContext ctx) throws Exception {
        // Stone at relative (1,1,3); region from (1,1,2)–(1,1,3); anchor at (1,1,2)
        ctx.setBlockState(1, 1, 3, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos anchor = ctx.getAbsolutePos(new BlockPos(1, 1, 2));
        BlockPos stoneWPos = ctx.getAbsolutePos(new BlockPos(1, 1, 3));

        Path output = Files.createTempFile("msf-mirror-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(anchor, stoneWPos), CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.setBlockState(1, 1, 3, Blocks.AIR.getDefaultState()); // clear original

            // Mirror.X = LEFT_RIGHT = flip Z:
            // Stone was at relative (0,0,1) → mirrored → (0,0,-1) → anchor + (0,0,-1) = relative (1,1,1)
            int result = MsfCommands.executePlace(
                world, anchor, CanonicalFacing.NORTH, Mirror.X, null, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executePlace with Mirror.X must return 1");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.STONE),
                "Stone must appear at mirrored Z position relative(1,1,1)");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 3)).isOf(Blocks.AIR),
                "Original stone position must remain air after mirror placement");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 8. F2.1 — Layer filter: matching layer name places blocks
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placeWithLayerFilter_matchingLayerName_placesBlocks(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.EMERALD_BLOCK.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-layer-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());

            // executeExtract always stores blocks in a layer named "layer"
            int result = MsfCommands.executePlace(
                world, pos, CanonicalFacing.NORTH, Mirror.NONE, "layer", output, msg -> {}
            );
            ctx.assertTrue(result == 1, "Place with matching layer filter must return 1");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).isOf(Blocks.EMERALD_BLOCK),
                "EMERALD_BLOCK must be placed when layer filter matches");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 9. F2.1 — Layer filter: non-matching layer name returns 0
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placeWithLayerFilter_noMatchingLayer_returnsZero(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-nolayer-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {}
            );

            AtomicReference<String> feedbackText = new AtomicReference<>("");
            int result = MsfCommands.executePlace(
                world, pos, CanonicalFacing.NORTH, Mirror.NONE, "nonexistent-layer", output,
                msg -> feedbackText.set(msg.getString())
            );
            ctx.assertTrue(result == 0, "Place with non-matching layer filter must return 0");
            ctx.assertTrue(feedbackText.get().contains("nonexistent-layer"),
                "Feedback must mention the missing layer name; got: " + feedbackText.get());
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 10. F5.1 / V1.1 — executeInfo outputs format version and impl version
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void executeInfo_outputsFormatAndImplVersion(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-info-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {}
            );

            List<String> lines = new ArrayList<>();
            int result = MsfCommands.executeInfo(output, msg -> lines.add(msg.getString()));

            ctx.assertTrue(result == 1, "executeInfo must return 1 on success");
            String allText = String.join("\n", lines);
            ctx.assertTrue(allText.contains("Format: V"),
                "Info output must contain 'Format: V'; got: " + allText);
            ctx.assertTrue(allText.contains(MsfCommands.FABRIC_VERSION),
                "Info output must contain FABRIC_VERSION; got: " + allText);
            ctx.assertTrue(allText.contains("NORTH"),
                "Info output must contain canonical facing; got: " + allText);
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 11. F5.1 — executeInfo returns 0 for missing file
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void executeInfo_fileNotFound_returnsZero(TestContext ctx) {
        AtomicReference<String> feedbackText = new AtomicReference<>("");
        int result = MsfCommands.executeInfo(
            Path.of("msf-schematics", "no-such-info-file-xyz.msf"),
            msg -> feedbackText.set(msg.getString())
        );
        ctx.assertTrue(result == 0, "executeInfo must return 0 for missing file");
        ctx.assertTrue(feedbackText.get().toLowerCase().contains("not found"),
            "Feedback must say 'not found'; got: " + feedbackText.get());
        ctx.complete();
    }

    // =========================================================================
    // 12. F4.1 — executeList does not throw and always sends feedback
    // =========================================================================

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void executeList_alwaysSendsFeedback(TestContext ctx) {
        AtomicReference<String> feedbackText = new AtomicReference<>("");
        int result = MsfCommands.executeList(1, msg -> {
            String s = msg.getString();
            if (!s.isEmpty()) feedbackText.set(s);
        });
        ctx.assertTrue(result == 0 || result == 1, "executeList must return 0 or 1");
        ctx.assertTrue(!feedbackText.get().isEmpty(), "executeList must always send at least one feedback message");
        ctx.complete();
    }
}

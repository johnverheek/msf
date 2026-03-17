package dev.msf.fabric.test;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.command.MsfCommands;
import dev.msf.fabric.world.CanonicalFacing;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import net.minecraft.util.BlockMirror;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gametests for {@link MsfCommands} extract and place logic.
 */
public class MsfCommandsTest {

    // =========================================================================
    // 1. Extract → write → read back: block count and palette
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
    // 5. Armor stand round-trip: entity extracted, placed, re-appears in world
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractPlace_armorStand_roundTrip(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();

        // Spawn armor stand at a known position within the test bounds
        BlockPos standRelPos = new BlockPos(2, 1, 2);
        BlockPos standAbsPos = ctx.getAbsolutePos(standRelPos);
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, net.minecraft.entity.SpawnReason.LOAD);
        if (stand == null) throw new AssertionError("EntityType.ARMOR_STAND.create() returned null");
        stand.setPosition(standAbsPos.getX() + 0.5, standAbsPos.getY(), standAbsPos.getZ() + 0.5);
        world.spawnEntity(stand);

        // Extract a 5×3×5 region that contains the stand
        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-armorstand-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract must return 1");

            // Verify entity block is present and non-empty
            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            ctx.assertTrue(file.entities().isPresent() && !file.entities().get().isEmpty(),
                "Extracted file must contain the armor stand in the entity block");
            ctx.assertTrue("minecraft:armor_stand".equals(file.entities().get().get(0).entityType()),
                "Entity type must be minecraft:armor_stand");

            // Kill the original stand and verify it's gone
            stand.kill(world);
            Box searchBox = new Box(from.getX(), from.getY(), from.getZ(),
                to.getX() + 1, to.getY() + 1, to.getZ() + 1);
            ctx.assertTrue(world.getEntitiesByClass(ArmorStandEntity.class, searchBox, e -> true).isEmpty(),
                "Original armor stand must be gone before placement");

            // Place and verify the armor stand reappears
            MsfCommands.executePlace(world, from, CanonicalFacing.NORTH, output, msg -> {});
            List<ArmorStandEntity> stands = world.getEntitiesByClass(ArmorStandEntity.class, searchBox, e -> true);
            ctx.assertTrue(!stands.isEmpty(), "At least one armor stand must be present after placement");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 6. Chest round-trip: block entity NBT (inventory item) preserved
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractPlace_chestWithItem_blockEntityRoundTrip(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();

        // Place a chest and put a diamond in slot 0
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());
        BlockPos chestAbsPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(chestAbsPos);
        ctx.assertTrue(chest != null, "ChestBlockEntity must be non-null after placement");
        chest.setStack(0, new ItemStack(Items.DIAMOND));

        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-chest-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract must return 1");

            // Verify block entity block is present
            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            ctx.assertTrue(file.blockEntities().isPresent() && !file.blockEntities().get().isEmpty(),
                "Extracted file must contain the chest in the block entity block");

            // Clear the original chest
            ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());

            // Place and verify the chest contents are restored
            MsfCommands.executePlace(world, from, CanonicalFacing.NORTH, output, msg -> {});
            ChestBlockEntity restored = (ChestBlockEntity) world.getBlockEntity(chestAbsPos);
            ctx.assertTrue(restored != null, "ChestBlockEntity must be restored after placement");
            ctx.assertTrue(restored.getStack(0).isOf(Items.DIAMOND),
                "Diamond must be present in slot 0 of the restored chest");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 7. Extract blocks-only region: feature flag bits 0 and 1 clear
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extract_blocksOnly_featureFlagsEntityBitsClear(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-noflag-", ".msf");
        try {
            MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {}
            );
            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            ctx.assertTrue(file.entities().isEmpty(),
                "Blocks-only region: entity block must be absent (bit 0 clear)");
            ctx.assertTrue(file.blockEntities().isEmpty(),
                "Blocks-only region: block entity block must be absent (bit 1 clear)");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 9. Place with --rotate 90: block positions match CW90 transform (planning gate round-trip)
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placeWithRotate90_blockPositionsMatch(TestContext ctx) throws Exception {
        // 2×1×1 region: stone at (2,1,2), gold block at (3,1,2). Anchor = abs(2,1,2).
        ctx.setBlockState(2, 1, 2, Blocks.STONE.getDefaultState());
        ctx.setBlockState(3, 1, 2, Blocks.GOLD_BLOCK.getDefaultState());

        ServerWorld world = ctx.getWorld();
        BlockPos anchor = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos to     = ctx.getAbsolutePos(new BlockPos(3, 1, 2));

        Path output = Files.createTempFile("msf-rot90-", ".msf");
        try {
            MsfCommands.executeExtract(world, BlockBox.create(anchor, to), CanonicalFacing.NORTH, output, msg -> {});

            ctx.setBlockState(2, 1, 2, Blocks.AIR.getDefaultState());
            ctx.setBlockState(3, 1, 2, Blocks.AIR.getDefaultState());

            // --rotate 90 → CW90 transform: (x,z) → (-z, x)
            // Stone at local (0,0) → (0,0)  → anchor        → relPos (2,1,2)
            // Gold  at local (1,0) → (0,1)  → anchor+(0,0,1) → relPos (2,1,3)
            int result = MsfCommands.executePlace(
                world, anchor, CanonicalFacing.NORTH, 90, BlockMirror.NONE, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executePlace with --rotate 90 must return 1");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 1, 2)).isOf(Blocks.STONE),
                "STONE must remain at anchor after CW90");
            ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 1, 3)).isOf(Blocks.GOLD_BLOCK),
                "GOLD_BLOCK must move from x+1 to z+1 after CW90 rotation");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 10. Place with --mirror x: blockstate is reflected (LEFT_RIGHT)
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placeWithMirrorX_blockStateMirrored(TestContext ctx) throws Exception {
        // Oak stairs facing east — LEFT_RIGHT mirror should produce facing=west
        BlockState stairsEast = Blocks.OAK_STAIRS.getDefaultState()
            .with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.EAST);
        ctx.setBlockState(2, 1, 2, stairsEast);

        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));

        Path output = Files.createTempFile("msf-mirx-", ".msf");
        try {
            MsfCommands.executeExtract(world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {});
            ctx.setBlockState(2, 1, 2, Blocks.AIR.getDefaultState());

            int result = MsfCommands.executePlace(
                world, pos, CanonicalFacing.NORTH, null, BlockMirror.LEFT_RIGHT, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executePlace with --mirror x must return 1");

            BlockState placed = ctx.getBlockState(new BlockPos(2, 1, 2));
            ctx.assertTrue(placed.isOf(Blocks.OAK_STAIRS), "OAK_STAIRS must be placed after mirror");
            ctx.assertTrue(
                placed.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING) == Direction.WEST,
                "LEFT_RIGHT mirror of east-facing stairs must yield west; got: "
                    + placed.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)
            );
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 11. Place with --rotate 90 --mirror x combined: both transforms applied
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placeWithRotateAndMirror_combined(TestContext ctx) throws Exception {
        // Oak stairs facing east:
        //   mirror LEFT_RIGHT first  → facing=west
        //   rotate CW90              → facing=north  (west + CW90 → north)
        BlockState stairsEast = Blocks.OAK_STAIRS.getDefaultState()
            .with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.EAST);
        ctx.setBlockState(2, 1, 2, stairsEast);

        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));

        Path output = Files.createTempFile("msf-rotmir-", ".msf");
        try {
            MsfCommands.executeExtract(world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {});
            ctx.setBlockState(2, 1, 2, Blocks.AIR.getDefaultState());

            int result = MsfCommands.executePlace(
                world, pos, CanonicalFacing.NORTH, 90, BlockMirror.LEFT_RIGHT, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executePlace with --rotate 90 --mirror x must return 1");

            BlockState placed = ctx.getBlockState(new BlockPos(2, 1, 2));
            ctx.assertTrue(placed.isOf(Blocks.OAK_STAIRS), "OAK_STAIRS must be placed after rotate+mirror");
            ctx.assertTrue(
                placed.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING) == Direction.NORTH,
                "east + LEFT_RIGHT mirror + CW90 must yield north; got: "
                    + placed.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)
            );
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 8. Place file with no entity block: no exception thrown
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void place_fileWithNoEntityBlock_doesNotThrow(TestContext ctx) throws Exception {
        ctx.setBlockState(1, 1, 1, Blocks.STONE.getDefaultState());
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        Path output = Files.createTempFile("msf-noentity-", ".msf");
        try {
            // Extract a blocks-only file (no entities, no block entities)
            MsfCommands.executeExtract(
                world, BlockBox.create(pos, pos), CanonicalFacing.NORTH, output, msg -> {}
            );
            ctx.setBlockState(1, 1, 1, Blocks.AIR.getDefaultState());

            // Place must succeed without exception
            AtomicReference<String> feedbackText = new AtomicReference<>("");
            int result = MsfCommands.executePlace(
                world, pos, CanonicalFacing.NORTH, output, msg -> feedbackText.set(msg.getString())
            );
            ctx.assertTrue(result == 1, "Place of no-entity file must return 1; feedback: " + feedbackText.get());
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }
}

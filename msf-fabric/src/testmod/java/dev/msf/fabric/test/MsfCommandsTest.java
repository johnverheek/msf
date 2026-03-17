package dev.msf.fabric.test;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.command.MsfCommands;
import dev.msf.fabric.world.CanonicalFacing;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
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

    // =========================================================================
    // 12. Entity capture policy — no flag: villager excluded, armor stand captured
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractDefaultPolicy_excludesVillager_includesArmorStand(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();

        // Spawn a villager (LivingEntity — excluded by default)
        BlockPos villagerRelPos = new BlockPos(1, 1, 1);
        BlockPos villagerAbsPos = ctx.getAbsolutePos(villagerRelPos);
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.LOAD);
        if (villager == null) throw new AssertionError("EntityType.VILLAGER.create() returned null");
        villager.setPosition(villagerAbsPos.getX() + 0.5, villagerAbsPos.getY(), villagerAbsPos.getZ() + 0.5);
        world.spawnEntity(villager);

        // Spawn an armor stand (LivingEntity subtype — always captured regardless of flag)
        BlockPos standRelPos = new BlockPos(2, 1, 2);
        BlockPos standAbsPos = ctx.getAbsolutePos(standRelPos);
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, SpawnReason.LOAD);
        if (stand == null) throw new AssertionError("EntityType.ARMOR_STAND.create() returned null");
        stand.setPosition(standAbsPos.getX() + 0.5, standAbsPos.getY(), standAbsPos.getZ() + 0.5);
        world.spawnEntity(stand);

        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-policy-default-", ".msf");
        try {
            // Default: includeLivingMobs=false, numLayers=1
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, false, 1, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract (default policy) must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);

            ctx.assertTrue(file.entities().isPresent() && !file.entities().get().isEmpty(),
                "Entity block must be present (armor stand must be captured)");

            boolean hasVillager = file.entities().get().stream()
                .anyMatch(e -> e.entityType().equals("minecraft:villager"));
            boolean hasArmorStand = file.entities().get().stream()
                .anyMatch(e -> e.entityType().equals("minecraft:armor_stand"));

            ctx.assertTrue(!hasVillager, "Villager must NOT be captured with default policy");
            ctx.assertTrue(hasArmorStand, "Armor stand MUST be captured with default policy");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 13. Entity capture policy — --living-mobs flag: villager and armor stand both captured
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractWithLivingMobsFlag_capturesBoth(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();

        // Spawn a villager
        BlockPos villagerAbsPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.LOAD);
        if (villager == null) throw new AssertionError("EntityType.VILLAGER.create() returned null");
        villager.setPosition(villagerAbsPos.getX() + 0.5, villagerAbsPos.getY(), villagerAbsPos.getZ() + 0.5);
        world.spawnEntity(villager);

        // Spawn an armor stand
        BlockPos standAbsPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, SpawnReason.LOAD);
        if (stand == null) throw new AssertionError("EntityType.ARMOR_STAND.create() returned null");
        stand.setPosition(standAbsPos.getX() + 0.5, standAbsPos.getY(), standAbsPos.getZ() + 0.5);
        world.spawnEntity(stand);

        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 0, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-policy-livingmobs-", ".msf");
        try {
            // includeLivingMobs=true
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, true, 1, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract (--living-mobs) must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);

            ctx.assertTrue(file.entities().isPresent() && file.entities().get().size() >= 2,
                "Entity block must contain at least 2 entities (villager + armor stand)");

            boolean hasVillager = file.entities().get().stream()
                .anyMatch(e -> e.entityType().equals("minecraft:villager"));
            boolean hasArmorStand = file.entities().get().stream()
                .anyMatch(e -> e.entityType().equals("minecraft:armor_stand"));

            ctx.assertTrue(hasVillager, "Villager MUST be captured with --living-mobs");
            ctx.assertTrue(hasArmorStand, "Armor stand MUST be captured with --living-mobs");
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 14. --layers 2 on a 10-block-tall region: two layers of height 5 each
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractWithLayers2_tenBlockTall_twoLayersOfFive(TestContext ctx) throws Exception {
        // Place stone through the full 10-block column so the extract has real content
        ServerWorld world = ctx.getWorld();
        for (int y = 1; y <= 10; y++) {
            ctx.setBlockState(1, y, 1, Blocks.STONE.getDefaultState());
        }

        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 10, 2));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-layers2-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, false, 2, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract --layers 2 must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            List<MsfLayer> layers = file.layerIndex().layers();

            ctx.assertTrue(layers.size() == 2, "Must produce 2 layers; got " + layers.size());

            // Layer 0: y-offset=0, height=5 (blocks minY+0 to minY+4)
            MsfRegion r0 = layers.get(0).regions().get(0);
            ctx.assertTrue(r0.sizeY() == 5,
                "Layer 0 must have height 5; got " + r0.sizeY());
            ctx.assertTrue(r0.originY() == 0,
                "Layer 0 must have originY=0; got " + r0.originY());

            // Layer 1: y-offset=5, height=5 (blocks minY+5 to minY+9)
            MsfRegion r1 = layers.get(1).regions().get(0);
            ctx.assertTrue(r1.sizeY() == 5,
                "Layer 1 must have height 5; got " + r1.sizeY());
            ctx.assertTrue(r1.originY() == 5,
                "Layer 1 must have originY=5; got " + r1.originY());
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }

    // =========================================================================
    // 15. --layers 3 on a 7-block-tall region: heights 3, 3, 1
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractWithLayers3_sevenBlockTall_heightsThreeThreeOne(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();
        for (int y = 1; y <= 7; y++) {
            ctx.setBlockState(1, y, 1, Blocks.STONE.getDefaultState());
        }

        BlockPos from = ctx.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos to   = ctx.getAbsolutePos(new BlockPos(2, 7, 2));
        BlockBox bounds = BlockBox.create(from, to);

        Path output = Files.createTempFile("msf-layers3-", ".msf");
        try {
            int result = MsfCommands.executeExtract(
                world, bounds, CanonicalFacing.NORTH, false, 3, output, msg -> {}
            );
            ctx.assertTrue(result == 1, "executeExtract --layers 3 must return 1");

            byte[] bytes = Files.readAllBytes(output);
            MsfFile file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
            List<MsfLayer> layers = file.layerIndex().layers();

            ctx.assertTrue(layers.size() == 3, "Must produce 3 layers; got " + layers.size());

            int[] expectedHeights  = {3, 3, 1};
            int[] expectedOriginYs = {0, 3, 6};
            for (int i = 0; i < 3; i++) {
                MsfRegion r = layers.get(i).regions().get(0);
                ctx.assertTrue(r.sizeY() == expectedHeights[i],
                    "Layer " + i + " must have height " + expectedHeights[i] + "; got " + r.sizeY());
                ctx.assertTrue(r.originY() == expectedOriginYs[i],
                    "Layer " + i + " must have originY=" + expectedOriginYs[i] + "; got " + r.originY());
            }
        } finally {
            Files.deleteIfExists(output);
        }
        ctx.complete();
    }
}

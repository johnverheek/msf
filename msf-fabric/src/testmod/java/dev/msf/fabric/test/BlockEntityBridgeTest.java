package dev.msf.fabric.test;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.fabric.bridge.BlockEntityBridge;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Gametests for {@link BlockEntityBridge} — NBT extraction and application.
 */
public class BlockEntityBridgeTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fromBlockEntityCapturesType(TestContext ctx) {
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());

        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockEntity be = ctx.getWorld().getBlockEntity(worldPos);
        ctx.assertTrue(be != null, "Chest must have a block entity");

        MsfBlockEntity msfBe = BlockEntityBridge.fromBlockEntity(be, anchor);
        ctx.assertTrue("minecraft:chest".equals(msfBe.blockEntityType()),
            "Expected type minecraft:chest, got " + msfBe.blockEntityType());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fromBlockEntityRecordsRelativePosition(TestContext ctx) {
        ctx.setBlockState(2, 1, 3, Blocks.CHEST.getDefaultState());

        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(2, 1, 3));
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockEntity be = ctx.getWorld().getBlockEntity(worldPos);
        ctx.assertTrue(be != null, "Chest must have a block entity");

        MsfBlockEntity msfBe = BlockEntityBridge.fromBlockEntity(be, anchor);
        ctx.assertEquals(2, msfBe.positionX(), "positionX");
        ctx.assertEquals(1, msfBe.positionY(), "positionY");
        ctx.assertEquals(3, msfBe.positionZ(), "positionZ");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void applyToWorldSilentlySkipsNullBlockEntity(TestContext ctx) throws MsfParseException {
        // Position has no block entity placed — applyToWorld must not throw
        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        MsfBlockEntity msfBe = MsfBlockEntity.builder()
            .position(1, 1, 1)
            .blockEntityType("minecraft:chest")
            .nbtPayload(new byte[0])
            .build();

        // No block entity exists at this position — must silently return
        BlockEntityBridge.applyToWorld(msfBe, ctx.getWorld(), worldPos);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void applyEmptyPayloadIsNoOp(TestContext ctx) throws MsfParseException {
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());

        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        MsfBlockEntity msfBe = MsfBlockEntity.builder()
            .position(1, 1, 1)
            .blockEntityType("minecraft:chest")
            .nbtPayload(new byte[0])
            .build();

        // Empty payload must not throw
        BlockEntityBridge.applyToWorld(msfBe, ctx.getWorld(), worldPos);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractAndApplyRoundTrip(TestContext ctx) throws MsfParseException {
        // Place source chest
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());
        // Place target chest to receive NBT
        ctx.setBlockState(3, 1, 1, Blocks.CHEST.getDefaultState());

        BlockPos srcWorld    = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos targetWorld = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
        BlockPos anchor      = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockEntity srcBe = ctx.getWorld().getBlockEntity(srcWorld);
        ctx.assertTrue(srcBe != null, "Source chest must have a block entity");

        MsfBlockEntity msfBe = BlockEntityBridge.fromBlockEntity(srcBe, anchor);

        // Re-build record with target position
        MsfBlockEntity targetRecord = MsfBlockEntity.builder()
            .position(3, 1, 1)
            .blockEntityType(msfBe.blockEntityType())
            .nbtPayload(msfBe.nbtPayload())
            .build();

        BlockEntityBridge.applyToWorld(targetRecord, ctx.getWorld(), targetWorld);

        // Target chest block entity must still exist after NBT application
        ctx.assertTrue(ctx.getWorld().getBlockEntity(targetWorld) != null,
            "Target block entity must still exist after applyToWorld");
        ctx.complete();
    }

    // =========================================================================
    // NBT field verification tests
    // =========================================================================

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractOmitsIdXYZFromPayload(TestContext ctx) throws IOException {
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());

        BlockPos worldPos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockEntity be = ctx.getWorld().getBlockEntity(worldPos);
        ctx.assertTrue(be != null, "Chest must have a block entity");

        MsfBlockEntity msfBe = BlockEntityBridge.fromBlockEntity(be, anchor);
        byte[] payload = msfBe.nbtPayload();
        ctx.assertTrue(payload != null && payload.length > 0, "Payload must not be empty");

        NbtCompound nbt;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            nbt = NbtIo.readCompound(dis, NbtSizeTracker.of(4 * 1024 * 1024L));
        }
        ctx.assertTrue(!nbt.contains("id"), "'id' must not appear in extracted NBT payload (Section 9.2)");
        ctx.assertTrue(!nbt.contains("x"),  "'x' must not appear in extracted NBT payload (Section 9.2)");
        ctx.assertTrue(!nbt.contains("y"),  "'y' must not appear in extracted NBT payload (Section 9.2)");
        ctx.assertTrue(!nbt.contains("z"),  "'z' must not appear in extracted NBT payload (Section 9.2)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chestInventoryRoundTrip(TestContext ctx) throws MsfParseException {
        // Place source chest at (1,1,1) and target chest at (3,1,1)
        ctx.setBlockState(1, 1, 1, Blocks.CHEST.getDefaultState());
        ctx.setBlockState(3, 1, 1, Blocks.CHEST.getDefaultState());

        BlockPos srcWorld    = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos targetWorld = ctx.getAbsolutePos(new BlockPos(3, 1, 1));
        BlockPos anchor      = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // Insert a diamond at slot 0 in the source chest
        ChestBlockEntity srcBe = (ChestBlockEntity) ctx.getWorld().getBlockEntity(srcWorld);
        ctx.assertTrue(srcBe != null, "Source chest must have a block entity");
        srcBe.setStack(0, new ItemStack(Items.DIAMOND));

        // Extract source, rebuild at target position, apply
        MsfBlockEntity msfBe = BlockEntityBridge.fromBlockEntity(srcBe, anchor);
        MsfBlockEntity targetRecord = MsfBlockEntity.builder()
            .position(3, 1, 1)
            .blockEntityType(msfBe.blockEntityType())
            .nbtPayload(msfBe.nbtPayload())
            .build();
        BlockEntityBridge.applyToWorld(targetRecord, ctx.getWorld(), targetWorld);

        // Verify the diamond was transferred to slot 0 of the target chest
        ChestBlockEntity targetBe = (ChestBlockEntity) ctx.getWorld().getBlockEntity(targetWorld);
        ctx.assertTrue(targetBe != null, "Target chest block entity must exist");
        ctx.assertTrue(targetBe.getStack(0).isOf(Items.DIAMOND),
            "Diamond must be in slot 0 of target chest after NBT application, got: "
            + targetBe.getStack(0));
        ctx.complete();
    }
}

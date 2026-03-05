package dev.msf.fabric.test;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfEntity;
import dev.msf.fabric.bridge.EntityBridge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Gametests for {@link EntityBridge} — NBT extraction and entity reconstruction.
 */
public class EntityBridgeTest implements FabricGameTest {

    /** Spawns an armor stand at the given world position and returns it. */
    private static ArmorStandEntity spawnArmorStand(ServerWorld world, double x, double y, double z) {
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        if (stand == null) throw new AssertionError("EntityType.ARMOR_STAND.create() returned null");
        stand.setPosition(x, y, z);
        world.spawnEntity(stand);
        return stand;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fromEntityCapturesType(TestContext ctx) {
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos spawnPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));

        ArmorStandEntity stand = spawnArmorStand(
            ctx.getWorld(), spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5
        );

        MsfEntity msfEntity = EntityBridge.fromEntity(stand, anchor);
        ctx.assertTrue("minecraft:armor_stand".equals(msfEntity.entityType()),
            "Expected entityType minecraft:armor_stand, got " + msfEntity.entityType());
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fromEntityRecordsRelativePosition(TestContext ctx) {
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos spawnPos = ctx.getAbsolutePos(new BlockPos(3, 1, 2));

        double srcX = spawnPos.getX() + 0.5;
        double srcY = spawnPos.getY();
        double srcZ = spawnPos.getZ() + 0.5;

        ArmorStandEntity stand = spawnArmorStand(ctx.getWorld(), srcX, srcY, srcZ);

        MsfEntity msfEntity = EntityBridge.fromEntity(stand, anchor);

        double expectedX = srcX - anchor.getX();
        double expectedY = srcY - anchor.getY();
        double expectedZ = srcZ - anchor.getZ();

        ctx.assertTrue(Math.abs(msfEntity.positionX() - expectedX) < 0.001,
            "positionX mismatch: expected " + expectedX + ", got " + msfEntity.positionX());
        ctx.assertTrue(Math.abs(msfEntity.positionY() - expectedY) < 0.001,
            "positionY mismatch: expected " + expectedY + ", got " + msfEntity.positionY());
        ctx.assertTrue(Math.abs(msfEntity.positionZ() - expectedZ) < 0.001,
            "positionZ mismatch: expected " + expectedZ + ", got " + msfEntity.positionZ());
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void toEntityReconstructsTypeAndPosition(TestContext ctx) throws MsfParseException {
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos spawnPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));

        double srcX = spawnPos.getX() + 0.5;
        double srcY = spawnPos.getY();
        double srcZ = spawnPos.getZ() + 0.5;

        ServerWorld world = ctx.getWorld();
        ArmorStandEntity stand = spawnArmorStand(world, srcX, srcY, srcZ);

        MsfEntity msfEntity = EntityBridge.fromEntity(stand, anchor);
        Entity rebuilt = EntityBridge.toEntity(msfEntity, world, anchor);

        ctx.assertTrue(rebuilt.getType() == EntityType.ARMOR_STAND,
            "Rebuilt entity must be armor_stand, got: "
            + Registries.ENTITY_TYPE.getId(rebuilt.getType()));
        ctx.assertTrue(Math.abs(rebuilt.getX() - srcX) < 0.001,
            "Rebuilt X mismatch: expected " + srcX + ", got " + rebuilt.getX());
        ctx.assertTrue(Math.abs(rebuilt.getY() - srcY) < 0.001,
            "Rebuilt Y mismatch: expected " + srcY + ", got " + rebuilt.getY());
        ctx.assertTrue(Math.abs(rebuilt.getZ() - srcZ) < 0.001,
            "Rebuilt Z mismatch: expected " + srcZ + ", got " + rebuilt.getZ());
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void idTagAbsentFromPayload(TestContext ctx) throws IOException {
        BlockPos anchor   = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos spawnPos = ctx.getAbsolutePos(new BlockPos(2, 1, 2));

        ArmorStandEntity stand = spawnArmorStand(
            ctx.getWorld(), spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5
        );

        MsfEntity msfEntity = EntityBridge.fromEntity(stand, anchor);
        byte[] payload = msfEntity.nbtPayload();
        ctx.assertTrue(payload != null && payload.length > 0,
            "Payload must not be empty for armor stand");

        NbtCompound nbt;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            nbt = NbtIo.readCompound(dis, NbtSizeTracker.of(4 * 1024 * 1024L));
        }
        ctx.assertTrue(!nbt.contains("id"),
            "'id' must not appear in entity NBT payload (Section 8.2)");
        ctx.assertTrue(!nbt.contains("UUID"),
            "'UUID' must not appear in entity NBT payload after UUID stripping");
        ctx.complete();
    }
}

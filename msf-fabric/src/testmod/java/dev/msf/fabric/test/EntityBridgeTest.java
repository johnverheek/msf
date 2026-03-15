package dev.msf.fabric.test;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfEntity;
import dev.msf.fabric.bridge.EntityBridge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
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
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, SpawnReason.LOAD);
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

    /**
     * Verifies that entity position is anchor-relative: extracting at one anchor and
     * placing at a DIFFERENT anchor must produce the entity at the placement anchor +
     * relative offset, NOT at the original extraction world position.
     *
     * <p>This is the regression test for the bug where entities were spawned at the
     * original extraction location regardless of the placement anchor.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void toEntityUsesPlacementAnchorNotExtractionAnchor(TestContext ctx) throws MsfParseException {
        ServerWorld world = ctx.getWorld();

        // Extraction: entity is 2.5 blocks east and 1 block above the extraction anchor
        BlockPos extractionAnchor = ctx.getAbsolutePos(BlockPos.ORIGIN);
        double entityWorldX = extractionAnchor.getX() + 2.5;
        double entityWorldY = extractionAnchor.getY() + 1.0;
        double entityWorldZ = extractionAnchor.getZ() + 0.5;

        ArmorStandEntity stand = spawnArmorStand(world, entityWorldX, entityWorldY, entityWorldZ);
        MsfEntity msfEntity = EntityBridge.fromEntity(stand, extractionAnchor);

        // Verify the relative offsets stored in the MsfEntity
        double relX = entityWorldX - extractionAnchor.getX(); // 2.5
        double relY = entityWorldY - extractionAnchor.getY(); // 1.0
        double relZ = entityWorldZ - extractionAnchor.getZ(); // 0.5
        ctx.assertTrue(Math.abs(msfEntity.positionX() - relX) < 0.001,
            "relX mismatch during extraction: expected " + relX + ", got " + msfEntity.positionX());

        // Placement: use a completely different anchor — entity must appear at
        // placementAnchor + relative offset, NOT at the original extraction world position.
        BlockPos placementAnchor = ctx.getAbsolutePos(new BlockPos(5, 0, 5));
        Entity rebuilt = EntityBridge.toEntity(msfEntity, world, placementAnchor);

        double expectedX = placementAnchor.getX() + relX;
        double expectedY = placementAnchor.getY() + relY;
        double expectedZ = placementAnchor.getZ() + relZ;

        ctx.assertTrue(Math.abs(rebuilt.getX() - expectedX) < 0.001,
            "Placed entity X should be placementAnchor+relX=" + expectedX
            + " (not extraction pos " + entityWorldX + "), got " + rebuilt.getX());
        ctx.assertTrue(Math.abs(rebuilt.getY() - expectedY) < 0.001,
            "Placed entity Y should be placementAnchor+relY=" + expectedY
            + " (not extraction pos " + entityWorldY + "), got " + rebuilt.getY());
        ctx.assertTrue(Math.abs(rebuilt.getZ() - expectedZ) < 0.001,
            "Placed entity Z should be placementAnchor+relZ=" + expectedZ
            + " (not extraction pos " + entityWorldZ + "), got " + rebuilt.getZ());
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
        ctx.assertTrue(!nbt.contains("Pos"),
            "'Pos' must not appear in entity NBT payload (position is in typed f64 fields)");
        ctx.assertTrue(!nbt.contains("Rotation"),
            "'Rotation' must not appear in entity NBT payload (rotation is in typed f32 fields)");
        ctx.complete();
    }
}

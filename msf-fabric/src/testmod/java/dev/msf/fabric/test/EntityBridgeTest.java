package dev.msf.fabric.test;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfEntity;
import dev.msf.fabric.bridge.EntityBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Gametests for {@link EntityBridge} — NBT extraction and entity reconstruction.
 */
public class EntityBridgeTest {

    /** Spawns an armor stand at the given world position and returns it. */
    private static ArmorStandEntity spawnArmorStand(ServerWorld world, double x, double y, double z) {
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, SpawnReason.LOAD);
        if (stand == null) throw new AssertionError("EntityType.ARMOR_STAND.create() returned null");
        stand.setPosition(x, y, z);
        world.spawnEntity(stand);
        return stand;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void toEntityUsesPlacementAnchorNotExtractionAnchor(TestContext ctx) throws MsfParseException {
        ServerWorld world = ctx.getWorld();

        // Extraction anchor is at a NON-ORIGIN offset so that (entity - anchor) is a
        // genuine subtraction with a non-trivial anchor value. If anchor were (0,0,0)
        // the relative position would equal the entity's absolute world position and
        // the test would not distinguish correct from incorrect anchor-relative math.
        BlockPos extractionAnchor = ctx.getAbsolutePos(new BlockPos(2, 0, 2));

        // Entity is 3.5 blocks east and 1 block above the extraction anchor in world space.
        double entityWorldX = extractionAnchor.getX() + 3.5;
        double entityWorldY = extractionAnchor.getY() + 1.0;
        double entityWorldZ = extractionAnchor.getZ() + 0.5;

        ArmorStandEntity stand = spawnArmorStand(world, entityWorldX, entityWorldY, entityWorldZ);
        MsfEntity msfEntity = EntityBridge.fromEntity(stand, extractionAnchor);

        // Relative offsets must be (3.5, 1.0, 0.5) — NOT the entity's absolute world coords.
        double relX = 3.5;
        double relY = 1.0;
        double relZ = 0.5;
        ctx.assertTrue(Math.abs(msfEntity.positionX() - relX) < 0.001,
            "relX mismatch: expected " + relX + " got " + msfEntity.positionX()
            + " (extractionAnchor.X=" + extractionAnchor.getX() + ", entityWorld.X=" + entityWorldX + ")");
        ctx.assertTrue(Math.abs(msfEntity.positionY() - relY) < 0.001,
            "relY mismatch: expected " + relY + " got " + msfEntity.positionY());
        ctx.assertTrue(Math.abs(msfEntity.positionZ() - relZ) < 0.001,
            "relZ mismatch: expected " + relZ + " got " + msfEntity.positionZ());

        // Placement anchor is far from both the test origin and the extraction anchor.
        // Entity must appear at placementAnchor + (3.5, 1.0, 0.5), NOT near the original
        // extraction world position (entityWorldX, entityWorldY, entityWorldZ).
        BlockPos placementAnchor = ctx.getAbsolutePos(new BlockPos(15, 0, 15));
        Entity rebuilt = EntityBridge.toEntity(msfEntity, world, placementAnchor);

        double expectedX = placementAnchor.getX() + relX;
        double expectedY = placementAnchor.getY() + relY;
        double expectedZ = placementAnchor.getZ() + relZ;

        ctx.assertTrue(Math.abs(rebuilt.getX() - expectedX) < 0.001,
            "Placed entity X: expected placementAnchor.X+" + relX + "=" + expectedX
            + " but got " + rebuilt.getX()
            + " (original extraction world X was " + entityWorldX + ")");
        ctx.assertTrue(Math.abs(rebuilt.getY() - expectedY) < 0.001,
            "Placed entity Y: expected " + expectedY + " but got " + rebuilt.getY());
        ctx.assertTrue(Math.abs(rebuilt.getZ() - expectedZ) < 0.001,
            "Placed entity Z: expected placementAnchor.Z+" + relZ + "=" + expectedZ
            + " but got " + rebuilt.getZ()
            + " (original extraction world Z was " + entityWorldZ + ")");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

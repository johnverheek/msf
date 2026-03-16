package dev.msf.fabric.bridge;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.util.UuidStripper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Converts between {@link MsfEntity} records and Minecraft {@link Entity} objects.
 *
 * <h2>UUID stripping (Section 8.2)</h2>
 * On extraction, the entity NBT is serialized to bytes and passed through
 * {@link UuidStripper#strip(byte[])} before storing in the {@link MsfEntity}
 * payload. The {@code id} tag is also removed — it MUST NOT appear in stored payloads.
 *
 * <h2>Placement</h2>
 * {@link #toEntity} constructs an entity in the given world but does NOT spawn it.
 * Spawning is {@link dev.msf.fabric.world.RegionPlacer}'s responsibility.
 * A fresh UUID is assigned on every reconstruction.
 */
public final class EntityBridge {

    /** Maximum NBT payload size accepted during reconstruction (64 MB). */
    private static final long MAX_NBT_READ_BYTES = 67_108_864L;

    private EntityBridge() {}

    // =========================================================================
    // fromEntity
    // =========================================================================

    /**
     * Extracts an {@link MsfEntity} from a live Minecraft {@link Entity}.
     *
     * <p>Position is recorded relative to {@code anchorPos}. UUID tags are stripped
     * from the NBT payload via {@link UuidStripper}. The {@code id} tag, if present,
     * is removed before serialization.
     *
     * @param entity    the source entity (must not be null)
     * @param anchorPos the schematic anchor in world coordinates
     * @return the extracted {@link MsfEntity}
     * @throws IllegalArgumentException if the serialized NBT exceeds 65535 bytes
     *                                  after UUID stripping
     */
    public static MsfEntity fromEntity(Entity entity, BlockPos anchorPos) {
        // Section 8.2 — serialize entity data using WriteView (MC 1.21.11 storage API)
        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY);
        entity.writeData(writeView);
        NbtCompound nbt = writeView.getNbt();
        // Section 8.2 — id tag MUST NOT appear in stored payload (type is in the typed field)
        nbt.remove("id");
        // Section 8.1 — position and rotation are stored in the typed f64/f32 fields, not the
        // NBT payload. Strip "Pos" and "Rotation" so that readData() in toEntity() cannot
        // restore the original extraction-time world coordinates and override setPosition().
        nbt.remove("Pos");
        nbt.remove("Rotation");

        byte[] rawBytes = nbtToBytes(nbt);
        // Section 8.2 — strip UUID tags via UuidStripper
        byte[] strippedBytes = UuidStripper.strip(rawBytes);

        String entityType = Registries.ENTITY_TYPE.getId(entity.getType()).toString();

        double relX = entity.getX() - anchorPos.getX();
        double relY = entity.getY() - anchorPos.getY();
        double relZ = entity.getZ() - anchorPos.getZ();

        return MsfEntity.builder()
            .position(relX, relY, relZ)
            .rotation(entity.getYaw(), entity.getPitch())
            .entityType(entityType)
            .nbtPayload(strippedBytes)
            .build();
    }

    // =========================================================================
    // toEntity
    // =========================================================================

    /**
     * Reconstructs a Minecraft {@link Entity} from an {@link MsfEntity}.
     *
     * <p>The entity is created and configured but NOT spawned into the world.
     * A fresh {@link UUID} is assigned. Position is computed as
     * {@code anchorPos + msfEntity.position()}.
     *
     * @param msfEntity the source MSF entity
     * @param world     the target world (used for entity type construction)
     * @param anchorPos the schematic anchor in world coordinates
     * @return the constructed entity, ready to be spawned
     * @throws MsfParseException if the entity type identifier is unknown or if
     *                           the entity cannot be instantiated
     */
    public static Entity toEntity(
        MsfEntity msfEntity, ServerWorld world, BlockPos anchorPos
    ) throws MsfParseException {
        Identifier typeId = Identifier.tryParse(msfEntity.entityType());
        if (typeId == null || !Registries.ENTITY_TYPE.containsId(typeId)) {
            throw new MsfParseException("Unknown entity type: '" + msfEntity.entityType() + "'");
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(typeId);

        Entity entity = entityType.create(world, SpawnReason.LOAD);
        if (entity == null) {
            throw new MsfParseException(
                "EntityType.create() returned null for type: '" + msfEntity.entityType() + "'"
            );
        }

        byte[] payload = msfEntity.nbtPayload();
        if (payload != null && payload.length > 0) {
            try {
                NbtCompound nbt = nbtFromBytes(payload);
                entity.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt));
            } catch (IOException e) {
                throw new MsfParseException(
                    "Failed to deserialize NBT for entity type '" + msfEntity.entityType() + "'", e
                );
            }
        }

        double worldX = anchorPos.getX() + msfEntity.positionX();
        double worldY = anchorPos.getY() + msfEntity.positionY();
        double worldZ = anchorPos.getZ() + msfEntity.positionZ();

        entity.setPosition(worldX, worldY, worldZ);
        entity.setYaw(msfEntity.yaw());
        entity.setPitch(msfEntity.pitch());
        // Section 8.2 — assign a fresh UUID; never reuse the stored UUID
        entity.setUuid(UUID.randomUUID());

        return entity;
    }

    // =========================================================================
    // NBT I/O helpers
    // =========================================================================

    private static byte[] nbtToBytes(NbtCompound nbt) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            NbtIo.writeCompound(nbt, dos);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream threw unexpectedly", e);
        }
        return baos.toByteArray();
    }

    private static NbtCompound nbtFromBytes(byte[] bytes) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.readCompound(dis, NbtSizeTracker.of(MAX_NBT_READ_BYTES));
        }
    }
}

package dev.msf.fabric.bridge;

import dev.msf.core.MsfParseException;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.util.UuidStripper;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Converts between {@link MsfBlockEntity} records and Minecraft {@link BlockEntity} objects.
 *
 * <h2>Payload normalization (Section 9.2)</h2>
 * On extraction, {@link BlockEntity#createNbt(RegistryWrapper.WrapperLookup)} is called to
 * produce the full NBT compound. The tags {@code id}, {@code x}, {@code y}, and {@code z}
 * are removed (captured in typed fields), then UUID tags are stripped via
 * {@link UuidStripper}. The remaining bytes are stored as the payload.
 *
 * <h2>Placement precondition</h2>
 * {@link #applyToWorld} requires the block at {@code worldPos} to already be placed and to
 * be a block entity type — {@link ServerWorld#getBlockEntity(BlockPos)} must return non-null.
 * {@link dev.msf.fabric.world.RegionPlacer} satisfies this by placing all block data before
 * iterating block entities.
 */
public final class BlockEntityBridge {

    /** Maximum NBT payload size accepted during reconstruction (64 MB). */
    private static final long MAX_NBT_READ_BYTES = 67_108_864L;

    private BlockEntityBridge() {}

    // =========================================================================
    // fromBlockEntity
    // =========================================================================

    /**
     * Extracts an {@link MsfBlockEntity} from a live Minecraft {@link BlockEntity}.
     *
     * <p>Position is recorded relative to {@code anchorPos}. The tags {@code id},
     * {@code x}, {@code y}, {@code z} are removed from the payload (they are captured
     * in typed fields). UUID tags are stripped via {@link UuidStripper} (Section 9.2).
     *
     * @param blockEntity the source block entity; must be placed in a world
     * @param anchorPos   the schematic anchor in world coordinates
     * @return the extracted {@link MsfBlockEntity}
     * @throws IllegalArgumentException if the serialized NBT exceeds 65535 bytes
     *                                  after stripping
     */
    public static MsfBlockEntity fromBlockEntity(BlockEntity blockEntity, BlockPos anchorPos) {
        RegistryWrapper.WrapperLookup registries = blockEntity.getWorld().getRegistryManager();

        // createNbt includes id, x, y, z plus all custom data (MC 1.21.1 API)
        NbtCompound nbt = blockEntity.createNbt(registries);
        // Section 9.2 — id, x, y, z MUST NOT appear in stored payload
        nbt.remove("id");
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");

        byte[] rawBytes = nbtToBytes(nbt);
        // Section 9.2 — strip UUID tags
        byte[] strippedBytes = UuidStripper.strip(rawBytes);

        String beType = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString();

        BlockPos pos = blockEntity.getPos();
        int relX = pos.getX() - anchorPos.getX();
        int relY = pos.getY() - anchorPos.getY();
        int relZ = pos.getZ() - anchorPos.getZ();

        return MsfBlockEntity.builder()
            .position(relX, relY, relZ)
            .blockEntityType(beType)
            .nbtPayload(strippedBytes)
            .build();
    }

    // =========================================================================
    // applyToWorld
    // =========================================================================

    /**
     * Loads the stored NBT into the existing block entity at {@code worldPos}.
     *
     * <p><b>Precondition:</b> a block must already be placed at {@code worldPos} and it
     * must be a block entity type. If no block entity exists at that position, this method
     * silently returns — the block was likely placed without block entity support (e.g.
     * data version mismatch) and the payload cannot be applied.
     *
     * <p>{@link BlockEntity#read(NbtCompound, RegistryWrapper.WrapperLookup)} is used to
     * load the custom NBT data. The {@code id}, {@code x}, {@code y}, {@code z} tags are
     * NOT re-added — {@code read()} only processes custom block entity data and does not
     * require positional tags.
     *
     * @param msfBe    the MSF block entity record
     * @param world    the target world
     * @param worldPos the world position of the block entity (after rotation is applied by
     *                 the caller)
     * @throws MsfParseException if the stored NBT bytes cannot be deserialized
     */
    public static void applyToWorld(
        MsfBlockEntity msfBe,
        ServerWorld world,
        BlockPos worldPos
    ) throws MsfParseException {
        BlockEntity be = world.getBlockEntity(worldPos);
        if (be == null) {
            // Block was placed but has no block entity — data version mismatch or air was placed.
            // Documented precondition: silently skip rather than throw.
            return;
        }

        byte[] payload = msfBe.nbtPayload();
        if (payload == null || payload.length == 0) {
            return; // Nothing to apply
        }

        try {
            NbtCompound nbt = nbtFromBytes(payload);
            be.read(nbt, world.getRegistryManager());
            // Mark chunk dirty so the data is persisted
            be.markDirty();
        } catch (IOException e) {
            throw new MsfParseException(
                "Failed to deserialize NBT for block entity type '"
                + msfBe.blockEntityType() + "' at " + worldPos, e
            );
        }
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
            return NbtIo.readCompound(dis, NbtSizeTracker.ofBytes(MAX_NBT_READ_BYTES));
        }
    }
}

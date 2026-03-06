package dev.msf.core;

import dev.msf.core.checksum.XxHash3;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for entity block and block entity block round trips, UUID stripping,
 * and constraint validation.
 */
class EntityBlockTest {

    // =========================================================================
    // File assembly helpers
    // =========================================================================

    private static MsfFile fileWithEntity(MsfEntity entity) throws Exception {
        return MsfFile.builder()
            .metadata(MsfMetadata.builder().name("Test").build())
            .palette(MsfPalette.of(List.of("minecraft:stone")))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("Main")
                    .addRegion(MsfRegion.builder().origin(0, 0, 0).size(1, 1, 1).build())
                    .build()
            )))
            .entities(List.of(entity))
            .build();
    }

    private static MsfFile fileWithBlockEntity(MsfBlockEntity be) throws Exception {
        return MsfFile.builder()
            .metadata(MsfMetadata.builder().name("Test").build())
            .palette(MsfPalette.of(List.of("minecraft:stone")))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("Main")
                    .addRegion(MsfRegion.builder().origin(0, 0, 0).size(1, 1, 1).build())
                    .build()
            )))
            .blockEntities(List.of(be))
            .build();
    }

    // =========================================================================
    // NBT construction helpers
    // =========================================================================

    /**
     * Minimal valid TAG_Compound NBT with a single TAG_Byte named "Data".
     * Contains no UUID tags, so it passes through UuidStripper unchanged.
     */
    private static byte[] minimalNbt(byte value) {
        byte[] nameBytes = "Data".getBytes(StandardCharsets.UTF_8);
        // TAG_Compound root (3) + TAG_Byte header (3) + name + value (1) + TAG_End (1)
        byte[] nbt = new byte[3 + 3 + nameBytes.length + 2];
        int i = 0;
        nbt[i++] = 0x0A;                        // TAG_Compound
        nbt[i++] = 0x00;                        // root name length (BE u16)
        nbt[i++] = 0x00;
        nbt[i++] = 0x01;                        // TAG_Byte
        nbt[i++] = 0x00;                        // name length (BE u16)
        nbt[i++] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, nbt, i, nameBytes.length);
        i += nameBytes.length;
        nbt[i++] = value;
        nbt[i] = 0x00;                          // TAG_End
        return nbt;
    }

    /**
     * TAG_Compound NBT containing a "UUID" TAG_Int_Array (Minecraft 1.16+ format).
     * The writer must strip this before encoding.
     */
    private static byte[] nbtWithUuidIntArray() {
        byte[] uuidName = "UUID".getBytes(StandardCharsets.UTF_8);
        // 3 (root) + 1 (type) + 2 (name len) + 4 (name) + 4 (array count) + 16 (4 ints) + 1 (end)
        ByteBuffer b = ByteBuffer.allocate(3 + 1 + 2 + uuidName.length + 4 + 16 + 1)
            .order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 0x0A);                     // TAG_Compound root
        b.putShort((short) 0);                  // empty root name
        b.put((byte) 0x0B);                     // TAG_Int_Array
        b.putShort((short) uuidName.length);
        b.put(uuidName);
        b.putInt(4);                            // 4 ints
        b.putInt(0xA1B2C3D4); b.putInt(0xE5F6A7B8); b.putInt(0xC9D0E1F2); b.putInt(0x03040506);
        b.put((byte) 0x00);                     // TAG_End
        return b.array();
    }

    /**
     * Recomputes the file checksum (last 8 bytes) after patching, returning a new array.
     * The file checksum is XxHash3-64 of all content bytes except the final 8 bytes.
     */
    private static byte[] recomputeFileChecksum(byte[] bytes) {
        long checksum = XxHash3.hash(bytes, 0, bytes.length - 8);
        byte[] result = bytes.clone();
        ByteBuffer csb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csb.putLong(checksum);
        System.arraycopy(csb.array(), 0, result, result.length - 8, 8);
        return result;
    }

    // =========================================================================
    // Test 1: entity block round trip
    // =========================================================================

    @Test
    void entityBlock_roundTrip() throws Exception {
        MsfEntity original = MsfEntity.builder()
            .position(10.5, 64.0, -30.25)
            .rotation(90.0f, -15.5f)
            .entityType("minecraft:armor_stand")
            .nbtPayload(minimalNbt((byte) 1))
            .build();

        byte[] fileBytes = MsfWriter.writeFile(fileWithEntity(original), null);
        MsfFile read = MsfReader.readFile(fileBytes, MsfReaderConfig.DEFAULT, null);

        assertTrue(read.entities().isPresent());
        assertEquals(1, read.entities().get().size());
        MsfEntity result = read.entities().get().get(0);
        assertEquals("minecraft:armor_stand", result.entityType());
        assertEquals(10.5,   result.positionX(), 1e-10);
        assertEquals(64.0,   result.positionY(), 1e-10);
        assertEquals(-30.25, result.positionZ(), 1e-10);
        assertEquals(90.0f,  result.yaw(),   1e-6f);
        assertEquals(-15.5f, result.pitch(), 1e-6f);
    }

    // =========================================================================
    // Test 2: block entity block round trip
    // =========================================================================

    @Test
    void blockEntityBlock_roundTrip() throws Exception {
        MsfBlockEntity original = MsfBlockEntity.builder()
            .position(5, -60, 12)
            .blockEntityType("minecraft:chest")
            .nbtPayload(minimalNbt((byte) 7))
            .build();

        byte[] fileBytes = MsfWriter.writeFile(fileWithBlockEntity(original), null);
        MsfFile read = MsfReader.readFile(fileBytes, MsfReaderConfig.DEFAULT, null);

        assertTrue(read.blockEntities().isPresent());
        assertEquals(1, read.blockEntities().get().size());
        MsfBlockEntity result = read.blockEntities().get().get(0);
        assertEquals("minecraft:chest", result.blockEntityType());
        assertEquals(5,   result.positionX());
        assertEquals(-60, result.positionY());
        assertEquals(12,  result.positionZ());
    }

    // =========================================================================
    // Test 3: UUID absent from NBT payload after write (Section 8.2)
    // =========================================================================

    @Test
    void uuidAbsentFromNbt_afterWrite() throws Exception {
        MsfEntity entity = MsfEntity.builder()
            .position(0.0, 64.0, 0.0)
            .entityType("minecraft:zombie")
            .nbtPayload(nbtWithUuidIntArray())
            .build();

        byte[] fileBytes = MsfWriter.writeFile(fileWithEntity(entity), null);
        MsfFile read = MsfReader.readFile(fileBytes, MsfReaderConfig.DEFAULT, null);

        assertTrue(read.entities().isPresent());
        byte[] readNbt = read.entities().get().get(0).nbtPayload();

        // "UUID" tag name must not appear in the encoded/read-back NBT payload
        byte[] uuidName = "UUID".getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= readNbt.length - uuidName.length; i++) {
            for (int j = 0; j < uuidName.length; j++) {
                if (readNbt[i + j] != uuidName[j]) continue outer;
            }
            fail("UUID tag name found at offset " + i + " in NBT payload after write — UUID must be stripped");
        }
    }

    // =========================================================================
    // Test 4: zero entity count → FEATURE_FLAG_CONFLICT warning, block treated as absent
    // =========================================================================

    @Test
    void zeroEntityCount_emitsFeatureFlagConflict() throws Exception {
        // Write a valid file with one entity, then patch entity_count to 0.
        MsfEntity entity = MsfEntity.builder()
            .position(0.0, 64.0, 0.0)
            .entityType("minecraft:cow")
            .build();
        byte[] fileBytes = MsfWriter.writeFile(fileWithEntity(entity), null);

        // Read entity block offset from the written header (little-endian u32 at bytes 28–31).
        int entityOffset = (int) Integer.toUnsignedLong(
            ByteBuffer.wrap(fileBytes, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
        );

        // Patch entity_count (u32 at entityOffset + 4) to 0.
        byte[] patched = fileBytes.clone();
        patched[entityOffset + 4] = 0;
        patched[entityOffset + 5] = 0;
        patched[entityOffset + 6] = 0;
        patched[entityOffset + 7] = 0;
        patched = recomputeFileChecksum(patched);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfFile result = MsfReader.readFile(patched, MsfReaderConfig.DEFAULT, warnings::add);

        assertFalse(result.entities().isPresent(),
            "Entity block with count=0 must be treated as absent");
        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.FEATURE_FLAG_CONFLICT),
            "Expected FEATURE_FLAG_CONFLICT warning for zero entity count"
        );
    }

    // =========================================================================
    // Test 5: zero block entity count → FEATURE_FLAG_CONFLICT warning, block treated as absent
    // =========================================================================

    @Test
    void zeroBlockEntityCount_emitsFeatureFlagConflict() throws Exception {
        MsfBlockEntity be = MsfBlockEntity.builder()
            .position(0, 64, 0)
            .blockEntityType("minecraft:chest")
            .build();
        byte[] fileBytes = MsfWriter.writeFile(fileWithBlockEntity(be), null);

        // Block entity block offset is the little-endian u32 at bytes 32–35.
        int beOffset = (int) Integer.toUnsignedLong(
            ByteBuffer.wrap(fileBytes, 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
        );

        byte[] patched = fileBytes.clone();
        patched[beOffset + 4] = 0;
        patched[beOffset + 5] = 0;
        patched[beOffset + 6] = 0;
        patched[beOffset + 7] = 0;
        patched = recomputeFileChecksum(patched);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfFile result = MsfReader.readFile(patched, MsfReaderConfig.DEFAULT, warnings::add);

        assertFalse(result.blockEntities().isPresent(),
            "Block entity block with count=0 must be treated as absent");
        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.FEATURE_FLAG_CONFLICT),
            "Expected FEATURE_FLAG_CONFLICT warning for zero block entity count"
        );
    }

    // =========================================================================
    // Test 6: entity NBT payload > 65535 bytes → IllegalArgumentException
    // =========================================================================

    @Test
    void entityNbt_exceedsMax_throwsIllegalArgumentException() {
        byte[] oversized = new byte[MsfEntity.MAX_NBT_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () ->
            MsfEntity.builder()
                .entityType("minecraft:test")
                .nbtPayload(oversized)
                .build()
        );
    }

    // =========================================================================
    // Test 7: block entity NBT payload > 65535 bytes → IllegalArgumentException
    // =========================================================================

    @Test
    void blockEntityNbt_exceedsMax_throwsIllegalArgumentException() {
        byte[] oversized = new byte[MsfBlockEntity.MAX_NBT_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () ->
            MsfBlockEntity.builder()
                .blockEntityType("minecraft:test")
                .nbtPayload(oversized)
                .build()
        );
    }
}

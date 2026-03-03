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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full MSF file round-trip tests and forward-compatibility verification.
 *
 * <p>Tests the complete write → read cycle via {@link MsfWriter#writeFile} and
 * {@link MsfReader#readFile}, covering files with and without optional blocks, and
 * verifying that unknown bytes injected between known blocks are silently skipped.
 */
class MsfFileRoundTripTest {

    // =========================================================================
    // Shared test fixtures
    // =========================================================================

    private static final long TEST_MC_DATA_VERSION = 3953L;

    private static MsfMetadata testMetadata() throws Exception {
        return MsfMetadata.builder()
            .name("RoundTrip Test")
            .author("TestSuite")
            .build();
    }

    private static MsfPalette testPalette() throws Exception {
        // MsfPalette.of() prepends "minecraft:air" automatically → 3 entries total
        return MsfPalette.of(List.of("minecraft:stone", "minecraft:dirt"));
    }

    private static MsfLayerIndex testLayerIndex() {
        return MsfLayerIndex.of(List.of(
            MsfLayer.builder()
                .layerId(1)
                .name("Main")
                .addRegion(MsfRegion.builder()
                    .name("region1")
                    .origin(0, 0, 0)
                    .size(4, 4, 4)
                    .build())
                .build()
        ));
    }

    // =========================================================================
    // Test 1: full round trip with all optional blocks present
    // =========================================================================

    @Test
    void fullFile_roundTrip_withOptionalBlocks() throws Exception {
        MsfEntity entity = MsfEntity.builder()
            .position(1.0, 65.0, 1.0)
            .rotation(180.0f, 0.0f)
            .entityType("minecraft:villager")
            .build();

        MsfBlockEntity blockEntity = MsfBlockEntity.builder()
            .position(2, 64, 3)
            .blockEntityType("minecraft:furnace")
            .build();

        MsfFile original = MsfFile.builder()
            .mcDataVersion(TEST_MC_DATA_VERSION)
            .metadata(testMetadata())
            .palette(testPalette())
            .layerIndex(testLayerIndex())
            .entities(List.of(entity))
            .blockEntities(List.of(blockEntity))
            .build();

        byte[] fileBytes = MsfWriter.writeFile(original, null);
        MsfFile read = MsfReader.readFile(fileBytes, MsfReaderConfig.DEFAULT, null);

        // Header
        assertEquals(TEST_MC_DATA_VERSION, read.header().mcDataVersion());
        assertTrue(read.header().hasEntities());
        assertTrue(read.header().hasBlockEntities());

        // Metadata
        assertEquals("RoundTrip Test", read.metadata().name());
        assertEquals("TestSuite", read.metadata().author());

        // Palette: 3 entries (air + stone + dirt)
        assertEquals(3, read.palette().entries().size());
        assertTrue(read.palette().entries().contains("minecraft:stone"));
        assertTrue(read.palette().entries().contains("minecraft:dirt"));

        // Layer index
        assertEquals(1, read.layerIndex().layers().size());
        assertEquals("Main", read.layerIndex().layers().get(0).name());
        assertEquals(1, read.layerIndex().layers().get(0).regions().size());

        // Entity
        assertTrue(read.entities().isPresent());
        assertEquals(1, read.entities().get().size());
        MsfEntity re = read.entities().get().get(0);
        assertEquals("minecraft:villager", re.entityType());
        assertEquals(1.0,   re.positionX(), 1e-10);
        assertEquals(65.0,  re.positionY(), 1e-10);
        assertEquals(1.0,   re.positionZ(), 1e-10);
        assertEquals(180.0f, re.yaw(),   1e-6f);
        assertEquals(0.0f,   re.pitch(), 1e-6f);

        // Block entity
        assertTrue(read.blockEntities().isPresent());
        assertEquals(1, read.blockEntities().get().size());
        MsfBlockEntity rbe = read.blockEntities().get().get(0);
        assertEquals("minecraft:furnace", rbe.blockEntityType());
        assertEquals(2,  rbe.positionX());
        assertEquals(64, rbe.positionY());
        assertEquals(3,  rbe.positionZ());
    }

    // =========================================================================
    // Test 2: full round trip without optional blocks
    // =========================================================================

    @Test
    void fullFile_roundTrip_withoutOptionalBlocks() throws Exception {
        MsfFile original = MsfFile.builder()
            .mcDataVersion(TEST_MC_DATA_VERSION)
            .metadata(testMetadata())
            .palette(testPalette())
            .layerIndex(testLayerIndex())
            .build();

        byte[] fileBytes = MsfWriter.writeFile(original, null);
        MsfFile read = MsfReader.readFile(fileBytes, MsfReaderConfig.DEFAULT, null);

        assertEquals(TEST_MC_DATA_VERSION, read.header().mcDataVersion());
        assertFalse(read.header().hasEntities());
        assertFalse(read.header().hasBlockEntities());

        assertEquals("RoundTrip Test", read.metadata().name());
        assertEquals(3, read.palette().entries().size());
        assertEquals(1, read.layerIndex().layers().size());

        assertFalse(read.entities().isPresent());
        assertFalse(read.blockEntities().isPresent());
    }

    // =========================================================================
    // Test 3: forward compatibility — unknown bytes injected between known blocks are skipped
    // =========================================================================

    @Test
    void forwardCompat_unknownBlockBetweenKnownBlocks_isSkipped() throws Exception {
        // Write a base file without optional blocks.
        MsfFile original = MsfFile.builder()
            .mcDataVersion(TEST_MC_DATA_VERSION)
            .metadata(testMetadata())
            .palette(testPalette())
            .layerIndex(testLayerIndex())
            .build();

        byte[] fileBytes = MsfWriter.writeFile(original, null);

        // Parse offsets from the written header (little-endian u32).
        ByteBuffer hdr = ByteBuffer.wrap(fileBytes, 0, 48).order(ByteOrder.LITTLE_ENDIAN);
        int layerIndexOffset = (int) Integer.toUnsignedLong(hdr.getInt(24));

        // Inject 16 "junk" bytes immediately before the layer index block.
        // This simulates an unknown block that a future writer may insert between palette and layer index.
        byte[] junk = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                       0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10};
        int junkLen = junk.length;

        byte[] patched = new byte[fileBytes.length + junkLen];
        // Bytes 0 .. layerIndexOffset-1 (header + meta + palette) copied verbatim
        System.arraycopy(fileBytes, 0, patched, 0, layerIndexOffset);
        // Junk block at layerIndexOffset
        System.arraycopy(junk, 0, patched, layerIndexOffset, junkLen);
        // Layer index and everything after (including the old checksum placeholder) shifted right
        System.arraycopy(fileBytes, layerIndexOffset, patched, layerIndexOffset + junkLen,
            fileBytes.length - layerIndexOffset);

        // Update header fields that changed: layerIndexOffset and fileSize.
        // (No entity or block entity blocks, so only layerIndexOffset needs adjusting.)
        ByteBuffer patchHdr = ByteBuffer.wrap(patched, 0, 48).order(ByteOrder.LITTLE_ENDIAN);
        patchHdr.putInt(24, layerIndexOffset + junkLen);    // new layerIndexOffset
        patchHdr.putInt(36, patched.length);                // new fileSize

        // Recompute header checksum (Section 3.7): xxHash3-64 of patched bytes 0–39.
        long newHeaderChecksum = XxHash3.hash(patched, 0, 40);
        patchHdr.putLong(40, newHeaderChecksum);

        // Recompute file checksum (Section 11): xxHash3-64 of all content except last 8 bytes.
        long newFileChecksum = XxHash3.hash(patched, 0, patched.length - 8);
        ByteBuffer csBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csBuf.putLong(newFileChecksum);
        System.arraycopy(csBuf.array(), 0, patched, patched.length - 8, 8);

        // Reader must locate all blocks by absolute offset — the injected bytes between
        // palette and layer index are never accessed, so the read must succeed cleanly.
        MsfFile read = MsfReader.readFile(patched, MsfReaderConfig.DEFAULT, null);

        assertEquals("RoundTrip Test", read.metadata().name());
        assertEquals(3, read.palette().entries().size());
        assertEquals(1, read.layerIndex().layers().size(),
            "Layer index must be read correctly despite injected bytes before it");
        assertEquals("Main", read.layerIndex().layers().get(0).name());
        assertEquals(1, read.layerIndex().layers().get(0).regions().size());
        assertFalse(read.entities().isPresent());
        assertFalse(read.blockEntities().isPresent());
    }
}

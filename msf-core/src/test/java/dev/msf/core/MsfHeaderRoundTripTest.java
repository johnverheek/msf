package dev.msf.core;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Happy path round-trip tests: write a header, read it back, verify all fields survive intact.
 *
 * @see MsfSpec Section 3 — header
 */
class MsfHeaderRoundTripTest {

    @TempDir
    Path tempDir;

    /**
     * A minimal valid header fixture that can be used by multiple tests.
     * fileSize is 0 here, which causes MsfWriter to compute 56 (48 header + 8 checksum).
     */
    private static MsfHeader minimalHeader() {
        return new MsfHeader(
            1,      // majorVersion
            0,      // minorVersion
            0,      // featureFlags — no optional features
            3953L,  // mcDataVersion (Minecraft 1.21)
            48L,    // metadataOffset — immediately after header
            49L,    // globalPaletteOffset — stub offset
            50L,    // layerIndexOffset — stub offset
            0L,     // entityBlockOffset — absent
            0L,     // blockEntityBlockOffset — absent
            0L,     // fileSize — 0 tells writer to compute it
            0L      // headerChecksum — computed by writer
        );
    }

    @Test
    void roundTrip_allFieldsSurviveIntact() throws Exception {
        MsfHeader original = minimalHeader();

        byte[] fileBytes = MsfWriter.buildFileBytes(original, null);
        MsfHeader parsed = MsfReader.readHeaderFromBytes(fileBytes, MsfReaderConfig.DEFAULT, null);

        // Version fields
        assertEquals(1, parsed.majorVersion(),  "majorVersion");
        assertEquals(0, parsed.minorVersion(),  "minorVersion");
        assertEquals(0, parsed.featureFlags(),  "featureFlags");

        // MC data version
        assertEquals(3953L, parsed.mcDataVersion(), "mcDataVersion");

        // Offsets
        assertEquals(48L, parsed.metadataOffset(),         "metadataOffset");
        assertEquals(49L, parsed.globalPaletteOffset(),    "globalPaletteOffset");
        assertEquals(50L, parsed.layerIndexOffset(),       "layerIndexOffset");
        assertEquals(0L,  parsed.entityBlockOffset(),      "entityBlockOffset");
        assertEquals(0L,  parsed.blockEntityBlockOffset(), "blockEntityBlockOffset");

        // File size should be 56 (48-byte header + 8-byte file checksum)
        assertEquals(56L, parsed.fileSize(), "fileSize");
    }

    @Test
    void roundTrip_viaPath_producesIdenticalResult() throws Exception {
        MsfHeader original = minimalHeader();
        Path file = tempDir.resolve("test.msf");

        MsfWriter.writeHeader(file, original);
        MsfHeader parsed = MsfReader.readHeader(file);

        assertEquals(1,     parsed.majorVersion());
        assertEquals(0,     parsed.minorVersion());
        assertEquals(3953L, parsed.mcDataVersion());
        assertEquals(48L,   parsed.metadataOffset());
        assertEquals(56L,   parsed.fileSize());
    }

    @Test
    void roundTrip_maxMcDataVersion_u32Boundary() throws Exception {
        // mcDataVersion is a u32; test the maximum u32 value (4,294,967,295)
        MsfHeader header = new MsfHeader(
            1, 0, 0,
            0xFFFFFFFFL, // max u32
            48L, 49L, 50L,
            0L, 0L, 0L, 0L
        );

        byte[] bytes = MsfWriter.buildFileBytes(header, null);
        MsfHeader parsed = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null);
        assertEquals(0xFFFFFFFFL, parsed.mcDataVersion(), "max u32 mcDataVersion must survive");
    }

    @Test
    void roundTrip_withFeatureFlags_hasEntities() throws Exception {
        MsfHeader header = new MsfHeader(
            1, 0,
            MsfHeader.FeatureFlags.HAS_ENTITIES,
            3953L,
            48L, 49L, 50L,
            51L, // entityBlockOffset non-zero since flag is set
            0L,
            0L, 0L
        );

        List<MsfWarning> warnings = new ArrayList<>();
        byte[] bytes = MsfWriter.buildFileBytes(header, warnings::add);
        MsfHeader parsed = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, warnings::add);

        assertTrue(parsed.hasEntities(), "HAS_ENTITIES flag must survive round trip");
        assertEquals(51L, parsed.entityBlockOffset());
    }

    @Test
    void roundTrip_headerChecksumIsComputedCorrectly() throws Exception {
        // Verify that the header checksum field written by MsfWriter is the value
        // that MsfReader independently verifies and stores in the parsed record.
        MsfHeader original = minimalHeader();
        byte[] bytes = MsfWriter.buildFileBytes(original, null);
        MsfHeader parsed = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null);

        // The parsed header checksum should be non-zero (extremely unlikely to be 0)
        assertNotEquals(0L, parsed.headerChecksum(), "header checksum should not be zero");
    }

    @Test
    void roundTrip_fileSizeComputedAsHeaderPlusChecksum() throws Exception {
        // When fileSize is 0 in the input, the writer computes 48 + 8 = 56
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        assertEquals(56, bytes.length, "session-1 file should be exactly 56 bytes");

        MsfHeader parsed = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null);
        assertEquals(56L, parsed.fileSize(), "file_size field should equal 56");
    }
}

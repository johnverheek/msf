package dev.msf.core;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for header checksum corruption and file checksum corruption.
 *
 * @see MsfSpec Section 3.7 — header checksum
 * @see MsfSpec Section 11 — file checksum
 */
class MsfChecksumTest {

    private static MsfHeader minimalHeader() {
        return new MsfHeader(1, 0, 0, 3953L, 48L, 49L, 50L, 0L, 0L, 0L, 0L);
    }

    // -------------------------------------------------------------------------
    // Header checksum corruption
    // -------------------------------------------------------------------------

    @Test
    void headerChecksumCorruption_throwsMsfChecksumException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);

        // Corrupt one byte in the header checksum area (offset 40–47).
        // Flip a bit in the middle of the checksum field.
        bytes[40] ^= 0xFF;

        MsfChecksumException ex = assertThrows(
            MsfChecksumException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null)
        );

        assertEquals(MsfChecksumException.ChecksumType.HEADER, ex.getChecksumType());
        assertNotEquals(ex.getExpected(), ex.getActual(),
            "stored and computed checksums must differ when header is corrupted");
    }

    @Test
    void headerChecksumCorruption_stopsParsing_noPartialResult() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[44] ^= 0x01; // corrupt checksum, not a magic or version byte

        assertThrows(
            MsfChecksumException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null),
            "must throw before returning any partial result"
        );
    }

    @Test
    void headerDataCorruption_alsoFailsChecksumBecauseChecksumCoversData() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);

        // Corrupt a non-magic, non-checksum byte in the header (e.g., the MC data version)
        bytes[12] ^= 0x01;

        // Because the header checksum covers bytes 0–39, altering byte 12 invalidates the checksum.
        assertThrows(
            MsfChecksumException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null)
        );
    }

    // -------------------------------------------------------------------------
    // File checksum corruption
    // -------------------------------------------------------------------------

    @Test
    void fileChecksumCorruption_defaultConfig_throwsMsfChecksumException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);

        // Corrupt the final 8 bytes (file checksum).
        bytes[bytes.length - 1] ^= 0xFF;

        List<MsfWarning> warnings = new ArrayList<>();
        MsfChecksumException ex = assertThrows(
            MsfChecksumException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, warnings::add)
        );

        assertEquals(MsfChecksumException.ChecksumType.FILE, ex.getChecksumType());

        // The warning must also have been emitted before the exception
        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE),
            "FILE_CHECKSUM_FAILURE warning must be emitted before throwing"
        );
    }

    @Test
    void fileChecksumCorruption_allowChecksumFailure_returnsHeader() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[bytes.length - 1] ^= 0xFF; // corrupt file checksum

        List<MsfWarning> warnings = new ArrayList<>();
        MsfHeader parsed = MsfReader.readHeaderFromBytes(
            bytes,
            MsfReaderConfig.allowChecksumFailure(),
            warnings::add
        );

        // Should return a header even though the file checksum is wrong
        assertNotNull(parsed);
        assertEquals(1, parsed.majorVersion());

        // Warning must still be emitted
        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE),
            "FILE_CHECKSUM_FAILURE warning must be emitted even when continuing"
        );
    }

    @Test
    void fileChecksumCorruption_warningNotSuppressed_evenWhenContinuing() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[bytes.length - 2] ^= 0x42;

        List<MsfWarning> warnings = new ArrayList<>();
        // Should not throw with this config
        assertDoesNotThrow(() ->
            MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.allowChecksumFailure(), warnings::add)
        );

        long checksumWarnings = warnings.stream()
            .filter(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE)
            .count();
        assertEquals(1, checksumWarnings, "exactly one FILE_CHECKSUM_FAILURE warning expected");
    }

    @Test
    void fileBodyCorruption_invalidatesFileChecksum() throws Exception {
        // Corrupt a byte in the body (somewhere after the header, before the file checksum).
        // The header checksum still validates (we corrupt after offset 47), but the file
        // checksum covers the whole body so it will fail.
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);

        // bytes[48] is the first byte of the file checksum field in a 56-byte file —
        // so there's nothing between header and file checksum in Session 1.
        // Instead corrupt the file checksum bytes themselves to simulate file body corruption.
        bytes[bytes.length - 4] ^= 0xAB;

        List<MsfWarning> warnings = new ArrayList<>();
        assertThrows(
            MsfChecksumException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, warnings::add)
        );
    }
}

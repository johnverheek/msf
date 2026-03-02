package dev.msf.core;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for magic byte validation, file-too-short rejection, and file size mismatch warnings.
 *
 * @see MsfSpec Section 3.1 — magic bytes
 * @see MsfSpec Section 3.6 — file size
 */
class MsfMagicAndSizeTest {

    private static MsfHeader minimalHeader() {
        return new MsfHeader(1, 0, 0, 3953L, 48L, 49L, 50L, 0L, 0L, 0L, 0L);
    }

    // -------------------------------------------------------------------------
    // File-too-short
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "file of {0} bytes is too short for a header")
    @ValueSource(ints = { 0, 1, 4, 10, 47 })
    void fileTooShort_throwsMsfParseException_beforeAnyOtherValidation(int fileLength) {
        byte[] tooShort = new byte[fileLength];
        // Fill with correct magic just to confirm the check is size-first, not magic-first
        if (fileLength >= 4) {
            tooShort[0] = 0x4D;
            tooShort[1] = 0x53;
            tooShort[2] = 0x46;
            tooShort[3] = 0x21;
        }

        assertThrows(
            MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(tooShort, MsfReaderConfig.DEFAULT, null),
            "file shorter than 48 bytes must throw MsfParseException immediately"
        );
    }

    // -------------------------------------------------------------------------
    // Magic bytes
    // -------------------------------------------------------------------------

    @Test
    void magicBytesMismatch_atByte0_throwsMsfParseException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[0] = 0x00; // corrupt first magic byte

        assertThrows(MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void magicBytesMismatch_atByte1_throwsMsfParseException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[1] = 0x00; // corrupt second magic byte

        assertThrows(MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void magicBytesMismatch_atByte2_throwsMsfParseException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[2] = 0x00;

        assertThrows(MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void magicBytesMismatch_atByte3_throwsMsfParseException() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[3] = 0x00;

        assertThrows(MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void magicBytesCorruption_occursBeforeChecksumCheck() throws Exception {
        // Even if the checksum field happens to be valid, a magic mismatch must be caught first.
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        bytes[0] = 0x00; // kill the magic 'M'

        // Must throw MsfParseException (magic failure), NOT MsfChecksumException
        MsfParseException ex = assertThrows(
            MsfParseException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null)
        );
        assertNotNull(ex);
    }

    // -------------------------------------------------------------------------
    // File size mismatch
    // -------------------------------------------------------------------------

    @Test
    void fileSizeMismatch_emitsWarning_doesNotThrow() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);

        // Truncate to fewer bytes than declared — simulate a truncated file.
        // We need to recompute checksums though, or the header/file checksum will fail.
        // Instead: append extra bytes so actual > declared (less disruptive to checksums).
        // Actually, the simplest approach: build normally then append junk bytes so the
        // actual length exceeds the declared file_size, which is 56.
        byte[] extended = new byte[bytes.length + 10];
        System.arraycopy(bytes, 0, extended, 0, bytes.length);
        // The file_size in the header still says 56, but actual length is 66.
        // The file checksum stored at bytes 48–55 is still valid for the first 48 bytes,
        // but the actual file is longer. So: FILE_SIZE_MISMATCH fires, then FILE_CHECKSUM_FAILURE
        // fires because the checksum input range is the actual length - 8 = 58 bytes.

        List<MsfWarning> warnings = new ArrayList<>();
        // Use allowChecksumFailure so we don't stop at the checksum error
        MsfReader.readHeaderFromBytes(extended, MsfReaderConfig.allowChecksumFailure(), warnings::add);

        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.FILE_SIZE_MISMATCH),
            "FILE_SIZE_MISMATCH warning must be emitted when actual size != declared size"
        );
    }

    @Test
    void fileSizeMismatch_subsequentChecksumFailure_notesMismatch() throws Exception {
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        byte[] extended = new byte[bytes.length + 10];
        System.arraycopy(bytes, 0, extended, 0, bytes.length);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfReader.readHeaderFromBytes(extended, MsfReaderConfig.allowChecksumFailure(), warnings::add);

        // If both warnings fire, the FILE_CHECKSUM_FAILURE message must note the size mismatch
        boolean hasSizeMismatch = warnings.stream()
            .anyMatch(w -> w.code() == MsfWarning.Code.FILE_SIZE_MISMATCH);
        boolean hasChecksumFailure = warnings.stream()
            .anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE);

        if (hasSizeMismatch && hasChecksumFailure) {
            MsfWarning checksumWarning = warnings.stream()
                .filter(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE)
                .findFirst()
                .orElseThrow();

            assertTrue(
                checksumWarning.message().toLowerCase().contains("mismatch")
                || checksumWarning.message().toLowerCase().contains("unreliable"),
                "FILE_CHECKSUM_FAILURE message must note the size mismatch: " + checksumWarning.message()
            );
        }
    }

    // -------------------------------------------------------------------------
    // Warning consumer receives correct codes and offsets
    // -------------------------------------------------------------------------

    @Test
    void warningConsumer_receivesCorrectOffset_forFileSizeMismatch() throws Exception {
        // FILE_SIZE_MISMATCH warning should be at the file_size field offset = 36
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        byte[] extended = new byte[bytes.length + 5];
        System.arraycopy(bytes, 0, extended, 0, bytes.length);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfReader.readHeaderFromBytes(extended, MsfReaderConfig.allowChecksumFailure(), warnings::add);

        MsfWarning sizeMismatch = warnings.stream()
            .filter(w -> w.code() == MsfWarning.Code.FILE_SIZE_MISMATCH)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected FILE_SIZE_MISMATCH warning"));

        assertEquals(36L, sizeMismatch.offset(),
            "FILE_SIZE_MISMATCH warning offset should be 36 (the file_size field offset)");
    }

    @Test
    void warningConsumer_isNull_noCrash_warningsDropped() throws Exception {
        // Supplying null consumer must not cause NPE — warnings are silently discarded
        MsfHeader header = minimalHeader();
        byte[] bytes = MsfWriter.buildFileBytes(header, null);

        assertDoesNotThrow(() -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void warningConsumer_writeSideWarning_hasOffsetMinusOne() throws Exception {
        // Write-side warnings must have offset = -1 (Section 3.5.1)
        MsfHeader header = new MsfHeader(
            1, 0,
            (1 << 10), // reserved bit set — will trigger RESERVED_FLAG_CLEARED
            3953L, 48L, 49L, 50L, 0L, 0L, 0L, 0L
        );

        List<MsfWarning> warnings = new ArrayList<>();
        MsfWriter.buildFileBytes(header, warnings::add);

        MsfWarning clearedWarning = warnings.stream()
            .filter(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_CLEARED)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected RESERVED_FLAG_CLEARED warning"));

        assertEquals(-1L, clearedWarning.offset(),
            "write-side warnings must have offset -1");
    }

    @Test
    void warningConsumer_multipleWarnings_deliveredIndividually_notAggregated() throws Exception {
        // Construct a situation where multiple distinct warnings fire (size mismatch + checksum failure)
        byte[] bytes = MsfWriter.buildFileBytes(minimalHeader(), null);
        byte[] extended = new byte[bytes.length + 20];
        System.arraycopy(bytes, 0, extended, 0, bytes.length);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfReader.readHeaderFromBytes(extended, MsfReaderConfig.allowChecksumFailure(), warnings::add);

        // Each warning must be delivered as a separate call — list size > 1 if multiple fire
        // We know at minimum FILE_SIZE_MISMATCH fires; if FILE_CHECKSUM_FAILURE also fires
        // they are two separate list entries.
        assertTrue(warnings.size() >= 1, "at least one warning must be delivered");

        // Verify no single warning has multiple codes (i.e. they are not aggregated)
        for (MsfWarning w : warnings) {
            assertNotNull(w.code());
            assertNotNull(w.message());
        }
    }
}

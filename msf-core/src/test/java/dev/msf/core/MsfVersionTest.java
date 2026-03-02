package dev.msf.core;

import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.checksum.XxHash3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for major version rejection and minor version tolerance.
 *
 * @see MsfSpec Section 3.2 — version fields
 * @see MsfSpec Section 12.3 — universal V1 compatibility
 */
class MsfVersionTest {

    /**
     * Builds a raw byte array representing a header with the given major and minor versions,
     * recomputing the header checksum so the validation sequence passes up to the version check.
     */
    private static byte[] buildRawHeaderBytes(int majorVersion, int minorVersion) {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);

        // Magic bytes
        buf.put(0, (byte) 0x4D);
        buf.put(1, (byte) 0x53);
        buf.put(2, (byte) 0x46);
        buf.put(3, (byte) 0x21);

        // Version fields
        buf.putShort(4, (short) (majorVersion & 0xFFFF));
        buf.putShort(6, (short) (minorVersion & 0xFFFF));

        // Feature flags = 0
        buf.putInt(8, 0);

        // MC data version = 3953 (Minecraft 1.21)
        buf.putInt(12, 3953);

        // Required offsets — must be non-zero to pass offset validation
        buf.putInt(16, 48); // metadataOffset
        buf.putInt(20, 49); // globalPaletteOffset
        buf.putInt(24, 50); // layerIndexOffset
        buf.putInt(28, 0);  // entityBlockOffset
        buf.putInt(32, 0);  // blockEntityBlockOffset

        // file_size = 56 (48 header + 8 file checksum)
        buf.putInt(36, 56);

        // Compute header checksum over bytes 0–39
        byte[] rawBytes = buf.array();
        long headerChecksum = XxHash3.hash(rawBytes, 0, 40);
        buf.putLong(40, headerChecksum);

        // Compute and append file checksum (over bytes 0–47)
        rawBytes = buf.array();
        long fileChecksum = XxHash3.hash(rawBytes, 0, 48);
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, fileChecksum);
        System.arraycopy(checksumBuf.array(), 0, rawBytes, 48, 8);

        return rawBytes;
    }

    // -------------------------------------------------------------------------
    // Unsupported major version
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "major version {0} must be rejected")
    @ValueSource(ints = { 0, 2, 3, 65535 })
    void unsupportedMajorVersion_throwsMsfVersionException(int badMajor) {
        byte[] bytes = buildRawHeaderBytes(badMajor, 0);

        MsfVersionException ex = assertThrows(
            MsfVersionException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null)
        );

        assertEquals(badMajor, ex.getFoundVersion(),
            "exception must report the major version found in the file");
        assertEquals(1, ex.getSupportedVersion(),
            "exception must report the supported major version (1)");
    }

    @Test
    void majorVersion0_isRejected_notTreatedAsPreRelease() {
        // Section 3.2: major version 0 receives no special treatment
        byte[] bytes = buildRawHeaderBytes(0, 0);
        assertThrows(MsfVersionException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));
    }

    @Test
    void unsupportedMajorVersion_exceptionMessageContainsBothVersions() {
        byte[] bytes = buildRawHeaderBytes(2, 0);
        MsfVersionException ex = assertThrows(
            MsfVersionException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null)
        );

        String msg = ex.getMessage();
        assertTrue(msg.contains("2"), "message must contain the found version (2)");
        assertTrue(msg.contains("1"), "message must contain the supported version (1)");
    }

    // -------------------------------------------------------------------------
    // Minor version tolerance — forward compatibility
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "minor version {0} must be accepted")
    @ValueSource(ints = { 0, 1, 5, 100, 1000 })
    void higherMinorVersion_isAccepted_notRejected(int minorVersion) throws Exception {
        byte[] bytes = buildRawHeaderBytes(1, minorVersion);

        List<MsfWarning> warnings = new ArrayList<>();
        // Should not throw — minor version is not a compatibility gate per Section 3.2
        MsfHeader parsed = MsfReader.readHeaderFromBytes(
            bytes, MsfReaderConfig.DEFAULT, warnings::add
        );

        assertNotNull(parsed);
        assertEquals(1, parsed.majorVersion());
        assertEquals(minorVersion, parsed.minorVersion());
    }

    @Test
    void futureMinorVersion_doesNotWarnAboutReservedBits_whenMinorVersionExceedsImplemented() throws Exception {
        // Per Section 3.3: if the file's minor version exceeds the reader's implemented minor
        // version (0), readers MUST NOT warn on reserved bits, because those bits may carry
        // defined meaning in a later minor version.
        // We construct a file with minorVersion=1 and reserved bits set.
        byte[] bytes = buildRawHeaderBytes(1, 1);

        // Manually set a reserved bit in the feature flags field (bit 10) — offset 8.
        // We need to recompute the header checksum after doing this.
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int flags = buf.getInt(8);
        flags |= (1 << 10); // set reserved bit 10
        buf.putInt(8, flags);

        // Recompute header checksum (bytes 0–39)
        long newHeaderChecksum = XxHash3.hash(bytes, 0, 40);
        buf.putLong(40, newHeaderChecksum);

        // Recompute file checksum (bytes 0–47)
        long newFileChecksum = XxHash3.hash(bytes, 0, 48);
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, newFileChecksum);
        System.arraycopy(checksumBuf.array(), 0, bytes, 48, 8);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, warnings::add);

        boolean hasReservedFlagWarning = warnings.stream()
            .anyMatch(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_SET);

        assertFalse(hasReservedFlagWarning,
            "must NOT warn about reserved bits when minor version exceeds reader's implemented version");
    }

    @Test
    void v10File_withReservedBitsSet_emitsWarning() throws Exception {
        // Per Section 3.3: for V1.0 files (minorVersion == 0), reserved bits 10–31 MUST trigger
        // a RESERVED_FLAG_SET warning.
        byte[] bytes = buildRawHeaderBytes(1, 0);

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int flags = buf.getInt(8);
        flags |= (1 << 10); // set reserved bit 10
        buf.putInt(8, flags);

        // Recompute header checksum
        long newHeaderChecksum = XxHash3.hash(bytes, 0, 40);
        buf.putLong(40, newHeaderChecksum);

        // Recompute file checksum
        long newFileChecksum = XxHash3.hash(bytes, 0, 48);
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, newFileChecksum);
        System.arraycopy(checksumBuf.array(), 0, bytes, 48, 8);

        List<MsfWarning> warnings = new ArrayList<>();
        MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, warnings::add);

        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_SET),
            "RESERVED_FLAG_SET warning must be emitted for V1.0 files with reserved bits set"
        );
    }

    @Test
    void writer_clearReservedBits_emitsReservedFlagClearedWarning() throws Exception {
        // Writer must clear reserved bits 10–31 and emit RESERVED_FLAG_CLEARED warning.
        MsfHeader header = new MsfHeader(
            1, 0,
            (1 << 10) | MsfHeader.FeatureFlags.HAS_ENTITIES, // reserved bit 10 + valid bit 0
            3953L, 48L, 49L, 50L, 51L, 0L, 0L, 0L
        );

        List<MsfWarning> warnings = new ArrayList<>();
        byte[] bytes = MsfWriter.buildFileBytes(header, warnings::add);

        assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_CLEARED),
            "RESERVED_FLAG_CLEARED must be emitted when writer clears reserved bits"
        );

        // Verify the written file does NOT have the reserved bit set
        MsfHeader parsed = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null);
        assertEquals(0, parsed.featureFlags() & MsfHeader.FeatureFlags.RESERVED_BITS_MASK,
            "reserved bits must be cleared in the written file");

        // The valid bit 0 must still be present
        assertEquals(MsfHeader.FeatureFlags.HAS_ENTITIES,
            parsed.featureFlags() & MsfHeader.FeatureFlags.HAS_ENTITIES);
    }
}

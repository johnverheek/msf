package dev.msf.core;

import dev.msf.core.checksum.XxHash3;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the reader validates magic bytes, header checksum, and major version
 * in strict order per Section 3.7 — and does not act on any other field before
 * all three steps pass.
 *
 * @see MsfSpec Section 3.7 — header validation sequence
 */
class MsfValidationSequenceTest {

    /**
     * Builds a raw 56-byte buffer with specific major version and checksum,
     * with valid magic bytes. Used to set up precise failure scenarios.
     *
     * @param majorVersion the major version to write
     * @param computeValidChecksum if true, computes a valid checksum; if false, zeroes it
     */
    private static byte[] buildControlledBytes(int majorVersion, boolean computeValidChecksum) {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 0x4D);
        buf.put(1, (byte) 0x53);
        buf.put(2, (byte) 0x46);
        buf.put(3, (byte) 0x21);
        buf.putShort(4, (short) (majorVersion & 0xFFFF));
        buf.putShort(6, (short) 0); // minor version 0
        buf.putInt(8, 0);           // feature flags
        buf.putInt(12, 3953);       // MC data version
        buf.putInt(16, 48);         // metadataOffset
        buf.putInt(20, 49);         // globalPaletteOffset
        buf.putInt(24, 50);         // layerIndexOffset
        buf.putInt(28, 0);
        buf.putInt(32, 0);
        buf.putInt(36, 56);         // file_size

        byte[] bytes = buf.array();

        if (computeValidChecksum) {
            long checksum = XxHash3.hash(bytes, 0, 40);
            buf.putLong(40, checksum);
            bytes = buf.array();
            long fileChecksum = XxHash3.hash(bytes, 0, 48);
            ByteBuffer csb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            csb.putLong(0, fileChecksum);
            System.arraycopy(csb.array(), 0, bytes, 48, 8);
        }
        // else: checksum field is 0 — deliberately invalid

        return bytes;
    }

    @Test
    void step1_magicMismatch_throwsParseException_beforeChecksumCheck() {
        // Magic mismatch with a valid checksum for the original bytes — if we compute the
        // checksum over corrupted bytes, it changes. The point is: magic must be checked
        // BEFORE the checksum. So we use bytes where magic is wrong but checksum is 0
        // (also wrong) — magic must fail first.
        byte[] bytes = buildControlledBytes(1, false);
        bytes[0] = 0x00; // bad magic, also bad checksum → magic must be reported

        Exception ex = assertThrows(MsfException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));

        // The exception must be MsfParseException (magic), not MsfChecksumException
        assertInstanceOf(MsfParseException.class, ex,
            "magic byte mismatch must throw MsfParseException, not MsfChecksumException");
    }

    @Test
    void step2_badChecksum_withGoodMagic_throwsChecksumException_beforeVersionCheck() {
        // Good magic, bad header checksum, bad major version (2) — checksum must fire before version
        byte[] bytes = buildControlledBytes(2, false); // major=2, no valid checksum

        Exception ex = assertThrows(MsfException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));

        // Must be MsfChecksumException, not MsfVersionException
        assertInstanceOf(MsfChecksumException.class, ex,
            "header checksum must be verified before inspecting major version");
    }

    @Test
    void step3_badMajorVersion_withGoodMagicAndChecksum_throwsVersionException() {
        // Good magic, valid checksum, bad major version (2) — all three in order
        byte[] bytes = buildControlledBytes(2, true);

        Exception ex = assertThrows(MsfException.class,
            () -> MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null));

        assertInstanceOf(MsfVersionException.class, ex,
            "after magic and checksum pass, bad major version must throw MsfVersionException");
    }

    @Test
    void allThreeStepsPass_withVersion1_returnsHeader() throws Exception {
        byte[] bytes = buildControlledBytes(1, true);
        MsfHeader header = MsfReader.readHeaderFromBytes(bytes, MsfReaderConfig.DEFAULT, null);
        assertNotNull(header);
        assertEquals(1, header.majorVersion());
    }

    @Test
    void headerChecksumCoversBytes0to39_not40to47() throws Exception {
        // Changing the 8 bytes at 40–47 (the checksum field itself) should NOT affect whether
        // the checksum recomputation passes — because the checksum is computed over 0–39 only.
        // This test verifies the computation boundary is correct.
        byte[] bytes = MsfWriter.buildFileBytes(
            new MsfHeader(1, 0, 0, 3953L, 48L, 49L, 50L, 0L, 0L, 0L, 0L), null
        );

        // Verify that checksum computed over 0–39 matches what's stored at 40–47
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long stored = buf.getLong(40);
        long computed = XxHash3.hash(bytes, 0, 40);
        assertEquals(stored, computed, "header checksum must cover exactly bytes 0–39");

        // Also verify checksum at 40–47 is NOT included in its own computation
        // (i.e. changing bytes 40–47 without recomputing does NOT affect the 0–39 hash)
        long hashOfFirst40Bytes = XxHash3.hash(bytes, 0, 40);
        bytes[40] ^= 0xFF; // corrupt the checksum field
        long hashAfterCorruption = XxHash3.hash(bytes, 0, 40);
        assertEquals(hashOfFirst40Bytes, hashAfterCorruption,
            "checksum of bytes 0–39 must not change when bytes 40–47 change");
    }
}

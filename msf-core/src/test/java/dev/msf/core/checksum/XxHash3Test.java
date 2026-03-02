package dev.msf.core.checksum;

import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XxHash3}.
 */
@DisplayName("XxHash3 Checksum")
class XxHash3Test {

    @Test
    @DisplayName("Empty array produces a stable, non-exception hash")
    void emptyArrayDoesNotThrow() {
        assertDoesNotThrow(() -> XxHash3.hash(new byte[0]));
    }

    @Test
    @DisplayName("Same bytes produce the same hash (determinism)")
    void deterministic() {
        byte[] data = "Hello, MSF!".getBytes();
        long hash1 = XxHash3.hash(data);
        long hash2 = XxHash3.hash(data);
        assertEquals(hash1, hash2, "xxHash3 must be deterministic for the same input");
    }

    @Test
    @DisplayName("Different bytes produce different hashes (sensitivity)")
    void sensitive() {
        byte[] a = "block_a".getBytes();
        byte[] b = "block_b".getBytes();
        assertNotEquals(XxHash3.hash(a), XxHash3.hash(b));
    }

    @Test
    @DisplayName("Single-byte change produces a different hash")
    void singleByteChange() {
        byte[] original = new byte[64];
        Arrays.fill(original, (byte) 0xAB);
        long before = XxHash3.hash(original);

        original[32] ^= 0x01;
        long after = XxHash3.hash(original);

        assertNotEquals(before, after, "Flipping one bit must change the hash");
    }

    @Test
    @DisplayName("headerChecksum covers exactly bytes 0-39 of a 48-byte buffer")
    void headerChecksumCoversFirst40Bytes() {
        byte[] buf = new byte[48];
        Arrays.fill(buf, (byte) 0x42);

        long viaHeader = XxHash3.headerChecksum(buf);
        long viaSlice = XxHash3.hash(buf, 0, 40);

        assertEquals(viaSlice, viaHeader,
            "headerChecksum must hash exactly bytes 0–39");
    }

    @Test
    @DisplayName("headerChecksum ignores bytes 40-47 (the checksum field itself)")
    void headerChecksumIgnoresChecksumField() {
        byte[] bufA = new byte[48];
        byte[] bufB = new byte[48];
        Arrays.fill(bufA, (byte) 0x55);
        Arrays.fill(bufB, (byte) 0x55);
        // Differ only in the checksum field (bytes 40–47).
        bufB[40] = (byte) 0xFF;
        bufB[47] = (byte) 0xFF;

        assertEquals(XxHash3.headerChecksum(bufA), XxHash3.headerChecksum(bufB),
            "Differences in the checksum field must not affect the computed header checksum");
    }

    @Test
    @DisplayName("headerChecksum rejects buffers shorter than 40 bytes")
    void headerChecksumTooShort() {
        assertThrows(IllegalArgumentException.class,
            () -> XxHash3.headerChecksum(new byte[39])
        );
    }

    @Test
    @DisplayName("fileChecksum covers all bytes except the final 8")
    void fileChecksumExcludesLastEightBytes() {
        byte[] file = new byte[100];
        Arrays.fill(file, (byte) 0x77);

        long viaFile = XxHash3.fileChecksum(file);
        long viaSlice = XxHash3.hash(file, 0, 92); // 100 - 8 = 92

        assertEquals(viaSlice, viaFile);
    }

    @Test
    @DisplayName("fileChecksum rejects files shorter than 9 bytes")
    void fileChecksumTooShort() {
        assertThrows(IllegalArgumentException.class,
            () -> XxHash3.fileChecksum(new byte[8])
        );
    }

    @Test
    @DisplayName("Hash with explicit offset and length matches slice")
    void hashWithOffsetMatchesSlice() {
        byte[] data = "prefix_content_suffix".getBytes();
        int offset = 7; // "content_suffix"
        int length = 7; // "content"

        byte[] slice = Arrays.copyOfRange(data, offset, offset + length);

        assertEquals(XxHash3.hash(slice), XxHash3.hash(data, offset, length));
    }
}

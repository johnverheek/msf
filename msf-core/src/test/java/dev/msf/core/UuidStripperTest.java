package dev.msf.core;

import dev.msf.core.util.UuidStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UuidStripper} — binary NBT UUID tag removal.
 */
class UuidStripperTest {

    // =========================================================================
    // Test 1: strips TAG_Int_Array named "UUID"
    // =========================================================================

    @Test
    void strip_uuidIntArray_isRemoved() throws IOException {
        // Compound NBT with a "UUID" int array (to strip) and a "Health" byte tag (to preserve).
        byte[] input = buildCompoundNbt(out -> {
            writeIntArrayTag(out, "UUID", new int[]{1, 2, 3, 4});
            writeByteTag(out, "Health", (byte) 20);
        });

        byte[] result = UuidStripper.strip(input);

        assertFalse(
            containsBytes(result, "UUID".getBytes(StandardCharsets.UTF_8)),
            "UUID tag name must not appear in output after stripping"
        );
        assertTrue(
            containsBytes(result, "Health".getBytes(StandardCharsets.UTF_8)),
            "Health tag must be preserved after stripping"
        );
    }

    // =========================================================================
    // Test 2: strips TAG_Long named "UUIDMost" and "UUIDLeast"
    // =========================================================================

    @Test
    void strip_uuidMostAndLeast_areRemoved() throws IOException {
        byte[] input = buildCompoundNbt(out -> {
            writeLongTag(out, "UUIDMost",  0x1234567890ABCDEFL);
            writeLongTag(out, "UUIDLeast", 0xFEDCBA0987654321L);
            writeByteTag(out, "Data", (byte) 0x55);
        });

        byte[] result = UuidStripper.strip(input);

        assertFalse(
            containsBytes(result, "UUIDMost".getBytes(StandardCharsets.UTF_8)),
            "UUIDMost tag name must not appear in output after stripping"
        );
        assertFalse(
            containsBytes(result, "UUIDLeast".getBytes(StandardCharsets.UTF_8)),
            "UUIDLeast tag name must not appear in output after stripping"
        );
        assertTrue(
            containsBytes(result, "Data".getBytes(StandardCharsets.UTF_8)),
            "Data tag must be preserved after stripping"
        );
    }

    // =========================================================================
    // Test 3: no UUID tags → output is byte-for-byte identical to input
    // =========================================================================

    @Test
    void strip_noUuidTags_returnsUnchanged() throws IOException {
        byte[] input = buildCompoundNbt(out -> {
            writeByteTag(out, "CustomData", (byte) 42);
            writeLongTag(out, "Timestamp", 1_700_000_000L);
        });

        byte[] result = UuidStripper.strip(input);

        assertArrayEquals(
            input,
            result,
            "Output must be byte-for-byte identical when no UUID tags are present"
        );
    }

    // =========================================================================
    // NBT construction helpers
    // =========================================================================

    @FunctionalInterface
    interface NbtBody {
        void write(ByteArrayOutputStream out) throws IOException;
    }

    /** Wraps {@code body} in a minimal TAG_Compound root (type byte + empty name + body + TAG_End). */
    private static byte[] buildCompoundNbt(NbtBody body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A);        // TAG_Compound root
        writeBeU16(out, 0);     // empty root name
        body.write(out);
        out.write(0x00);        // TAG_End
        return out.toByteArray();
    }

    private static void writeByteTag(ByteArrayOutputStream out, String name, byte value) throws IOException {
        out.write(0x01);        // TAG_Byte
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        writeBeU16(out, n.length);
        out.write(n);
        out.write(value & 0xFF);
    }

    private static void writeLongTag(ByteArrayOutputStream out, String name, long value) throws IOException {
        out.write(0x04);        // TAG_Long
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        writeBeU16(out, n.length);
        out.write(n);
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        b.putLong(value);
        out.write(b.array());
    }

    private static void writeIntArrayTag(ByteArrayOutputStream out, String name, int[] values) throws IOException {
        out.write(0x0B);        // TAG_Int_Array
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        writeBeU16(out, n.length);
        out.write(n);
        ByteBuffer b = ByteBuffer.allocate(4 + values.length * 4).order(ByteOrder.BIG_ENDIAN);
        b.putInt(values.length);
        for (int v : values) b.putInt(v);
        out.write(b.array());
    }

    /** Writes a big-endian unsigned 16-bit integer. */
    private static void writeBeU16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** Returns {@code true} if {@code haystack} contains {@code needle} as a contiguous subarray. */
    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}

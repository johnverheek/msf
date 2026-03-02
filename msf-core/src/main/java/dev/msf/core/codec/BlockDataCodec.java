package dev.msf.core.codec;

import dev.msf.core.MsfParseException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encodes and decodes region block data arrays using {@link BitPackedArray}.
 *
 * <p>A region block data array is a contiguous sequence of palette IDs stored in YZX order.
 * This codec handles the serialization layer: writing the {@code bits_per_entry} byte and
 * {@code packed_array_length} u32 field, followed by the packed {@code long[]} words.
 * It also handles validation on read: verifying the stored {@code packed_array_length}
 * against the formula, detecting out-of-range palette IDs, and rejecting
 * {@code bits_per_entry = 0}.
 *
 * <p>Callers are responsible for providing the decompressed byte buffer positioned
 * at the start of the block data section and for advancing it past the data on read.
 *
 * @see MsfSpec Section 7.3 — region payload
 * @see MsfSpec Section 7.5 — bit packing
 */
public final class BlockDataCodec {

    private BlockDataCodec() {
        // Utility class — not instantiable
    }

    // -------------------------------------------------------------------------
    // Encode (write)
    // -------------------------------------------------------------------------

    /**
     * Encodes a block data array into bytes ready for inclusion in a compressed region payload.
     *
     * <p>Layout written:
     * <pre>
     *     u8     bits_per_entry
     *     u32    packed_array_length (count of u64 words)
     *     u64[]  packed block data
     * </pre>
     *
     * @param paletteIds    the palette ID for each block in YZX order; each value must be
     *                      a valid palette ID (0 to paletteSize-1 inclusive)
     * @param paletteSize   total number of palette entries; used to compute bits_per_entry
     * @return the encoded bytes
     * @throws IllegalArgumentException if any palette ID is out of range, or paletteSize < 1
     */
    public static byte[] encode(int[] paletteIds, int paletteSize) {
        int bitsPerEntry = BitPackedArray.bitsPerEntry(paletteSize);
        long[] words = BitPackedArray.pack(paletteIds, bitsPerEntry);

        long wordCount = words.length;
        if (wordCount > 0xFFFFFFFFL) {
            throw new IllegalArgumentException(
                "packed_array_length " + wordCount + " exceeds u32 maximum 4294967295"
            );
        }

        // Layout: 1 (bits_per_entry) + 4 (packed_array_length) + 8*wordCount
        int byteCount = 1 + 4 + (int) (wordCount * 8L);
        ByteBuffer buf = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) bitsPerEntry);
        buf.putInt((int) wordCount);
        for (long w : words) {
            buf.putLong(w);
        }
        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Decode (read)
    // -------------------------------------------------------------------------

    /**
     * Decodes block data from a {@link ByteBuffer} positioned at the start of the block data
     * section of a decompressed region payload.
     *
     * <p>Reads:
     * <pre>
     *     u8     bits_per_entry
     *     u32    packed_array_length (count of u64 words)
     *     u64[]  packed block data
     * </pre>
     *
     * <p>Validates:
     * <ul>
     *   <li>{@code bits_per_entry} must not be 0 (Section 7.5)</li>
     *   <li>stored {@code packed_array_length} must match the value computed from
     *       {@code entryCount} and {@code bits_per_entry} (Section 7.5)</li>
     *   <li>every unpacked palette ID must be < {@code paletteSize} (Section 7.5)</li>
     * </ul>
     *
     * <p>The buffer's position is advanced past all consumed bytes.
     *
     * @param buf           the decompressed payload buffer; must have sufficient remaining bytes
     * @param entryCount    number of block entries expected (sizeX * sizeY * sizeZ)
     * @param paletteSize   total number of palette entries for range validation
     * @return the decoded palette IDs in YZX order
     * @throws MsfParseException if any validation check fails
     */
    public static int[] decode(ByteBuffer buf, long entryCount, int paletteSize)
            throws MsfParseException {
        // Read bits_per_entry (u8)
        int bitsPerEntry = Byte.toUnsignedInt(buf.get());
        if (bitsPerEntry == 0) {
            throw new MsfParseException(
                "Region payload has bits_per_entry = 0, which is invalid (Section 7.5)"
            );
        }

        // Read packed_array_length (u32)
        long storedWordCount = Integer.toUnsignedLong(buf.getInt());

        // Validate stored packed_array_length against derived value (Section 7.5)
        long expectedWordCount = BitPackedArray.wordsRequired(entryCount, bitsPerEntry);
        if (storedWordCount != expectedWordCount) {
            throw new MsfParseException(String.format(
                "Region payload packed_array_length mismatch: stored %d, expected %d "
                + "(entryCount=%d, bitsPerEntry=%d) — misaligned parsing would result",
                storedWordCount, expectedWordCount, entryCount, bitsPerEntry
            ));
        }

        if (storedWordCount > Integer.MAX_VALUE / 8) {
            throw new MsfParseException(
                "packed_array_length " + storedWordCount + " is too large to allocate"
            );
        }

        // Read packed u64 words
        long[] words = new long[(int) storedWordCount];
        for (int i = 0; i < words.length; i++) {
            words[i] = buf.getLong();
        }

        // Unpack
        if (entryCount > Integer.MAX_VALUE) {
            throw new MsfParseException(
                "entryCount " + entryCount + " exceeds int[] maximum size"
            );
        }
        int[] paletteIds = BitPackedArray.unpack(words, (int) entryCount, bitsPerEntry);

        // Validate palette ID range (Section 7.5) — every value must be < paletteSize
        for (int i = 0; i < paletteIds.length; i++) {
            if (paletteIds[i] >= paletteSize) {
                throw new MsfParseException(String.format(
                    "Block data at YZX index %d contains out-of-range palette ID %d "
                    + "(palette has %d entries, max valid ID is %d)",
                    i, paletteIds[i], paletteSize, paletteSize - 1
                ));
            }
        }

        return paletteIds;
    }
}

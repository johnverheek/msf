package dev.msf.core.codec;

/**
 * Packs and unpacks integer values into an array of {@code long} words using a
 * fixed
 * number of bits per entry, as specified by MSF Section 7.5.
 *
 * <h2>Packing rules (Section 7.5)</h2>
 * <ul>
 * <li>Entries are packed from the least-significant bit of each {@code long}
 * word.</li>
 * <li>An entry that would span a word boundary begins in the <em>next</em> word
 * instead.
 * The remaining bits of the previous word are set to {@code 0} and ignored by
 * readers.</li>
 * <li>The caller provides {@code bitsPerEntry} explicitly; it is stored with
 * the data
 * and MUST NOT be re-derived independently (Section 7.5).</li>
 * </ul>
 *
 * <h2>Packed array length formula (Section 7.5)</h2>
 * 
 * <pre>
 * packedLength = ceil((entryCount * bitsPerEntry) / 64)
 * </pre>
 * 
 * Because of the no-spanning rule, the actual word count may be higher than
 * this formula
 * suggests when many entries are too wide to fit in the remaining bits of a
 * word. The
 * {@link #wordsRequired(int, int)} method computes the exact word count
 * accounting for
 * the no-spanning rule.
 *
 * <p>
 * All intermediate arithmetic uses {@code long} to prevent overflow.
 *
 * @see MsfSpec Section 7.5 — bit packing
 */
public final class BitPackedArray {

    private BitPackedArray() {
        // Utility class — not instantiable
    }

    // -------------------------------------------------------------------------
    // Packing formula
    // -------------------------------------------------------------------------

    /**
     * Computes the number of {@code long} words required to store
     * {@code entryCount} values
     * using {@code bitsPerEntry} bits each, accounting for the no-spanning rule.
     *
     * <p>
     * This is the value that MUST be stored in the {@code packed_array_length}
     * field of
     * a region payload per Section 7.5. Readers MUST verify the stored value
     * against this
     * formula.
     *
     * @param entryCount   number of entries to pack
     * @param bitsPerEntry bits used per entry; must be in the range [1, 64]
     * @return number of {@code long} words required
     * @throws IllegalArgumentException if {@code bitsPerEntry} is not in [1, 64]
     */
    public static long wordsRequired(long entryCount, int bitsPerEntry) {
        if (bitsPerEntry < 1 || bitsPerEntry > 64) {
            throw new IllegalArgumentException(
                    "bitsPerEntry must be in [1, 64], got " + bitsPerEntry);
        }
        if (entryCount <= 0) {
            return 0;
        }
        // How many entries fit in one word without spanning?
        int entriesPerWord = 64 / bitsPerEntry;
        // Ceiling division: ceil(entryCount / entriesPerWord)
        return (entryCount + entriesPerWord - 1) / entriesPerWord;
    }

    /**
     * Computes the packed array length using the formula from Section 7.5:
     * {@code ceil((entryCount * bitsPerEntry) / 64)}.
     *
     * <p>
     * This formula is used for validation only — the actual number of words
     * required
     * may be higher due to the no-spanning rule. The spec mandates that the stored
     * {@code packed_array_length} field equals the value from
     * {@link #wordsRequired},
     * which accounts for the no-spanning rule. Both formulas are provided here for
     * testing.
     *
     * @param entryCount   number of entries
     * @param bitsPerEntry bits per entry; must be in [1, 64]
     * @return {@code ceil((entryCount * bitsPerEntry) / 64)}
     */
    public static long formulaWordsRequired(long entryCount, int bitsPerEntry) {
        if (bitsPerEntry < 1 || bitsPerEntry > 64) {
            throw new IllegalArgumentException(
                    "bitsPerEntry must be in [1, 64], got " + bitsPerEntry);
        }
        if (entryCount <= 0) {
            return 0;
        }
        long bits = entryCount * bitsPerEntry; // long to prevent overflow
        return (bits + 63L) / 64L;
    }

    // -------------------------------------------------------------------------
    // Bit count formula
    // -------------------------------------------------------------------------

    /**
     * Computes the number of bits per entry required to represent
     * {@code paletteSize} distinct
     * values, using the formula from Section 7.5:
     * 
     * <pre>
     * bitsPerEntry = max(1, ceil(log2(paletteSize)))
     * </pre>
     *
     * <p>
     * This formula is defined only for {@code paletteSize >= 1}. Since palette
     * entry 0
     * ({@code minecraft:air}) is always present, the minimum palette size is 1,
     * which yields
     * {@code bitsPerEntry = 1}.
     *
     * @param paletteSize number of distinct palette entries; must be ≥ 1
     * @return bits per entry in [1, 16] for valid palette sizes (palette is limited
     *         to 65535 entries)
     * @throws IllegalArgumentException if {@code paletteSize} is less than 1
     */
    public static int bitsPerEntry(int paletteSize) {
        if (paletteSize < 1) {
            throw new IllegalArgumentException(
                    "paletteSize must be >= 1, got " + paletteSize);
        }
        if (paletteSize == 1) {
            // ceil(log2(1)) = 0, max(1, 0) = 1
            return 1;
        }
        // For paletteSize > 1: ceil(log2(n)) = 32 - Integer.numberOfLeadingZeros(n - 1)
        // This is equivalent to the number of bits needed to represent values 0..n-1
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    // -------------------------------------------------------------------------
    // Pack
    // -------------------------------------------------------------------------

    /**
     * Packs {@code values} into an array of {@code long} words using
     * {@code bitsPerEntry}
     * bits per entry.
     *
     * <p>
     * Values are packed from the LSB of each word. An entry that would span a word
     * boundary
     * begins in the next word; remaining bits in the previous word are zeroed.
     *
     * @param values       the integer values to pack; each value must be in [0,
     *                     2^bitsPerEntry)
     * @param bitsPerEntry bits to use per entry; must be in [1, 64]
     * @return a {@code long[]} array containing the packed data
     * @throws IllegalArgumentException if any value is negative or requires more
     *                                  than
     *                                  {@code bitsPerEntry} bits
     */
    public static long[] pack(int[] values, int bitsPerEntry) {
        if (bitsPerEntry < 1 || bitsPerEntry > 64) {
            throw new IllegalArgumentException(
                    "bitsPerEntry must be in [1, 64], got " + bitsPerEntry);
        }
        int wordCount = (int) wordsRequired(values.length, bitsPerEntry);
        long[] words = new long[wordCount];
        if (values.length == 0) {
            return words;
        }

        long mask = bitsPerEntry == 64 ? -1L : (1L << bitsPerEntry) - 1L;
        int wordIndex = 0;
        int bitOffset = 0;

        for (int value : values) {
            long v = Integer.toUnsignedLong(value);
            if ((v & ~mask) != 0) {
                throw new IllegalArgumentException(
                        "Value " + (v & 0xFFFFFFFFL) + " does not fit in " + bitsPerEntry + " bits");
            }
            // Would this entry span a word boundary?
            if (bitOffset + bitsPerEntry > 64) {
                // Start a new word — per spec the entry begins in the next word
                wordIndex++;
                bitOffset = 0;
            }
            words[wordIndex] |= v << bitOffset;
            bitOffset += bitsPerEntry;
            if (bitOffset == 64) {
                wordIndex++;
                bitOffset = 0;
            }
        }

        return words;
    }

    // -------------------------------------------------------------------------
    // Unpack
    // -------------------------------------------------------------------------

    /**
     * Unpacks {@code entryCount} values from {@code words}, each using
     * {@code bitsPerEntry} bits.
     *
     * <p>
     * Mirrors the packing layout: values are read from LSB, entries that start at a
     * word
     * boundary don't span. Any trailing padding bits (set to 0 by writers) are
     * ignored.
     *
     * @param words        the packed {@code long} array
     * @param entryCount   number of values to unpack
     * @param bitsPerEntry bits per entry; must be in [1, 64]
     * @return an {@code int[]} of length {@code entryCount} containing the unpacked
     *         values
     * @throws IllegalArgumentException if {@code bitsPerEntry} is not in [1, 64]
     */
    public static int[] unpack(long[] words, int entryCount, int bitsPerEntry) {
        if (bitsPerEntry < 1 || bitsPerEntry > 64) {
            throw new IllegalArgumentException(
                    "bitsPerEntry must be in [1, 64], got " + bitsPerEntry);
        }
        int[] result = new int[entryCount];
        if (entryCount == 0) {
            return result;
        }

        long mask = bitsPerEntry == 64 ? -1L : (1L << bitsPerEntry) - 1L;
        int wordIndex = 0;
        int bitOffset = 0;

        for (int i = 0; i < entryCount; i++) {
            // Would this entry span a word boundary?
            if (bitOffset + bitsPerEntry > 64) {
                wordIndex++;
                bitOffset = 0;
            }
            long v = (words[wordIndex] >>> bitOffset) & mask;
            result[i] = (int) v;
            bitOffset += bitsPerEntry;
            if (bitOffset == 64) {
                wordIndex++;
                bitOffset = 0;
            }
        }

        return result;
    }
}

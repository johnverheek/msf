package dev.msf.core;

import dev.msf.core.codec.BitPackedArray;
import dev.msf.core.codec.BlockDataCodec;
import dev.msf.core.util.YzxOrder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BitPackedArray}, {@link BlockDataCodec}, and
 * {@link YzxOrder}.
 */
class BitPackingAndOrderTest {

    // =========================================================================
    // BitPackedArray — bitsPerEntry formula
    // =========================================================================

    @Test
    void bitsPerEntry_paletteSize1_returns1() {
        assertEquals(1, BitPackedArray.bitsPerEntry(1));
    }

    @Test
    void bitsPerEntry_paletteSize2_returns1() {
        assertEquals(1, BitPackedArray.bitsPerEntry(2));
    }

    @Test
    void bitsPerEntry_paletteSize3_returns2() {
        assertEquals(2, BitPackedArray.bitsPerEntry(3));
    }

    @Test
    void bitsPerEntry_paletteSize4_returns2() {
        assertEquals(2, BitPackedArray.bitsPerEntry(4));
    }

    @Test
    void bitsPerEntry_paletteSize5_returns3() {
        assertEquals(3, BitPackedArray.bitsPerEntry(5));
    }

    @Test
    void bitsPerEntry_paletteSize65535_returns16() {
        // 2^15 = 32768 < 65535 ≤ 2^16 = 65536 → 16 bits
        assertEquals(16, BitPackedArray.bitsPerEntry(65535));
    }

    @Test
    void bitsPerEntry_paletteSize32768_returns15() {
        // Exactly 2^15; ceil(log2(32768)) = 15
        assertEquals(15, BitPackedArray.bitsPerEntry(32768));
    }

    @Test
    void bitsPerEntry_paletteSize32769_returns16() {
        assertEquals(16, BitPackedArray.bitsPerEntry(32769));
    }

    @Test
    void bitsPerEntry_lessThan1_throws() {
        assertThrows(IllegalArgumentException.class, () -> BitPackedArray.bitsPerEntry(0));
        assertThrows(IllegalArgumentException.class, () -> BitPackedArray.bitsPerEntry(-1));
    }

    // =========================================================================
    // BitPackedArray — pack/unpack round trip
    // =========================================================================

    @Test
    void packUnpack_1bit_allZeros() {
        int[] values = new int[64];
        long[] words = BitPackedArray.pack(values, 1);
        int[] unpacked = BitPackedArray.unpack(words, values.length, 1);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_1bit_alternating() {
        int[] values = new int[64];
        for (int i = 0; i < values.length; i++)
            values[i] = i & 1;
        long[] words = BitPackedArray.pack(values, 1);
        int[] unpacked = BitPackedArray.unpack(words, values.length, 1);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_15bits_boundaryValues() {
        // 15 bits: max value = 2^15 - 1 = 32767
        int maxVal = 32767;
        int[] values = { 0, 1, maxVal, maxVal - 1, 0, 12345, 1 };
        long[] words = BitPackedArray.pack(values, 15);
        int[] unpacked = BitPackedArray.unpack(words, values.length, 15);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_16bits_boundaryValues() {
        // 16 bits: max value = 65535
        int maxVal = 65535;
        int[] values = { 0, 1, maxVal, maxVal - 1, 32768, 0 };
        long[] words = BitPackedArray.pack(values, 16);
        int[] unpacked = BitPackedArray.unpack(words, values.length, 16);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_noSpanning_entriesStartInNextWord() {
        // With 5 bits per entry: 64/5 = 12 entries per word (remaining 4 bits unused).
        // Entry at index 12 must start in the next word.
        int bpe = 5;
        int count = 13; // crosses first word boundary
        int[] values = new int[count];
        for (int i = 0; i < count; i++)
            values[i] = i % 31;
        long[] words = BitPackedArray.pack(values, bpe);
        int[] unpacked = BitPackedArray.unpack(words, count, bpe);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_wordBoundaryAtExactFit() {
        // 4 bits per entry: 16 entries per word, exactly fills one 64-bit word
        int bpe = 4;
        int[] values = new int[16];
        for (int i = 0; i < 16; i++)
            values[i] = i; // 0..15, each fits in 4 bits
        long[] words = BitPackedArray.pack(values, bpe);
        assertEquals(1, words.length, "Exactly 1 word for 16 entries × 4 bits");
        int[] unpacked = BitPackedArray.unpack(words, values.length, bpe);
        assertArrayEquals(values, unpacked);
    }

    @Test
    void packUnpack_emptyArray() {
        int[] empty = new int[0];
        long[] words = BitPackedArray.pack(empty, 1);
        assertEquals(0, words.length);
        int[] unpacked = BitPackedArray.unpack(words, 0, 1);
        assertEquals(0, unpacked.length);
    }

    // =========================================================================
    // BitPackedArray — wordsRequired formula (no-spanning)
    // =========================================================================

    @Test
    void wordsRequired_1bit_64entries_is64() {
        // Each entry uses 1 bit; 64 entries fit in 1 word × 64 = 64 → but 64 entries /
        // (64/1 = 64 entries per word) = ceil(64/64) = 1
        assertEquals(1, BitPackedArray.wordsRequired(64, 1));
    }

    @Test
    void wordsRequired_5bits_12entries_is1() {
        // 64/5 = 12 entries per word; 12 entries → ceil(12/12) = 1
        assertEquals(1, BitPackedArray.wordsRequired(12, 5));
    }

    @Test
    void wordsRequired_5bits_13entries_is2() {
        // 13 entries; 12 per word → ceil(13/12) = 2
        assertEquals(2, BitPackedArray.wordsRequired(13, 5));
    }

    @Test
    void wordsRequired_16bits_4entries_is1() {
        // 64/16 = 4 entries per word; 4 entries → 1 word
        assertEquals(1, BitPackedArray.wordsRequired(4, 16));
    }

    @Test
    void wordsRequired_16bits_5entries_is2() {
        // 5 entries; 4 per word → 2 words
        assertEquals(2, BitPackedArray.wordsRequired(5, 16));
    }

    @Test
    void wordsRequired_longArithmetic_noOverflow() {
        // Large region: 65535 × 65535 × 65535 × 16 bits — must not overflow int
        // arithmetic
        // This is used for the packed array length formula (Section 7.5)
        long sizeX = 4096, sizeY = 4096, sizeZ = 4096;
        long entryCount = sizeX * sizeY * sizeZ; // 68 billion — exceeds int max
        long result = BitPackedArray.wordsRequired(entryCount, 16);
        assertTrue(result > 0, "wordsRequired must return a positive value for large regions");
        // Expected: entryCount / (64/16) = entryCount / 4
        long expected = (entryCount + 3) / 4;
        assertEquals(expected, result);
    }

    // =========================================================================
    // BlockDataCodec — encode/decode round trip
    // =========================================================================

    @Test
    void blockDataCodec_roundTrip_smallPalette() throws Exception {
        // palette size 2 → 1 bit per entry
        int[] paletteIds = { 0, 1, 0, 1, 0, 0, 1, 1 };
        byte[] encoded = BlockDataCodec.encode(paletteIds, 2);
        ByteBuffer buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int[] decoded = BlockDataCodec.decode(buf, paletteIds.length, 2);
        assertArrayEquals(paletteIds, decoded);
    }

    @Test
    void blockDataCodec_roundTrip_largePalette() throws Exception {
        // palette size 256 → 8 bits per entry
        int[] paletteIds = new int[100];
        for (int i = 0; i < paletteIds.length; i++)
            paletteIds[i] = i % 256;
        byte[] encoded = BlockDataCodec.encode(paletteIds, 256);
        ByteBuffer buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int[] decoded = BlockDataCodec.decode(buf, paletteIds.length, 256);
        assertArrayEquals(paletteIds, decoded);
    }

    @Test
    void blockDataCodec_decode_outOfRangePaletteId_throwsMsfParseException() {
        // Encode with palette size 4, then decode claiming only 2 entries (so ID 2 is
        // out of range)
        int[] paletteIds = { 0, 1, 2, 3 };
        byte[] encoded = BlockDataCodec.encode(paletteIds, 4);
        ByteBuffer buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        // Decode with paletteSize=2 — IDs 2 and 3 are out of range
        assertThrows(MsfParseException.class, () -> BlockDataCodec.decode(buf, paletteIds.length, 2));
    }

    @Test
    void blockDataCodec_decode_bitsPerEntry0_throwsMsfParseException() {
        // Manually craft bytes with bits_per_entry = 0
        byte[] bytes = { 0x00, 0x01, 0x00, 0x00, 0x00 }; // bpe=0, wordCount=1 (bogus)
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(MsfParseException.class, () -> BlockDataCodec.decode(buf, 1, 2));
    }

    @Test
    void blockDataCodec_decode_wordCountMismatch_throwsMsfParseException() {
        // Encode 4 entries with palette size 2 (1 bit each → 1 word)
        int[] paletteIds = { 0, 1, 0, 1 };
        byte[] encoded = BlockDataCodec.encode(paletteIds, 2);
        // Corrupt the packed_array_length field (bytes 1–4) to claim 2 words instead of
        // 1
        java.nio.ByteBuffer corrupt = java.nio.ByteBuffer.wrap(encoded).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        corrupt.putInt(1, 2); // write 2 at offset 1 (packed_array_length)
        ByteBuffer buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(MsfParseException.class, () -> BlockDataCodec.decode(buf, paletteIds.length, 2));
    }

    // =========================================================================
    // YzxOrder
    // =========================================================================

    @Test
    void yzxOrder_origin_isIndex0() {
        assertEquals(0L, YzxOrder.index(0, 0, 0, 10, 10));
    }

    @Test
    void yzxOrder_xIncrement_incrementsIndexBy1() {
        // index(1,0,0) = index(0,0,0) + 1 (X is innermost)
        assertEquals(1L, YzxOrder.index(1, 0, 0, 10, 10));
    }

    @Test
    void yzxOrder_zIncrement_incrementsIndexBySizeX() {
        // index(0,0,1) = sizeX (Z is middle)
        int sizeX = 7, sizeZ = 5;
        assertEquals((long) sizeX, YzxOrder.index(0, 0, 1, sizeX, sizeZ));
    }

    @Test
    void yzxOrder_yIncrement_incrementsIndexBySizeZxSizeX() {
        // index(0,1,0) = sizeZ * sizeX (Y is outermost)
        int sizeX = 7, sizeZ = 5;
        assertEquals((long) sizeZ * sizeX, YzxOrder.index(0, 1, 0, sizeX, sizeZ));
    }

    @Test
    void yzxOrder_formula_knownCoordinate() {
        // For region 4×3×2 (X=4, Y=3, Z=2):
        // index(x=2, y=1, z=1) = 1*(2*4) + 1*4 + 2 = 8 + 4 + 2 = 14
        assertEquals(14L, YzxOrder.index(2, 1, 1, 4, 2));
    }

    @Test
    void yzxOrder_blockCount_usesLongArithmetic() {
        // Large region: should not overflow int
        long count = YzxOrder.blockCount(65535, 65535, 2);
        assertEquals(65535L * 65535 * 2, count);
    }

    @Test
    void yzxOrder_lastIndex_equalsBlockCountMinus1() {
        int sizeX = 5, sizeY = 4, sizeZ = 3;
        long expected = YzxOrder.blockCount(sizeX, sizeY, sizeZ) - 1;
        assertEquals(expected, YzxOrder.index(sizeX - 1, sizeY - 1, sizeZ - 1, sizeX, sizeZ));
    }
}

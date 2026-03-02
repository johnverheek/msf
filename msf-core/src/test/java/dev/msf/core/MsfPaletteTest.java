package dev.msf.core;

import dev.msf.core.model.MsfPalette;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MsfPalette} — read/write round trip, deduplication,
 * palette ID 0 invariant, and entry count overflow.
 */
class MsfPaletteTest {

    // -------------------------------------------------------------------------
    // Round trip
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_singleAirEntry() throws Exception {
        MsfPalette original = MsfPalette.of(List.of());
        byte[] bytes = original.toBytes();
        MsfPalette parsed = MsfPalette.fromBytes(bytes, 0);

        assertEquals(1, parsed.entries().size());
        assertEquals(MsfPalette.AIR, parsed.entries().get(0));
    }

    @Test
    void roundTrip_multipleEntries() throws Exception {
        List<String> blockstates = List.of(
            "minecraft:stone",
            "minecraft:oak_planks",
            "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
        );
        MsfPalette original = MsfPalette.of(blockstates);
        byte[] bytes = original.toBytes();
        MsfPalette parsed = MsfPalette.fromBytes(bytes, 0);

        assertEquals(original.entries(), parsed.entries());
    }

    @Test
    void roundTrip_preservesOrder() throws Exception {
        // Entries must appear in exactly the order written
        List<String> blockstates = List.of(
            "minecraft:stone",
            "minecraft:grass_block[snowy=false]",
            "minecraft:sand"
        );
        MsfPalette original = MsfPalette.of(blockstates);
        byte[] bytes = original.toBytes();
        MsfPalette parsed = MsfPalette.fromBytes(bytes, 0);

        for (int i = 0; i < original.entries().size(); i++) {
            assertEquals(original.entries().get(i), parsed.entries().get(i),
                "Entry at index " + i + " should match");
        }
    }

    // -------------------------------------------------------------------------
    // Palette ID 0 is always minecraft:air
    // -------------------------------------------------------------------------

    @Test
    void paletteId0_isAlwaysAir_whenAirNotProvided() throws Exception {
        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone"));
        assertEquals(MsfPalette.AIR, palette.entries().get(0));
        assertEquals("minecraft:stone", palette.entries().get(1));
    }

    @Test
    void paletteId0_isAlwaysAir_whenAirProvidedFirst() throws Exception {
        MsfPalette palette = MsfPalette.of(List.of(MsfPalette.AIR, "minecraft:stone"));
        assertEquals(MsfPalette.AIR, palette.entries().get(0));
        assertEquals(2, palette.entries().size());
    }

    @Test
    void paletteId0_forcedToAirOnRead() throws Exception {
        // Even if a non-conforming file has something else at index 0,
        // the spec says palette ID 0 is treated as air.
        // The read path currently reads the string and uses it as-is for index 0 (overriding to AIR).
        MsfPalette original = MsfPalette.of(List.of("minecraft:stone"));
        byte[] bytes = original.toBytes();
        MsfPalette parsed = MsfPalette.fromBytes(bytes, 0);
        assertEquals(MsfPalette.AIR, parsed.entries().get(0));
    }

    @Test
    void idOf_returnsCorrectIndex() throws Exception {
        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone", "minecraft:oak_planks"));
        assertEquals(0, palette.idOf(MsfPalette.AIR));
        assertEquals(1, palette.idOf("minecraft:stone"));
        assertEquals(2, palette.idOf("minecraft:oak_planks"));
        assertEquals(-1, palette.idOf("minecraft:nonexistent"));
    }

    // -------------------------------------------------------------------------
    // Deduplication
    // -------------------------------------------------------------------------

    @Test
    void write_throwsMsfPaletteException_onDuplicateEntry() {
        // Provide duplicate entries via of() — should throw MsfPaletteException
        assertThrows(MsfPaletteException.class, () ->
            MsfPalette.of(List.of("minecraft:stone", "minecraft:stone"))
        );
    }

    @Test
    void write_throwsMsfPaletteException_onDuplicateAir() {
        // If the caller explicitly provides AIR as a non-first entry alongside the auto-prepended AIR
        // This would result in a duplicate after prepending
        assertThrows(MsfPaletteException.class, () ->
            MsfPalette.of(List.of("minecraft:stone", MsfPalette.AIR))
        );
    }

    @Test
    void read_throwsMsfParseException_onDuplicateEntryInFile() throws Exception {
        // Build a palette with a duplicate entry by manually constructing the bytes
        // Layout: u32 blockLength | u16 entryCount | [u16 len + bytes] per entry
        // We'll write: air, stone, stone
        byte[] air = MsfPalette.AIR.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] stone = "minecraft:stone".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(512).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int entryCount = 3; // air, stone, stone (duplicate)
        // Compute body size: 2 (count) + (2+air.length) + (2+stone.length) + (2+stone.length)
        int bodySize = 2 + (2 + air.length) + (2 + stone.length) + (2 + stone.length);
        buf.putInt(bodySize);       // block_length
        buf.putShort((short) entryCount);
        buf.putShort((short) air.length);
        buf.put(air);
        buf.putShort((short) stone.length);
        buf.put(stone);
        buf.putShort((short) stone.length); // duplicate
        buf.put(stone);

        byte[] bytes = new byte[4 + bodySize];
        buf.flip();
        buf.get(bytes);

        assertThrows(MsfParseException.class, () -> MsfPalette.fromBytes(bytes, 0));
    }

    // -------------------------------------------------------------------------
    // Entry count overflow
    // -------------------------------------------------------------------------

    @Test
    void entryCountOverflow_throwsIllegalArgumentException() {
        // Create a list with MAX_ENTRIES + 1 entries (including the auto-prepended air)
        // air gets prepended making total MAX_ENTRIES + 1 (if we provide MAX_ENTRIES non-air entries)
        // Actually we need to exceed 65535 total. Provide 65535 non-air entries to make 65536 total.
        // This is too many to construct literally; test the validation in toBytes() by
        // constructing a palette record with 65536 entries via the record constructor
        // (bypassing of() which also validates).
        // Actually the cleanest approach: provide exactly MAX_ENTRIES entries to of(),
        // then include one more to exceed.
        // We'll create a synthetic list of 65535 blockstate strings (air is 0th, 1..65535 are unique)
        // That's too slow. Instead just verify the constant and that the check fires at 65535 + 1.
        // We mock the call: verify that of() with a large list (all unique) with count > 65535 throws.
        // 65536 entries total (including auto-prepended air) = 65535 provided non-air entries + air
        // → at 65535 non-air provided entries, total becomes 65536 which exceeds MAX_ENTRIES (65535)
        java.util.List<String> manyEntries = new java.util.ArrayList<>();
        for (int i = 0; i < MsfPalette.MAX_ENTRIES; i++) { // 65535 non-air entries
            manyEntries.add("minecraft:stone_" + i);
        }
        // Total would be 65536 (air + 65535 others)
        assertThrows(IllegalArgumentException.class, () -> MsfPalette.of(manyEntries));
    }

    // -------------------------------------------------------------------------
    // Block length skip field
    // -------------------------------------------------------------------------

    @Test
    void blockLength_allowsSkipping() throws Exception {
        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone"));
        byte[] bytes = palette.toBytes();

        // The block_length field at offset 0 should equal total bytes minus 4
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long blockLength = Integer.toUnsignedLong(buf.getInt());
        assertEquals(bytes.length - 4, blockLength);
    }
}

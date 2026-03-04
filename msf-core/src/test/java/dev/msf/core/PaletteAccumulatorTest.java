package dev.msf.core;

import dev.msf.core.model.MsfPalette;
import dev.msf.core.util.PaletteAccumulator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PaletteAccumulator}.
 *
 * <p>All tests run as plain JUnit — no Minecraft environment required.
 */
class PaletteAccumulatorTest {

    // =========================================================================
    // Initial state
    // =========================================================================

    @Test
    void newAccumulator_airIsAtIdZero() {
        PaletteAccumulator acc = new PaletteAccumulator();
        MsfPalette palette = acc.build();
        assertEquals(MsfPalette.AIR, palette.entries().get(0));
    }

    @Test
    void newAccumulator_sizeIsOne() {
        PaletteAccumulator acc = new PaletteAccumulator();
        assertEquals(1, acc.build().entries().size());
    }

    // =========================================================================
    // add()
    // =========================================================================

    @Test
    void add_airReturnsIdZero() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        assertEquals(0, acc.add(MsfPalette.AIR));
    }

    @Test
    void add_newEntryGetsNextId() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int id = acc.add("minecraft:stone");
        assertEquals(1, id);
    }

    @Test
    void add_duplicateEntryReturnsSameId() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int first  = acc.add("minecraft:stone");
        int second = acc.add("minecraft:stone");
        assertEquals(first, second);
    }

    @Test
    void add_multipleDistinctEntriesGetSequentialIds() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int stone = acc.add("minecraft:stone");
        int grass = acc.add("minecraft:grass_block");
        int oak   = acc.add("minecraft:oak_log");
        assertEquals(1, stone);
        assertEquals(2, grass);
        assertEquals(3, oak);
    }

    @Test
    void add_doesNotDuplicateAirWhenAddedExplicitly() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        acc.add(MsfPalette.AIR); // air already seeded at construction
        MsfPalette palette = acc.build();
        assertEquals(1, palette.entries().size(), "Air must not be duplicated");
    }

    // =========================================================================
    // build()
    // =========================================================================

    @Test
    void build_returnsSnapshotUnaffectedByLaterAdds() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        acc.add("minecraft:stone");
        MsfPalette snapshot = acc.build();
        acc.add("minecraft:grass_block");
        assertEquals(2, snapshot.entries().size(), "Snapshot must not reflect later additions");
        assertEquals(3, acc.build().entries().size());
    }

    @Test
    void build_airisAlwaysIndexZero() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        acc.add("minecraft:stone");
        acc.add("minecraft:oak_log");
        assertEquals(MsfPalette.AIR, acc.build().entries().get(0));
    }

    // =========================================================================
    // remapTable()
    // =========================================================================

    @Test
    void remapTable_localAirMapsToGlobalZero() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int[] remap = acc.remapTable(List.of(MsfPalette.AIR, "minecraft:stone"));
        assertEquals(0, remap[0], "Local palette ID 0 (air) must map to global ID 0");
    }

    @Test
    void remapTable_localNonAirMapsToCorrectGlobalId() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int[] remap = acc.remapTable(List.of(MsfPalette.AIR, "minecraft:stone"));
        assertEquals(1, remap[1]);
    }

    @Test
    void remapTable_singleEntryLocalPalette_airOnly() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        int[] remap = acc.remapTable(List.of(MsfPalette.AIR));
        assertEquals(1, remap.length);
        assertEquals(0, remap[0]);
    }

    @Test
    void remapTable_deduplicatesAcrossTwoLocalPalettes() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();

        // First region: [air, stone, oak_log]
        int[] remap1 = acc.remapTable(List.of(MsfPalette.AIR, "minecraft:stone", "minecraft:oak_log"));
        // Second region: [air, stone, grass_block] — stone already in global palette
        int[] remap2 = acc.remapTable(List.of(MsfPalette.AIR, "minecraft:stone", "minecraft:grass_block"));

        // Global palette: [air(0), stone(1), oak_log(2), grass_block(3)]
        assertEquals(0, remap1[0]); // air → 0
        assertEquals(1, remap1[1]); // stone → 1
        assertEquals(2, remap1[2]); // oak_log → 2
        assertEquals(0, remap2[0]); // air → 0
        assertEquals(1, remap2[1]); // stone → 1 (deduplicated)
        assertEquals(3, remap2[2]); // grass_block → 3

        assertEquals(4, acc.build().entries().size());
    }

    @Test
    void remapTable_tableLengthMatchesLocalPaletteSize() throws MsfPaletteException {
        PaletteAccumulator acc = new PaletteAccumulator();
        List<String> local = List.of(MsfPalette.AIR, "minecraft:stone", "minecraft:oak_log");
        int[] remap = acc.remapTable(local);
        assertEquals(local.size(), remap.length);
    }

    // =========================================================================
    // Overflow guard
    // =========================================================================

    @Test
    void add_throwsWhenExceedingMaxEntries() {
        PaletteAccumulator acc = new PaletteAccumulator();
        // Fill to MAX_ENTRIES (65535)
        assertDoesNotThrow(() -> {
            for (int i = 1; i < MsfPalette.MAX_ENTRIES; i++) {
                acc.add("minecraft:block_" + i);
            }
        });
        // The next add must throw
        assertThrows(MsfPaletteException.class, () -> acc.add("minecraft:overflow_block"));
    }
}

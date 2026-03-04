package dev.msf.core.util;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfPalette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates blockstate strings from one or more independently-encoded local
 * palettes into a single deduplicated global palette.
 *
 * <h2>Usage pattern — multi-region file</h2>
 * <pre>{@code
 * PaletteAccumulator acc = new PaletteAccumulator();
 *
 * // For each region extracted by RegionExtractor, remap its local palette IDs
 * // to global IDs before building the MsfRegion.
 * int[] remap = acc.remapTable(localPalette);  // e.g. ["minecraft:air", "minecraft:stone"]
 * int[] globalBlockData = Arrays.stream(localBlockData)
 *     .map(id -> remap[id])
 *     .toArray();
 *
 * MsfPalette globalPalette = acc.build();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Not thread-safe. Use from a single thread.
 *
 * <h2>Air invariant</h2>
 * {@link MsfPalette#AIR} ({@code "minecraft:air"}) is always at global ID 0.
 * The first call to {@link #add} or {@link #remapTable} initialises the accumulator
 * with air at index 0. Local palette ID 0 always maps to global ID 0.
 */
public final class PaletteAccumulator {

    /** Global entries in insertion order; index = global palette ID. */
    private final List<String> entries = new ArrayList<>();

    /** Reverse-lookup: blockstate string → global palette ID. */
    private final Map<String, Integer> index = new HashMap<>();

    /**
     * Constructs a new accumulator pre-seeded with {@link MsfPalette#AIR} at global ID 0.
     */
    public PaletteAccumulator() {
        // Always seed with air so that ID 0 == "minecraft:air" is guaranteed.
        insertUnchecked(MsfPalette.AIR);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Adds a single blockstate string to the global palette if not already present.
     *
     * @param blockstate the blockstate string to add
     * @return the global palette ID for {@code blockstate}
     * @throws MsfPaletteException if adding the entry would exceed
     *                             {@link MsfPalette#MAX_ENTRIES} (65535)
     */
    public int add(String blockstate) throws MsfPaletteException {
        Integer existing = index.get(blockstate);
        if (existing != null) {
            return existing;
        }
        if (entries.size() >= MsfPalette.MAX_ENTRIES) {
            throw new MsfPaletteException(
                "Global palette would exceed the maximum of " + MsfPalette.MAX_ENTRIES + " entries "
                + "while adding: \"" + blockstate + "\""
            );
        }
        return insertUnchecked(blockstate);
    }

    /**
     * Builds a remap table that maps local palette IDs (indices into
     * {@code localEntries}) to global palette IDs in this accumulator.
     *
     * <p>All entries in {@code localEntries} are added to the accumulator via
     * {@link #add}, so after this call the accumulator contains the union of its
     * previous state and every entry in {@code localEntries}.
     *
     * <p>Local palette ID 0 ({@code "minecraft:air"}) always maps to global ID 0.
     *
     * @param localEntries the local palette entries in ID order; must have
     *                     {@code "minecraft:air"} at index 0 (Section 4.3)
     * @return an {@code int[]} of length {@code localEntries.size()} where
     *         {@code result[localId] == globalId}
     * @throws MsfPaletteException if adding entries would exceed the 65535 limit
     */
    public int[] remapTable(List<String> localEntries) throws MsfPaletteException {
        int[] table = new int[localEntries.size()];
        for (int i = 0; i < localEntries.size(); i++) {
            table[i] = add(localEntries.get(i));
        }
        return table;
    }

    /**
     * Builds an {@link MsfPalette} from all entries accumulated so far.
     *
     * <p>The returned palette is a snapshot; subsequent calls to {@link #add} or
     * {@link #remapTable} do not affect it. May be called multiple times.
     *
     * @return a new {@link MsfPalette} with {@code "minecraft:air"} at index 0
     */
    public MsfPalette build() {
        // MsfPalette constructor makes a defensive copy of the list.
        // entries already starts with "minecraft:air" so MsfPalette.of() is not needed.
        return new MsfPalette(entries);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Inserts without overflow check; caller is responsible for the check. */
    private int insertUnchecked(String blockstate) {
        int id = entries.size();
        entries.add(blockstate);
        index.put(blockstate, id);
        return id;
    }
}

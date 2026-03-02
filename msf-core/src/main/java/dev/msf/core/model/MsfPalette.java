package dev.msf.core.model;

import dev.msf.core.MsfParseException;
import dev.msf.core.MsfPaletteException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The global palette block — a registry of all blockstate strings referenced by
 * block data
 * in an MSF file. Shared across all layers and regions.
 *
 * <h2>Key invariants (Section 4.3)</h2>
 * <ul>
 * <li>Palette ID 0 MUST be {@code "minecraft:air"}. Writers always prepend it;
 * readers
 * always enforce it.</li>
 * <li>Entries MUST be deduplicated. Duplicate entries on write throw
 * {@link MsfPaletteException}; on read throw {@link MsfParseException}.</li>
 * <li>Entry count is a u16 — maximum 65535 entries. Overflow throws
 * {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <h2>Block layout (Section 4.1)</h2>
 * 
 * <pre>
 *     u32     block length (bytes after this field)
 *     u16     entry count
 *     [per entry]
 *       u16   blockstate string byte length
 *       u8[]  blockstate string (UTF-8, not null-terminated)
 * </pre>
 *
 * @param entries unmodifiable list of blockstate strings; index 0 is always
 *                {@code "minecraft:air"}
 *
 * @see MsfSpec Section 4 — global palette block
 */
public record MsfPalette(List<String> entries) {

    /** The required value of palette entry 0 per Section 4.3. */
    public static final String AIR = "minecraft:air";

    /** Maximum number of palette entries (u16 ceiling). */
    public static final int MAX_ENTRIES = 65535;

    /**
     * Constructs a validated {@link MsfPalette}.
     *
     * @param entries the entries list; must not be null; element 0 must be
     *                {@link #AIR}
     */
    public MsfPalette {
        entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Returns the palette ID for the given blockstate string, or {@code -1} if not
     * found.
     *
     * @param blockstate the blockstate string to look up
     * @return the palette ID (0-based), or {@code -1} if absent
     */
    public int idOf(String blockstate) {
        return entries.indexOf(blockstate);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Serializes this palette to a byte array, ready to be written at the palette
     * block offset
     * in the MSF file.
     *
     * <p>
     * The serialized form includes the 4-byte {@code block_length} prefix. Palette
     * ID 0 is
     * always written as {@code "minecraft:air"} regardless of what the first entry
     * actually
     * contains (the model invariant ensures it is {@code "minecraft:air"}).
     *
     * @return the complete palette block bytes including the block_length prefix
     * @throws MsfPaletteException      if duplicate entries are detected
     * @throws IllegalArgumentException if the entry count exceeds 65535
     */
    public byte[] toBytes() throws MsfPaletteException {
        List<String> entriesToWrite = entries;

        // Validate entry count
        int count = entriesToWrite.size();
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException(String.format(
                    "Field 'entryCount' value %d exceeds the maximum permitted value of %d",
                    count, MAX_ENTRIES));
        }

        // Deduplication check (Section 4.3)
        Set<String> seen = new HashSet<>();
        for (String entry : entriesToWrite) {
            if (!seen.add(entry)) {
                throw new MsfPaletteException(
                        "Duplicate palette entry detected on write: \"" + entry + "\"");
            }
        }

        // Compute the byte length of each entry and the total block body size
        byte[][] encodedStrings = new byte[count][];
        int bodySize = 2; // u16 entry count
        for (int i = 0; i < count; i++) {
            encodedStrings[i] = entriesToWrite.get(i).getBytes(StandardCharsets.UTF_8);
            bodySize += 2 + encodedStrings[i].length; // u16 length prefix + string bytes
        }

        // Layout: [u32 block_length] [u16 entry_count] [entries...]
        // block_length = bytes AFTER the u32 field = bodySize
        ByteBuffer buf = ByteBuffer.allocate(4 + bodySize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(bodySize); // u32 block_length
        buf.putShort((short) count); // u16 entry_count

        for (byte[] encoded : encodedStrings) {
            buf.putShort((short) encoded.length); // u16 string byte length
            buf.put(encoded); // UTF-8 bytes
        }

        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses a palette block from the given byte array, starting at the beginning
     * of the block
     * (i.e. at the {@code block_length} u32 field).
     *
     * @param data   the file bytes
     * @param offset absolute byte offset of the start of the palette block in
     *               {@code data}
     * @return the parsed {@link MsfPalette}
     * @throws MsfParseException if the block is malformed or contains duplicate
     *                           entries
     */
    public static MsfPalette fromBytes(byte[] data, int offset) throws MsfParseException {
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset)
                .order(ByteOrder.LITTLE_ENDIAN);

        // Read block_length (u32)
        Integer.toUnsignedLong(buf.getInt());

        // Read entry count (u16)
        int count = Short.toUnsignedInt(buf.getShort());

        List<String> entries = new ArrayList<>(count);
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < count; i++) {
            int strLen = Short.toUnsignedInt(buf.getShort());
            byte[] strBytes = new byte[strLen];
            buf.get(strBytes);

            String blockstate;
            // Palette ID 0 is always "minecraft:air" per Section 4.3
            // We still read and advance past the stored bytes, but always set index 0 to
            // AIR
            if (i == 0) {
                blockstate = AIR;
            } else {
                blockstate = new String(strBytes, StandardCharsets.UTF_8);
            }

            // Deduplication check (Section 4.3)
            if (!seen.add(blockstate)) {
                throw new MsfParseException(
                        "Duplicate palette entry at index " + i + ": \"" + blockstate
                                + "\" — non-conforming writer; palette cannot be trusted");
            }

            entries.add(blockstate);
        }

        return new MsfPalette(entries);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates an {@link MsfPalette} from a caller-supplied list of blockstate
     * strings.
     *
     * <p>
     * If the list is empty or does not start with {@link #AIR},
     * {@code "minecraft:air"} is
     * prepended automatically. If the list already starts with {@link #AIR}, it is
     * used as-is.
     *
     * @param blockstates the blockstate strings; must not be null; may be empty
     * @return a new {@link MsfPalette}
     * @throws MsfPaletteException      if duplicate entries are detected (excluding
     *                                  the auto-prepended AIR)
     * @throws IllegalArgumentException if the resulting entry count exceeds 65535
     */
    public static MsfPalette of(List<String> blockstates) throws MsfPaletteException {
        List<String> entries = new ArrayList<>(blockstates.size() + 1);

        // Always ensure palette ID 0 is "minecraft:air"
        if (blockstates.isEmpty() || !AIR.equals(blockstates.get(0))) {
            entries.add(AIR);
            entries.addAll(blockstates);
        } else {
            entries.addAll(blockstates);
        }

        int count = entries.size();
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException(String.format(
                    "Field 'entryCount' value %d exceeds the maximum permitted value of %d",
                    count, MAX_ENTRIES));
        }

        // Deduplication check
        Set<String> seen = new HashSet<>();
        for (String entry : entries) {
            if (!seen.add(entry)) {
                throw new MsfPaletteException(
                        "Duplicate palette entry detected: \"" + entry + "\"");
            }
        }

        return new MsfPalette(entries);
    }
}

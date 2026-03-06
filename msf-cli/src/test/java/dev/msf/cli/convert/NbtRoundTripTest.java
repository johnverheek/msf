package dev.msf.cli.convert;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link NbtReader} and {@link NbtWriter}.
 */
class NbtRoundTripTest {

    // =========================================================================
    // Test 1: gzip round trip — write then read back a compound with all tag types
    // =========================================================================

    @Test
    void gzip_roundTrip_allTagTypes() throws Exception {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put("byteVal",      new NbtTag.ByteTag((byte) 42));
        entries.put("shortVal",     new NbtTag.ShortTag((short) 1234));
        entries.put("intVal",       new NbtTag.IntTag(100_000));
        entries.put("longVal",      new NbtTag.LongTag(Long.MAX_VALUE));
        entries.put("floatVal",     new NbtTag.FloatTag(3.14f));
        entries.put("doubleVal",    new NbtTag.DoubleTag(2.718281828));
        entries.put("byteArray",    new NbtTag.ByteArrayTag(new byte[]{1, 2, 3}));
        entries.put("strVal",       new NbtTag.StringTag("hello world"));
        entries.put("intArray",     new NbtTag.IntArrayTag(new int[]{10, 20, 30}));
        entries.put("longArray",    new NbtTag.LongArrayTag(new long[]{100L, 200L}));
        entries.put("nested",       new NbtTag.CompoundTag(Map.of("inner", new NbtTag.IntTag(7))));
        entries.put("intList",      new NbtTag.ListTag(NbtTag.TYPE_INT,
                List.of(new NbtTag.IntTag(1), new NbtTag.IntTag(2), new NbtTag.IntTag(3))));

        NbtTag.CompoundTag original = new NbtTag.CompoundTag(entries);
        byte[] gzipped = NbtWriter.writeCompound(original, "");
        NbtTag.CompoundTag restored = NbtReader.readCompound(gzipped);

        assertEquals((byte) 42,          ((NbtTag.ByteTag)   restored.entries().get("byteVal")).value());
        assertEquals((short) 1234,       ((NbtTag.ShortTag)  restored.entries().get("shortVal")).value());
        assertEquals(100_000,            ((NbtTag.IntTag)    restored.entries().get("intVal")).value());
        assertEquals(Long.MAX_VALUE,     ((NbtTag.LongTag)   restored.entries().get("longVal")).value());
        assertEquals(3.14f,              ((NbtTag.FloatTag)  restored.entries().get("floatVal")).value(), 1e-6f);
        assertEquals(2.718281828,        ((NbtTag.DoubleTag) restored.entries().get("doubleVal")).value(), 1e-12);
        assertArrayEquals(new byte[]{1, 2, 3},
                ((NbtTag.ByteArrayTag) restored.entries().get("byteArray")).value());
        assertEquals("hello world",      ((NbtTag.StringTag) restored.entries().get("strVal")).value());
        assertArrayEquals(new int[]{10, 20, 30},
                ((NbtTag.IntArrayTag)  restored.entries().get("intArray")).value());
        assertArrayEquals(new long[]{100L, 200L},
                ((NbtTag.LongArrayTag) restored.entries().get("longArray")).value());

        NbtTag.CompoundTag nested = (NbtTag.CompoundTag) restored.entries().get("nested");
        assertEquals(7, ((NbtTag.IntTag) nested.entries().get("inner")).value());

        NbtTag.ListTag intList = (NbtTag.ListTag) restored.entries().get("intList");
        assertEquals(NbtTag.TYPE_INT, intList.elementType());
        assertEquals(3, intList.elements().size());
        assertEquals(2, ((NbtTag.IntTag) intList.elements().get(1)).value());
    }

    // =========================================================================
    // Test 2: raw round trip (non-gzip) — write then read back via raw API
    // =========================================================================

    @Test
    void raw_roundTrip_basicCompound() throws Exception {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put("name",  new NbtTag.StringTag("minecraft:chest"));
        entries.put("Items", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        NbtTag.CompoundTag original = new NbtTag.CompoundTag(entries);

        byte[] rawBytes = NbtWriter.writeCompoundRaw(original, "");
        NbtTag.CompoundTag restored = NbtReader.readCompoundRaw(rawBytes);

        assertEquals("minecraft:chest", ((NbtTag.StringTag) restored.entries().get("name")).value());
        NbtTag.ListTag items = (NbtTag.ListTag) restored.entries().get("Items");
        assertEquals(0, items.elements().size());
    }

    // =========================================================================
    // Test 3: readCompoundRaw with empty bytes returns empty compound
    // =========================================================================

    @Test
    void raw_emptyBytes_returnsEmptyCompound() throws Exception {
        NbtTag.CompoundTag result = NbtReader.readCompoundRaw(new byte[0]);
        assertTrue(result.entries().isEmpty());
    }

    // =========================================================================
    // Test 4: nested list of compounds round trips correctly
    // =========================================================================

    @Test
    void gzip_nestedListOfCompounds_roundTrip() throws Exception {
        Map<String, NbtTag> item1 = new LinkedHashMap<>();
        item1.put("id", new NbtTag.StringTag("minecraft:stone"));
        item1.put("count", new NbtTag.ByteTag((byte) 1));
        Map<String, NbtTag> item2 = new LinkedHashMap<>();
        item2.put("id", new NbtTag.StringTag("minecraft:dirt"));
        item2.put("count", new NbtTag.ByteTag((byte) 3));

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("Items", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND,
                List.of(new NbtTag.CompoundTag(item1), new NbtTag.CompoundTag(item2))));
        NbtTag.CompoundTag original = new NbtTag.CompoundTag(root);

        byte[] gzipped = NbtWriter.writeCompound(original, "");
        NbtTag.CompoundTag restored = NbtReader.readCompound(gzipped);

        NbtTag.ListTag items = (NbtTag.ListTag) restored.entries().get("Items");
        assertEquals(2, items.elements().size());
        NbtTag.CompoundTag first = (NbtTag.CompoundTag) items.elements().get(0);
        assertEquals("minecraft:stone", ((NbtTag.StringTag) first.entries().get("id")).value());
        assertEquals((byte) 1, ((NbtTag.ByteTag) first.entries().get("count")).value());
    }
}

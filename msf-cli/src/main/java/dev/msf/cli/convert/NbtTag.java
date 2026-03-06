package dev.msf.cli.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing an NBT tag value.
 *
 * <p>One record implementation per tag type (TAG_Byte through TAG_Long_Array).
 * TAG_End (type 0) is not reified; it is handled implicitly by the reader/writer.
 *
 * <p>All multi-byte integers in NBT are big-endian per the NBT specification.
 */
public sealed interface NbtTag permits
        NbtTag.ByteTag, NbtTag.ShortTag, NbtTag.IntTag, NbtTag.LongTag,
        NbtTag.FloatTag, NbtTag.DoubleTag, NbtTag.ByteArrayTag, NbtTag.StringTag,
        NbtTag.ListTag, NbtTag.CompoundTag, NbtTag.IntArrayTag, NbtTag.LongArrayTag {

    // NBT tag type constants (per the NBT specification)
    int TYPE_BYTE        = 1;
    int TYPE_SHORT       = 2;
    int TYPE_INT         = 3;
    int TYPE_LONG        = 4;
    int TYPE_FLOAT       = 5;
    int TYPE_DOUBLE      = 6;
    int TYPE_BYTE_ARRAY  = 7;
    int TYPE_STRING      = 8;
    int TYPE_LIST        = 9;
    int TYPE_COMPOUND    = 10;
    int TYPE_INT_ARRAY   = 11;
    int TYPE_LONG_ARRAY  = 12;

    record ByteTag(byte value) implements NbtTag {}
    record ShortTag(short value) implements NbtTag {}
    record IntTag(int value) implements NbtTag {}
    record LongTag(long value) implements NbtTag {}
    record FloatTag(float value) implements NbtTag {}
    record DoubleTag(double value) implements NbtTag {}
    record ByteArrayTag(byte[] value) implements NbtTag {}
    record StringTag(String value) implements NbtTag {}

    /** A TAG_List. All elements share the type given by {@code elementType}. */
    record ListTag(int elementType, List<NbtTag> elements) implements NbtTag {}

    /** A TAG_Compound. Entries preserve insertion order via {@link LinkedHashMap}. */
    record CompoundTag(Map<String, NbtTag> entries) implements NbtTag {
        public CompoundTag {
            entries = new LinkedHashMap<>(entries);
        }
    }

    record IntArrayTag(int[] value) implements NbtTag {}
    record LongArrayTag(long[] value) implements NbtTag {}
}

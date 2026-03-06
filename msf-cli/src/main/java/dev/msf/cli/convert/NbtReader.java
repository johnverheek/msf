package dev.msf.cli.convert;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads NBT TAG_Compound data from byte arrays.
 *
 * <p>Two entry points are provided:
 * <ul>
 *   <li>{@link #readCompound} — decompresses and parses a gzip-wrapped TAG_Compound
 *       (used for vanilla {@code .nbt} structure files).</li>
 *   <li>{@link #readCompoundRaw} — parses a raw (non-gzip) TAG_Compound
 *       (used for NBT payloads stored inside MSF blocks).</li>
 * </ul>
 *
 * <p>All multi-byte integers are big-endian per the NBT specification.
 */
public final class NbtReader {

    private NbtReader() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Decompresses and parses a gzip-wrapped root TAG_Compound.
     *
     * @param bytes gzip-compressed NBT bytes (vanilla {@code .nbt} file)
     * @return the root {@link NbtTag.CompoundTag}
     * @throws IOException if decompression or parsing fails
     */
    public static NbtTag.CompoundTag readCompound(byte[] bytes) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
             DataInputStream dis = new DataInputStream(gis)) {
            return readRootCompound(dis);
        }
    }

    /**
     * Parses a raw (non-gzip) root TAG_Compound from a byte array.
     *
     * <p>The bytes must start with {@code 0x0A} (TAG_Compound type byte), followed by
     * a u16 big-endian name length (usually 0x00 0x00), and then the compound content.
     * This is the format used for NBT payloads inside MSF entity and block entity blocks.
     *
     * <p>If {@code bytes} is empty, an empty {@link NbtTag.CompoundTag} is returned.
     *
     * @param bytes raw NBT bytes
     * @return the root {@link NbtTag.CompoundTag}
     * @throws IOException if the bytes are not a valid TAG_Compound
     */
    public static NbtTag.CompoundTag readCompoundRaw(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return new NbtTag.CompoundTag(new LinkedHashMap<>());
        }
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return readRootCompound(dis);
        }
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    private static NbtTag.CompoundTag readRootCompound(DataInputStream dis) throws IOException {
        int type = dis.readUnsignedByte();
        if (type != NbtTag.TYPE_COMPOUND) {
            throw new IOException("Expected TAG_Compound (10) at root, got: " + type);
        }
        readUtf(dis); // discard root name (usually empty)
        return readCompoundPayload(dis);
    }

    private static NbtTag.CompoundTag readCompoundPayload(DataInputStream dis) throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        while (true) {
            int type = dis.readUnsignedByte();
            if (type == 0) break; // TAG_End
            String name = readUtf(dis);
            entries.put(name, readPayload(dis, type));
        }
        return new NbtTag.CompoundTag(entries);
    }

    private static NbtTag readPayload(DataInputStream dis, int type) throws IOException {
        return switch (type) {
            case NbtTag.TYPE_BYTE       -> new NbtTag.ByteTag(dis.readByte());
            case NbtTag.TYPE_SHORT      -> new NbtTag.ShortTag(dis.readShort());
            case NbtTag.TYPE_INT        -> new NbtTag.IntTag(dis.readInt());
            case NbtTag.TYPE_LONG       -> new NbtTag.LongTag(dis.readLong());
            case NbtTag.TYPE_FLOAT      -> new NbtTag.FloatTag(dis.readFloat());
            case NbtTag.TYPE_DOUBLE     -> new NbtTag.DoubleTag(dis.readDouble());
            case NbtTag.TYPE_BYTE_ARRAY -> {
                int n = dis.readInt();
                byte[] data = new byte[n];
                dis.readFully(data);
                yield new NbtTag.ByteArrayTag(data);
            }
            case NbtTag.TYPE_STRING     -> new NbtTag.StringTag(readUtf(dis));
            case NbtTag.TYPE_LIST       -> readListPayload(dis);
            case NbtTag.TYPE_COMPOUND   -> readCompoundPayload(dis);
            case NbtTag.TYPE_INT_ARRAY  -> {
                int n = dis.readInt();
                int[] data = new int[n];
                for (int i = 0; i < n; i++) data[i] = dis.readInt();
                yield new NbtTag.IntArrayTag(data);
            }
            case NbtTag.TYPE_LONG_ARRAY -> {
                int n = dis.readInt();
                long[] data = new long[n];
                for (int i = 0; i < n; i++) data[i] = dis.readLong();
                yield new NbtTag.LongArrayTag(data);
            }
            default -> throw new IOException("Unknown NBT tag type: " + type);
        };
    }

    private static NbtTag.ListTag readListPayload(DataInputStream dis) throws IOException {
        int elemType = dis.readUnsignedByte();
        int count = dis.readInt();
        List<NbtTag> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // elemType 0 (TAG_End) in a list means empty list; elements have no payload
            elements.add(elemType == 0 ? new NbtTag.IntTag(0) : readPayload(dis, elemType));
        }
        return new NbtTag.ListTag(elemType, elements);
    }

    private static String readUtf(DataInputStream dis) throws IOException {
        int len = dis.readUnsignedShort();
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

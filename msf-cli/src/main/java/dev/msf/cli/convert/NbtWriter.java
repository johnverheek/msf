package dev.msf.cli.convert;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes NBT TAG_Compound data to byte arrays.
 *
 * <p>Two entry points are provided:
 * <ul>
 *   <li>{@link #writeCompound} — produces gzip-compressed NBT bytes
 *       (used for vanilla {@code .nbt} structure files).</li>
 *   <li>{@link #writeCompoundRaw} — produces raw (non-gzip) NBT bytes
 *       (used for NBT payloads stored inside MSF blocks).</li>
 * </ul>
 *
 * <p>All multi-byte integers are big-endian per the NBT specification.
 */
public final class NbtWriter {

    private NbtWriter() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Serializes a {@link NbtTag.CompoundTag} to gzip-compressed NBT bytes.
     *
     * @param compound the root compound to serialize
     * @param rootName the root compound's name (typically {@code ""} for structure files)
     * @return gzip-compressed NBT bytes suitable for a vanilla {@code .nbt} file
     * @throws IOException if serialization fails
     */
    public static byte[] writeCompound(NbtTag.CompoundTag compound, String rootName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gos)) {
            dos.writeByte(NbtTag.TYPE_COMPOUND);
            writeUtf(dos, rootName);
            writeCompoundPayload(dos, compound);
        }
        return baos.toByteArray();
    }

    /**
     * Serializes a {@link NbtTag.CompoundTag} to raw (non-gzip) NBT bytes.
     *
     * <p>The output starts with {@code 0x0A} (TAG_Compound type byte), followed by the
     * u16 big-endian root name, and then the compound content. This format is expected by
     * {@link NbtReader#readCompoundRaw} and by {@link dev.msf.core.util.UuidStripper}.
     *
     * @param compound the root compound to serialize
     * @param rootName the root compound's name (typically {@code ""} for MSF payloads)
     * @return raw NBT bytes suitable for storage in MSF entity/block-entity nbt_payload fields
     * @throws IOException if serialization fails
     */
    public static byte[] writeCompoundRaw(NbtTag.CompoundTag compound, String rootName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(NbtTag.TYPE_COMPOUND);
        writeUtf(dos, rootName);
        writeCompoundPayload(dos, compound);
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Internal serialization
    // -------------------------------------------------------------------------

    private static void writeCompoundPayload(DataOutputStream dos, NbtTag.CompoundTag compound)
            throws IOException {
        for (Map.Entry<String, NbtTag> entry : compound.entries().entrySet()) {
            dos.writeByte(tagType(entry.getValue()));
            writeUtf(dos, entry.getKey());
            writePayload(dos, entry.getValue());
        }
        dos.writeByte(0); // TAG_End
    }

    private static void writePayload(DataOutputStream dos, NbtTag tag) throws IOException {
        switch (tag) {
            case NbtTag.ByteTag t       -> dos.writeByte(t.value());
            case NbtTag.ShortTag t      -> dos.writeShort(t.value());
            case NbtTag.IntTag t        -> dos.writeInt(t.value());
            case NbtTag.LongTag t       -> dos.writeLong(t.value());
            case NbtTag.FloatTag t      -> dos.writeFloat(t.value());
            case NbtTag.DoubleTag t     -> dos.writeDouble(t.value());
            case NbtTag.ByteArrayTag t  -> { dos.writeInt(t.value().length); dos.write(t.value()); }
            case NbtTag.StringTag t     -> writeUtf(dos, t.value());
            case NbtTag.ListTag t       -> writeListPayload(dos, t);
            case NbtTag.CompoundTag t   -> writeCompoundPayload(dos, t);
            case NbtTag.IntArrayTag t   -> {
                dos.writeInt(t.value().length);
                for (int v : t.value()) dos.writeInt(v);
            }
            case NbtTag.LongArrayTag t  -> {
                dos.writeInt(t.value().length);
                for (long v : t.value()) dos.writeLong(v);
            }
        }
    }

    private static void writeListPayload(DataOutputStream dos, NbtTag.ListTag list)
            throws IOException {
        dos.writeByte(list.elementType());
        dos.writeInt(list.elements().size());
        for (NbtTag elem : list.elements()) {
            writePayload(dos, elem);
        }
    }

    static int tagType(NbtTag tag) {
        return switch (tag) {
            case NbtTag.ByteTag ignored      -> NbtTag.TYPE_BYTE;
            case NbtTag.ShortTag ignored     -> NbtTag.TYPE_SHORT;
            case NbtTag.IntTag ignored       -> NbtTag.TYPE_INT;
            case NbtTag.LongTag ignored      -> NbtTag.TYPE_LONG;
            case NbtTag.FloatTag ignored     -> NbtTag.TYPE_FLOAT;
            case NbtTag.DoubleTag ignored    -> NbtTag.TYPE_DOUBLE;
            case NbtTag.ByteArrayTag ignored -> NbtTag.TYPE_BYTE_ARRAY;
            case NbtTag.StringTag ignored    -> NbtTag.TYPE_STRING;
            case NbtTag.ListTag ignored      -> NbtTag.TYPE_LIST;
            case NbtTag.CompoundTag ignored  -> NbtTag.TYPE_COMPOUND;
            case NbtTag.IntArrayTag ignored  -> NbtTag.TYPE_INT_ARRAY;
            case NbtTag.LongArrayTag ignored -> NbtTag.TYPE_LONG_ARRAY;
        };
    }

    private static void writeUtf(DataOutputStream dos, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }
}

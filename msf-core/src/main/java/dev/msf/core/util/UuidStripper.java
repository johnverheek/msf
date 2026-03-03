package dev.msf.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Binary NBT scanner that removes UUID-related tags from raw NBT payloads
 * without deserializing the full NBT tree into Java objects.
 *
 * <p>The following tags are stripped at every depth of a compound structure:
 * <ul>
 *   <li>{@code TAG_Int_Array} (type 11) named {@code "UUID"} — Minecraft 1.16+ entity UUID</li>
 *   <li>{@code TAG_Long} (type 4) named {@code "UUIDMost"} — pre-1.16 UUID high bits</li>
 *   <li>{@code TAG_Long} (type 4) named {@code "UUIDLeast"} — pre-1.16 UUID low bits</li>
 * </ul>
 *
 * <p>All other tags are preserved byte-for-byte. Bulk copies are used where possible —
 * primitive tag payloads are copied directly from the source array without byte-by-byte
 * re-encoding.
 *
 * <p>NBT is big-endian per the NBT specification, unlike the rest of MSF which is
 * little-endian. This class uses a big-endian {@link ByteBuffer} internally.
 *
 * <p>If the payload is empty, not a compound root (does not begin with {@code 0x0A}),
 * or is malformed, the original bytes are returned unchanged.
 *
 * <p>This class is a utility class — all methods are static and it cannot be instantiated.
 *
 * @see MsfSpec Section 8.2 — UUID stripping requirement for entities
 * @see MsfSpec Section 9.2 — UUID stripping requirement for block entities
 */
public final class UuidStripper {

    // -------------------------------------------------------------------------
    // NBT tag type constants (per the NBT specification)
    // -------------------------------------------------------------------------

    private static final int TAG_END        = 0;
    private static final int TAG_BYTE       = 1;
    private static final int TAG_SHORT      = 2;
    private static final int TAG_INT        = 3;
    private static final int TAG_LONG       = 4;
    private static final int TAG_FLOAT      = 5;
    private static final int TAG_DOUBLE     = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING     = 8;
    private static final int TAG_LIST       = 9;
    private static final int TAG_COMPOUND   = 10;
    private static final int TAG_INT_ARRAY  = 11;
    private static final int TAG_LONG_ARRAY = 12;

    // -------------------------------------------------------------------------
    // Tag names to strip
    // -------------------------------------------------------------------------

    /** Name of the int[4] UUID tag used in Minecraft 1.16+ (TAG_Int_Array, type 11). */
    private static final String UUID_INT_ARRAY = "UUID";

    /** Name of the UUID high-bits long tag used before Minecraft 1.16 (TAG_Long, type 4). */
    private static final String UUID_MOST = "UUIDMost";

    /** Name of the UUID low-bits long tag used before Minecraft 1.16 (TAG_Long, type 4). */
    private static final String UUID_LEAST = "UUIDLeast";

    private UuidStripper() {
        // Utility class — all methods are static
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Strips UUID-related tags from a binary NBT payload.
     *
     * <p>The input is expected to be a root {@code TAG_Compound} (type byte {@code 0x0A})
     * followed by a u16 name (typically empty) and the compound content. If the input is
     * empty or does not begin with {@code 0x0A}, it is returned unchanged.
     *
     * <p>If the payload is malformed and parsing fails, the original bytes are returned
     * unchanged rather than throwing — UUID stripping is best-effort for robustness.
     *
     * @param nbt the raw binary NBT payload; if {@code null}, an empty array is returned
     * @return binary NBT with UUID tags removed, or the original bytes if no tags were found
     *         or if parsing failed; guaranteed byte-for-byte identical if no UUID tags present
     */
    public static byte[] strip(byte[] nbt) {
        if (nbt == null) {
            return new byte[0];
        }
        if (nbt.length == 0 || (nbt[0] & 0xFF) != TAG_COMPOUND) {
            // Empty or not a compound root — nothing to strip
            return nbt;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(nbt.length);
        ByteBuffer in = ByteBuffer.wrap(nbt).order(ByteOrder.BIG_ENDIAN);

        try {
            // Write root tag type byte (0x0A)
            out.write(in.get() & 0xFF);
            // Write root tag name (u16 big-endian + bytes — usually empty, i.e. 0x00 0x00)
            copyStringHeader(nbt, in, out);
            // Recursively scan the root compound content, stripping UUID tags
            processCompound(nbt, in, out);
        } catch (Exception e) {
            // Malformed NBT — return original unchanged
            return nbt;
        }

        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Compound scanning
    // -------------------------------------------------------------------------

    /**
     * Scans compound content tag by tag. Strips UUID tags; copies all others.
     * Writes the closing {@code TAG_End} byte to {@code out}.
     */
    private static void processCompound(
        byte[] nbt,
        ByteBuffer in,
        ByteArrayOutputStream out
    ) throws IOException {
        while (in.hasRemaining()) {
            int type = in.get() & 0xFF;

            if (type == TAG_END) {
                out.write(TAG_END);
                return;
            }

            // Read name: u16BE length + UTF-8 bytes
            int nameLenPos = in.position();     // start of the u16 name-length field
            int nameLen = Short.toUnsignedInt(in.getShort());
            byte[] nameBytes = new byte[nameLen];
            in.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);

            if (shouldStrip(type, name)) {
                // Skip this tag's payload without writing anything
                skipPayload(in, type);
            } else {
                // Write type byte, then name (u16 + bytes), then payload
                out.write(type);
                out.write(nbt, nameLenPos, 2 + nameLen);
                writePayload(nbt, in, out, type);
            }
        }
    }

    /** Returns {@code true} if the tag should be stripped based on type and name. */
    private static boolean shouldStrip(int type, String name) {
        return (type == TAG_INT_ARRAY && UUID_INT_ARRAY.equals(name))
            || (type == TAG_LONG && (UUID_MOST.equals(name) || UUID_LEAST.equals(name)));
    }

    // -------------------------------------------------------------------------
    // Payload writing (keeping)
    // -------------------------------------------------------------------------

    /**
     * Writes the payload for a tag that should be kept.
     * Recurses into compounds; recurses into list elements that are compounds.
     * Bulk-copies all other payloads from the source array.
     */
    private static void writePayload(
        byte[] nbt,
        ByteBuffer in,
        ByteArrayOutputStream out,
        int type
    ) throws IOException {
        if (type == TAG_COMPOUND) {
            // Recurse — compound content is scanned for UUID tags
            processCompound(nbt, in, out);
        } else if (type == TAG_LIST) {
            writeListPayload(nbt, in, out);
        } else {
            // Primitive or array tag: skip (advance position), then bulk-copy the source bytes
            int start = in.position();
            skipPrimitive(in, type);
            out.write(nbt, start, in.position() - start);
        }
    }

    /**
     * Writes a TAG_List payload: element type + count + per-element payloads.
     * Recurses into compound and nested list elements; bulk-copies primitive elements.
     */
    private static void writeListPayload(
        byte[] nbt,
        ByteBuffer in,
        ByteArrayOutputStream out
    ) throws IOException {
        // Element type byte + count (4 bytes big-endian): copy these 5 bytes verbatim
        int headerStart = in.position();
        int elemType = in.get() & 0xFF;
        int count = in.getInt();
        out.write(nbt, headerStart, 5);

        for (int i = 0; i < count; i++) {
            if (elemType == TAG_COMPOUND) {
                processCompound(nbt, in, out);
            } else if (elemType == TAG_LIST) {
                writeListPayload(nbt, in, out);
            } else {
                // Primitive element: bulk-copy
                int start = in.position();
                skipPrimitive(in, elemType);
                out.write(nbt, start, in.position() - start);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Payload skipping (stripping)
    // -------------------------------------------------------------------------

    /** Skips a tag's payload without writing. Routes to the appropriate skip helper. */
    private static void skipPayload(ByteBuffer in, int type) {
        switch (type) {
            case TAG_COMPOUND   -> skipCompound(in);
            case TAG_LIST       -> skipList(in);
            default             -> skipPrimitive(in, type);
        }
    }

    /**
     * Skips a compound tag's content (all nested tags until TAG_End).
     * Used when the entire compound is being stripped.
     */
    private static void skipCompound(ByteBuffer in) {
        while (in.hasRemaining()) {
            int type = in.get() & 0xFF;
            if (type == TAG_END) {
                return;
            }
            // Skip name
            int nameLen = Short.toUnsignedInt(in.getShort());
            in.position(in.position() + nameLen);
            // Skip payload
            skipPayload(in, type);
        }
    }

    /** Skips a TAG_List payload (element type + count + all elements). */
    private static void skipList(ByteBuffer in) {
        int elemType = in.get() & 0xFF;
        int count = in.getInt();
        for (int i = 0; i < count; i++) {
            skipPayload(in, elemType);
        }
    }

    /**
     * Skips a primitive (non-compound, non-list) tag's payload by advancing the
     * buffer position by the computed byte count. Does not copy to output.
     */
    private static void skipPrimitive(ByteBuffer in, int type) {
        switch (type) {
            case TAG_BYTE       -> in.get();
            case TAG_SHORT      -> in.getShort();
            case TAG_INT        -> in.getInt();
            case TAG_LONG       -> in.getLong();
            case TAG_FLOAT      -> in.getInt();      // 4 bytes; read as int to avoid float ops
            case TAG_DOUBLE     -> in.getLong();     // 8 bytes; read as long to avoid double ops
            case TAG_BYTE_ARRAY -> { int n = in.getInt(); in.position(in.position() + n); }
            case TAG_STRING     -> { int n = Short.toUnsignedInt(in.getShort()); in.position(in.position() + n); }
            case TAG_INT_ARRAY  -> { int n = in.getInt(); in.position(in.position() + n * 4); }
            case TAG_LONG_ARRAY -> { int n = in.getInt(); in.position(in.position() + n * 8); }
            default             -> throw new IllegalStateException("Unknown NBT tag type: " + type);
        }
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    /**
     * Copies a tag name field (u16 big-endian length + bytes) from {@code in} to {@code out}
     * without interpreting the name as a string. Used for the root compound name.
     */
    private static void copyStringHeader(
        byte[] nbt,
        ByteBuffer in,
        ByteArrayOutputStream out
    ) throws IOException {
        int start = in.position();
        int len = Short.toUnsignedInt(in.getShort());
        in.position(in.position() + len);
        out.write(nbt, start, in.position() - start);
    }
}

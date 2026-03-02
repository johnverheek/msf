package dev.msf.core.model;

import dev.msf.core.MsfParseException;
import dev.msf.core.MsfWarning;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The metadata block — descriptive information about a schematic, including
 * placement
 * metadata. Required in all MSF files.
 *
 * <h2>Block layout (Section 5.1)</h2>
 * 
 * <pre>
 *     u32     block length
 *     str     name
 *     str     author
 *     u64     created timestamp
 *     u64     modified timestamp
 *     str     description
 *     u16     tag count
 *       str[] tags
 *     u16     contributor count
 *       str[] contributors
 *     str     license identifier
 *     str     source URL
 *     u32     thumbnail size (0 = absent)
 *       u8[]  thumbnail bytes (PNG, if size > 0)
 *     [placement metadata — Section 10]
 *     str     anchor name
 *     i32     anchor offset X
 *     i32     anchor offset Y
 *     i32     anchor offset Z
 *     u8      canonical facing
 *     u8      rotation compatibility flags
 *     u8      has functional volume (0x00/0x01)
 *       [if present]
 *       i32   functional volume min X
 *       i32   functional volume min Y
 *       i32   functional volume min Z
 *       i32   functional volume max X
 *       i32   functional volume max Y
 *       i32   functional volume max Z
 * </pre>
 *
 * @see MsfSpec Section 5 — metadata block
 * @see MsfSpec Section 10 — placement metadata
 */
public final class MsfMetadata {

    // -------------------------------------------------------------------------
    // Descriptive fields
    // -------------------------------------------------------------------------

    /** Schema name; MUST NOT be empty (Section 5.2). */
    private final String name;

    /** Author; MAY be empty (Section 5.2). */
    private final String author;

    /** Created timestamp — Unix epoch seconds (Section 5.2). */
    private final long createdTimestamp;

    /** Modified timestamp — Unix epoch seconds (Section 5.2). */
    private final long modifiedTimestamp;

    /** Freeform description; may be empty. */
    private final String description;

    /** Freeform tags; case-sensitive (Section 5.2). */
    private final List<String> tags;

    /** Contributor names; may be empty. */
    private final List<String> contributors;

    /** SPDX license identifier; empty = no license declared (Section 5.2). */
    private final String licenseIdentifier;

    /** Source URL; empty = absent. */
    private final String sourceUrl;

    /**
     * PNG thumbnail bytes; {@code null} or zero-length = absent.
     * Stored as-is; may be malformed (readers must tolerate malformed thumbnails).
     */
    private final byte[] thumbnail;

    // -------------------------------------------------------------------------
    // Placement metadata
    // -------------------------------------------------------------------------

    /** Human-readable anchor label (Section 10.1). */
    private final String anchorName;

    /** Anchor offset from minimum corner, X axis (Section 10.1). */
    private final int anchorOffsetX;

    /** Anchor offset from minimum corner, Y axis (Section 10.1). */
    private final int anchorOffsetY;

    /** Anchor offset from minimum corner, Z axis (Section 10.1). */
    private final int anchorOffsetZ;

    /**
     * Canonical facing direction (Section 10.2).
     * Valid values: 0x00 North, 0x01 South, 0x02 East, 0x03 West.
     */
    private final int canonicalFacing;

    /**
     * Rotation compatibility flags u8 bitmask (Section 10.3).
     * Bits 0–4 are defined; bits 5–7 must be 0.
     */
    private final int rotationCompatibility;

    /**
     * Functional volume, if present (Section 10.4).
     * Coordinates are relative to the anchor point.
     */
    private final Optional<FunctionalVolume> functionalVolume;

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Canonical facing — North ({@code 0x00}). */
    public static final int FACING_NORTH = 0x00;
    /** Canonical facing — South ({@code 0x01}). */
    public static final int FACING_SOUTH = 0x01;
    /** Canonical facing — East ({@code 0x02}). */
    public static final int FACING_EAST = 0x02;
    /** Canonical facing — West ({@code 0x03}). */
    public static final int FACING_WEST = 0x03;

    /** Rotation compatibility bit — rotate 90° valid (Section 10.3). */
    public static final int ROT_90_VALID = 1 << 0;
    /** Rotation compatibility bit — rotate 180° valid (Section 10.3). */
    public static final int ROT_180_VALID = 1 << 1;
    /** Rotation compatibility bit — rotate 270° valid (Section 10.3). */
    public static final int ROT_270_VALID = 1 << 2;
    /** Rotation compatibility bit — mirror X valid (Section 10.3). */
    public static final int MIRROR_X_VALID = 1 << 3;
    /** Rotation compatibility bit — mirror Z valid (Section 10.3). */
    public static final int MIRROR_Z_VALID = 1 << 4;
    /** Mask of all defined rotation compatibility bits (0–4). */
    public static final int ROT_COMPAT_DEFINED_MASK = 0x1F;
    /** Mask of reserved rotation compatibility bits (5–7). */
    public static final int ROT_COMPAT_RESERVED_MASK = 0xE0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private MsfMetadata(
            String name, String author, long createdTimestamp, long modifiedTimestamp,
            String description, List<String> tags, List<String> contributors,
            String licenseIdentifier, String sourceUrl, byte[] thumbnail,
            String anchorName, int anchorOffsetX, int anchorOffsetY, int anchorOffsetZ,
            int canonicalFacing, int rotationCompatibility, Optional<FunctionalVolume> functionalVolume) {
        this.name = name;
        this.author = author;
        this.createdTimestamp = createdTimestamp;
        this.modifiedTimestamp = modifiedTimestamp;
        this.description = description;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        this.contributors = Collections.unmodifiableList(new ArrayList<>(contributors));
        this.licenseIdentifier = licenseIdentifier;
        this.sourceUrl = sourceUrl;
        this.thumbnail = thumbnail == null ? new byte[0] : Arrays.copyOf(thumbnail, thumbnail.length);
        this.anchorName = anchorName;
        this.anchorOffsetX = anchorOffsetX;
        this.anchorOffsetY = anchorOffsetY;
        this.anchorOffsetZ = anchorOffsetZ;
        this.canonicalFacing = canonicalFacing;
        this.rotationCompatibility = rotationCompatibility;
        this.functionalVolume = functionalVolume;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public long createdTimestamp() {
        return createdTimestamp;
    }

    public long modifiedTimestamp() {
        return modifiedTimestamp;
    }

    public String description() {
        return description;
    }

    public List<String> tags() {
        return tags;
    }

    public List<String> contributors() {
        return contributors;
    }

    public String licenseIdentifier() {
        return licenseIdentifier;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public byte[] thumbnail() {
        return Arrays.copyOf(thumbnail, thumbnail.length);
    }

    public boolean hasThumbnail() {
        return thumbnail.length > 0;
    }

    public String anchorName() {
        return anchorName;
    }

    public int anchorOffsetX() {
        return anchorOffsetX;
    }

    public int anchorOffsetY() {
        return anchorOffsetY;
    }

    public int anchorOffsetZ() {
        return anchorOffsetZ;
    }

    public int canonicalFacing() {
        return canonicalFacing;
    }

    public int rotationCompatibility() {
        return rotationCompatibility;
    }

    public Optional<FunctionalVolume> functionalVolume() {
        return functionalVolume;
    }

    // -------------------------------------------------------------------------
    // Functional volume nested record
    // -------------------------------------------------------------------------

    /**
     * An author-declared region that must be unobstructed for the schematic to
     * function
     * correctly. Coordinates are relative to the anchor point (Section 10.4).
     *
     * @param minX minimum X coordinate (relative to anchor)
     * @param minY minimum Y coordinate (relative to anchor)
     * @param minZ minimum Z coordinate (relative to anchor)
     * @param maxX maximum X coordinate (relative to anchor)
     * @param maxY maximum Y coordinate (relative to anchor)
     * @param maxZ maximum Z coordinate (relative to anchor)
     */
    public record FunctionalVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Serializes this metadata block to bytes, including the 4-byte
     * {@code block_length} prefix.
     *
     * @param warningConsumer receives warnings produced during serialization; may
     *                        be {@code null}
     * @return the complete metadata block bytes
     * @throws IllegalArgumentException if {@code canonicalFacing} is not in [0, 3]
     */
    public byte[] toBytes(Consumer<MsfWarning> warningConsumer) {
        // Validate canonical facing (Section 10.2)
        if (canonicalFacing < 0 || canonicalFacing > 3) {
            throw new IllegalArgumentException(String.format(
                    "Field 'canonicalFacing' value 0x%02X is invalid — must be 0x00–0x03 "
                            + "(North/South/East/West)",
                    canonicalFacing));
        }

        // Sanitize rotation compatibility — mask to bits 0–4 (Section 10.3)
        int rawRotCompat = rotationCompatibility;
        int sanitizedRotCompat = rawRotCompat & ROT_COMPAT_DEFINED_MASK;
        if (sanitizedRotCompat != rawRotCompat && warningConsumer != null) {
            warningConsumer.accept(MsfWarning.writeWarning(
                    MsfWarning.Code.RESERVED_FLAG_CLEARED,
                    String.format(
                            "Reserved rotation compatibility bits were cleared: "
                                    + "provided 0x%02X, writing 0x%02X",
                            rawRotCompat & 0xFF, sanitizedRotCompat & 0xFF)));
        }

        // Build body bytes (everything after the block_length u32)
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        try {
            writeStr(body, name);
            writeStr(body, author);
            writeU64(body, createdTimestamp);
            writeU64(body, modifiedTimestamp);
            writeStr(body, description);
            writeU16(body, tags.size());
            for (String tag : tags)
                writeStr(body, tag);
            writeU16(body, contributors.size());
            for (String contributor : contributors)
                writeStr(body, contributor);
            writeStr(body, licenseIdentifier);
            writeStr(body, sourceUrl);

            // Thumbnail (Section 5.2)
            if (thumbnail.length > 0) {
                writeU32(body, thumbnail.length);
                body.write(thumbnail);
            } else {
                writeU32(body, 0);
            }

            // Placement metadata (Section 5.1 / Section 10)
            writeStr(body, anchorName);
            writeI32(body, anchorOffsetX);
            writeI32(body, anchorOffsetY);
            writeI32(body, anchorOffsetZ);
            body.write(canonicalFacing & 0xFF);
            body.write(sanitizedRotCompat & 0xFF);

            // Functional volume (Section 10.4)
            if (functionalVolume.isPresent()) {
                body.write(0x01);
                FunctionalVolume fv = functionalVolume.get();
                writeI32(body, fv.minX());
                writeI32(body, fv.minY());
                writeI32(body, fv.minZ());
                writeI32(body, fv.maxX());
                writeI32(body, fv.maxY());
                writeI32(body, fv.maxZ());
            } else {
                body.write(0x00);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        byte[] bodyBytes = body.toByteArray();

        // Prefix with block_length (u32 = bytes following this field = body length)
        ByteBuffer result = ByteBuffer.allocate(4 + bodyBytes.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(bodyBytes.length);
        result.put(bodyBytes);
        return result.array();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses a metadata block from the given byte array at the given offset.
     *
     * @param data            the file bytes
     * @param offset          absolute byte offset of the start of the metadata
     *                        block
     * @param warningConsumer receives warnings; may be {@code null}
     * @return the parsed {@link MsfMetadata}
     * @throws MsfParseException if the block is malformed
     */
    public static MsfMetadata fromBytes(byte[] data, int offset,
            Consumer<MsfWarning> warningConsumer)
            throws MsfParseException {
        ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset)
                .order(ByteOrder.LITTLE_ENDIAN);

        // Read block_length (u32) — we use it to track position for thumbnail skip
        Integer.toUnsignedLong(buf.getInt());

        String name = readStr(buf);
        if (name.isEmpty()) {
            throw new MsfParseException("Metadata block 'name' field must not be empty (Section 5.2)");
        }

        String author = readStr(buf);
        long createdTimestamp = buf.getLong();
        long modifiedTimestamp = buf.getLong();
        String description = readStr(buf);

        int tagCount = Short.toUnsignedInt(buf.getShort());
        List<String> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++)
            tags.add(readStr(buf));

        int contributorCount = Short.toUnsignedInt(buf.getShort());
        List<String> contributors = new ArrayList<>(contributorCount);
        for (int i = 0; i < contributorCount; i++)
            contributors.add(readStr(buf));

        String licenseId = readStr(buf);
        String sourceUrl = readStr(buf);

        // Thumbnail (Section 5.2)
        long thumbnailSize = Integer.toUnsignedLong(buf.getInt());
        byte[] thumbnailBytes;
        if (thumbnailSize == 0L) {
            thumbnailBytes = new byte[0];
        } else {
            // Read exactly thumbnailSize bytes regardless of PNG validity
            if (thumbnailSize > Integer.MAX_VALUE || buf.remaining() < (int) thumbnailSize) {
                throw new MsfParseException(String.format(
                        "Thumbnail size %d is declared but only %d bytes remain in block",
                        thumbnailSize, buf.remaining()));
            }
            thumbnailBytes = new byte[(int) thumbnailSize];
            buf.get(thumbnailBytes);

            // Validate PNG signature (bytes 0-7 must be 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A
            // 0x0A)
            if (!isPng(thumbnailBytes) && warningConsumer != null) {
                warningConsumer.accept(MsfWarning.atOffset(
                        MsfWarning.Code.MALFORMED_THUMBNAIL,
                        String.format(
                                "Thumbnail (%d bytes) does not begin with a valid PNG signature — "
                                        + "thumbnail is malformed but parsing continues",
                                thumbnailSize),
                        offset // approximate offset
                ));
            }
        }

        // Placement metadata (Section 10)
        String anchorName = readStr(buf);
        int anchorOffsetX = buf.getInt();
        int anchorOffsetY = buf.getInt();
        int anchorOffsetZ = buf.getInt();

        int canonicalFacing = Byte.toUnsignedInt(buf.get());
        if (canonicalFacing > 3) {
            throw new MsfParseException(String.format(
                    "Invalid canonical facing value 0x%02X — must be 0x00–0x03 (Section 10.2)",
                    canonicalFacing));
        }

        int rotCompatRaw = Byte.toUnsignedInt(buf.get());
        int rotCompatSanitized = rotCompatRaw;
        if ((rotCompatRaw & ROT_COMPAT_RESERVED_MASK) != 0 && warningConsumer != null) {
            warningConsumer.accept(MsfWarning.atOffset(
                    MsfWarning.Code.RESERVED_FLAG_SET,
                    String.format(
                            "Rotation compatibility field has reserved bits set: 0x%02X — "
                                    + "treating reserved bits as 0 (Section 10.3)",
                            rotCompatRaw),
                    offset));
            rotCompatSanitized = rotCompatRaw & ROT_COMPAT_DEFINED_MASK;
        }

        // Functional volume (Section 10.4)
        int hasFunctionalVolume = Byte.toUnsignedInt(buf.get());
        if (hasFunctionalVolume != 0x00 && hasFunctionalVolume != 0x01) {
            throw new MsfParseException(String.format(
                    "has_functional_volume field has invalid value 0x%02X — must be 0x00 or 0x01 "
                            + "(Section 10.4)",
                    hasFunctionalVolume));
        }

        Optional<FunctionalVolume> functionalVolume;
        if (hasFunctionalVolume == 0x01) {
            int minX = buf.getInt();
            int minY = buf.getInt();
            int minZ = buf.getInt();
            int maxX = buf.getInt();
            int maxY = buf.getInt();
            int maxZ = buf.getInt();
            functionalVolume = Optional.of(new FunctionalVolume(minX, minY, minZ, maxX, maxY, maxZ));
        } else {
            functionalVolume = Optional.empty();
        }

        return new MsfMetadata(
                name, author, createdTimestamp, modifiedTimestamp, description,
                tags, contributors, licenseId, sourceUrl, thumbnailBytes,
                anchorName, anchorOffsetX, anchorOffsetY, anchorOffsetZ,
                canonicalFacing, rotCompatSanitized, functionalVolume);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link Builder} for constructing {@link MsfMetadata} instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MsfMetadata}.
     *
     * <p>
     * All fields default to empty/zero values. At minimum, {@code name} must be
     * set.
     */
    public static final class Builder {
        private String name = "";
        private String author = "";
        private long createdTimestamp = 0L;
        private long modifiedTimestamp = 0L;
        private String description = "";
        private List<String> tags = new ArrayList<>();
        private List<String> contributors = new ArrayList<>();
        private String licenseIdentifier = "";
        private String sourceUrl = "";
        private byte[] thumbnail = new byte[0];
        private String anchorName = "";
        private int anchorOffsetX = 0;
        private int anchorOffsetY = 0;
        private int anchorOffsetZ = 0;
        private int canonicalFacing = FACING_NORTH;
        private int rotationCompatibility = 0;
        private Optional<FunctionalVolume> functionalVolume = Optional.empty();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder createdTimestamp(long ts) {
            this.createdTimestamp = ts;
            return this;
        }

        public Builder modifiedTimestamp(long ts) {
            this.modifiedTimestamp = ts;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = new ArrayList<>(tags);
            return this;
        }

        public Builder contributors(List<String> contributors) {
            this.contributors = new ArrayList<>(contributors);
            return this;
        }

        public Builder licenseIdentifier(String license) {
            this.licenseIdentifier = license;
            return this;
        }

        public Builder sourceUrl(String url) {
            this.sourceUrl = url;
            return this;
        }

        public Builder thumbnail(byte[] png) {
            this.thumbnail = Arrays.copyOf(png, png.length);
            return this;
        }

        public Builder anchorName(String name) {
            this.anchorName = name;
            return this;
        }

        public Builder anchorOffset(int x, int y, int z) {
            this.anchorOffsetX = x;
            this.anchorOffsetY = y;
            this.anchorOffsetZ = z;
            return this;
        }

        public Builder canonicalFacing(int facing) {
            this.canonicalFacing = facing;
            return this;
        }

        public Builder rotationCompatibility(int flags) {
            this.rotationCompatibility = flags;
            return this;
        }

        public Builder functionalVolume(FunctionalVolume fv) {
            this.functionalVolume = Optional.of(fv);
            return this;
        }

        public MsfMetadata build() {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Metadata 'name' must not be empty (Section 5.2)");
            }
            return new MsfMetadata(
                    name, author, createdTimestamp, modifiedTimestamp, description,
                    tags, contributors, licenseIdentifier, sourceUrl, thumbnail,
                    anchorName, anchorOffsetX, anchorOffsetY, anchorOffsetZ,
                    canonicalFacing, rotationCompatibility, functionalVolume);
        }
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    /** Writes a {@code str} field: u16 byte length + UTF-8 bytes. */
    private static void writeStr(java.io.OutputStream out, String s) throws java.io.IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        // u16 little-endian length prefix
        out.write(bytes.length & 0xFF);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes);
    }

    /** Writes a u64 value as little-endian 8 bytes. */
    private static void writeU64(java.io.OutputStream out, long v) throws java.io.IOException {
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putLong(v);
        out.write(buf);
    }

    /** Writes a u32 value as little-endian 4 bytes. */
    private static void writeU32(java.io.OutputStream out, long v) throws java.io.IOException {
        byte[] buf = new byte[4];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putInt((int) v);
        out.write(buf);
    }

    /** Writes an i32 value as little-endian 4 bytes. */
    private static void writeI32(java.io.OutputStream out, int v) throws java.io.IOException {
        byte[] buf = new byte[4];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putInt(v);
        out.write(buf);
    }

    /** Writes a u16 value as little-endian 2 bytes. */
    private static void writeU16(java.io.OutputStream out, int v) throws java.io.IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    /** Reads a {@code str} field: u16 byte length + UTF-8 bytes. */
    private static String readStr(ByteBuffer buf) throws MsfParseException {
        int len = Short.toUnsignedInt(buf.getShort());
        if (len > buf.remaining()) {
            throw new MsfParseException(
                    "String length " + len + " exceeds remaining buffer " + buf.remaining());
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Checks if the byte array begins with the PNG magic signature. */
    private static boolean isPng(byte[] data) {
        if (data.length < 8)
            return false;
        return (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E
                && data[3] == 0x47 && data[4] == 0x0D && data[5] == 0x0A
                && data[6] == 0x1A && data[7] == 0x0A;
    }
}

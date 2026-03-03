package dev.msf.core.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * A block entity stored in the MSF block entity block.
 *
 * <p>Immutable. The {@code nbtPayload} is stored and returned defensively copied.
 * The NBT payload MUST NOT include the {@code id}, {@code x}, {@code y}, or {@code z}
 * tags (those are captured in the typed position and type fields) and MUST NOT include
 * UUID data — the writer strips UUIDs via {@link dev.msf.core.util.UuidStripper} before
 * encoding.
 *
 * <h2>On-disk layout (Section 9.1 — per block entity)</h2>
 * <pre>
 *     i32   position X (relative to anchor)
 *     i32   position Y (relative to anchor)
 *     i32   position Z (relative to anchor)
 *     str   block entity type (opaque UTF-8)
 *     u16   NBT payload length
 *     u8[]  NBT payload (binary NBT, excludes position tags and id tag)
 * </pre>
 *
 * <p>All fields follow little-endian byte order per Section 2.1.
 * Block entity type and NBT payload are opaque UTF-8 / raw bytes — never interpreted in msf-core.
 *
 * @see MsfSpec Section 9 — block entity block
 * @see MsfSpec Section 9.2 — normative requirements
 */
public final class MsfBlockEntity {

    /** Maximum NBT payload size in bytes per Section 9.2. */
    public static final int MAX_NBT_PAYLOAD_BYTES = 65535;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Block entity X position relative to the schematic anchor (i32, little-endian). */
    private final int positionX;

    /** Block entity Y position relative to the schematic anchor (i32, little-endian). */
    private final int positionY;

    /** Block entity Z position relative to the schematic anchor (i32, little-endian). */
    private final int positionZ;

    /**
     * Block entity type identifier string (e.g. {@code "minecraft:chest"}).
     * Opaque UTF-8 — never validated against a registry in msf-core.
     */
    private final String blockEntityType;

    /**
     * Raw binary NBT payload after UUID stripping and exclusion of {@code id}/{@code x}/{@code y}/{@code z} tags.
     * Stored as a defensive copy; returned as a defensive copy from {@link #nbtPayload()}.
     */
    private final byte[] nbtPayload;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private MsfBlockEntity(
        int positionX,
        int positionY,
        int positionZ,
        String blockEntityType,
        byte[] nbtPayload
    ) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.blockEntityType = blockEntityType;
        this.nbtPayload = Arrays.copyOf(nbtPayload, nbtPayload.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the X position of this block entity relative to the schematic anchor.
     *
     * @return X coordinate (signed, relative to anchor)
     */
    public int positionX() {
        return positionX;
    }

    /**
     * Returns the Y position of this block entity relative to the schematic anchor.
     *
     * @return Y coordinate (signed, relative to anchor)
     */
    public int positionY() {
        return positionY;
    }

    /**
     * Returns the Z position of this block entity relative to the schematic anchor.
     *
     * @return Z coordinate (signed, relative to anchor)
     */
    public int positionZ() {
        return positionZ;
    }

    /**
     * Returns the block entity type identifier string (e.g. {@code "minecraft:chest"}).
     * This value is opaque UTF-8 — never validated against a registry in msf-core.
     *
     * @return block entity type identifier
     */
    public String blockEntityType() {
        return blockEntityType;
    }

    /**
     * Returns a defensive copy of the raw binary NBT payload.
     *
     * <p>The payload excludes the {@code id}, {@code x}, {@code y}, and {@code z} tags
     * (position and type are in typed fields) and UUID tags (stripped by the writer per
     * Section 9.2). The bytes are raw binary NBT — never deserialized in msf-core.
     *
     * @return defensive copy of the NBT payload; never {@code null}, may be empty
     */
    public byte[] nbtPayload() {
        return Arrays.copyOf(nbtPayload, nbtPayload.length);
    }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MsfBlockEntity other)) return false;
        return positionX == other.positionX
            && positionY == other.positionY
            && positionZ == other.positionZ
            && blockEntityType.equals(other.blockEntityType)
            && Arrays.equals(nbtPayload, other.nbtPayload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(positionX, positionY, positionZ, blockEntityType);
        result = 31 * result + Arrays.hashCode(nbtPayload);
        return result;
    }

    @Override
    public String toString() {
        return "MsfBlockEntity{type=" + blockEntityType
            + ", pos=(" + positionX + "," + positionY + "," + positionZ + ")"
            + ", nbtBytes=" + nbtPayload.length + "}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for constructing {@link MsfBlockEntity} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MsfBlockEntity}.
     */
    public static final class Builder {

        private int positionX = 0;
        private int positionY = 0;
        private int positionZ = 0;
        private String blockEntityType = "";
        private byte[] nbtPayload = new byte[0];

        private Builder() {}

        /**
         * Sets all three position coordinates (relative to schematic anchor).
         *
         * @param x X coordinate
         * @param y Y coordinate
         * @param z Z coordinate
         * @return this builder
         */
        public Builder position(int x, int y, int z) {
            this.positionX = x;
            this.positionY = y;
            this.positionZ = z;
            return this;
        }

        /**
         * Sets the block entity type identifier string (e.g. {@code "minecraft:chest"}).
         *
         * @param blockEntityType opaque UTF-8 block entity type identifier
         * @return this builder
         */
        public Builder blockEntityType(String blockEntityType) {
            this.blockEntityType = Objects.requireNonNull(blockEntityType, "blockEntityType must not be null");
            return this;
        }

        /**
         * Sets the raw binary NBT payload.
         *
         * <p>The payload MUST NOT include the {@code id}, {@code x}, {@code y}, {@code z},
         * or UUID tags — those are handled by the writer. A defensive copy is made.
         *
         * @param nbtPayload raw binary NBT bytes; must not be {@code null}; may be empty
         * @return this builder
         */
        public Builder nbtPayload(byte[] nbtPayload) {
            this.nbtPayload = Objects.requireNonNull(nbtPayload, "nbtPayload must not be null").clone();
            return this;
        }

        /**
         * Builds and returns the {@link MsfBlockEntity} instance.
         *
         * @return a new {@link MsfBlockEntity}
         * @throws IllegalArgumentException if the NBT payload exceeds
         *                                  {@value MsfBlockEntity#MAX_NBT_PAYLOAD_BYTES} bytes
         */
        public MsfBlockEntity build() {
            // Spec Section 9.2: NBT payload MUST NOT exceed 65535 bytes
            if (nbtPayload.length > MAX_NBT_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(String.format(
                    "Block entity '%s' NBT payload size %d exceeds maximum permitted value of %d",
                    blockEntityType, nbtPayload.length, MAX_NBT_PAYLOAD_BYTES
                ));
            }
            return new MsfBlockEntity(positionX, positionY, positionZ, blockEntityType, nbtPayload);
        }
    }
}

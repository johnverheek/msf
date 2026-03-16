package dev.msf.core.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * An entity stored in the MSF entity block.
 *
 * <p>Immutable. The {@code nbtPayload} is stored and returned defensively copied.
 * The NBT payload MUST NOT include the {@code id} tag (entity type is captured in
 * the typed {@link #entityType()} field) and MUST NOT include UUID data — the writer
 * strips UUIDs via {@link dev.msf.core.util.UuidStripper} before encoding.
 *
 * <h2>On-disk layout (Section 8.1 — per entity)</h2>
 * <pre>
 *     f64   position X
 *     f64   position Y
 *     f64   position Z
 *     f32   yaw
 *     f32   pitch
 *     str   entity type (opaque UTF-8)
 *     u16   NBT payload length
 *     u8[]  NBT payload (binary NBT, excludes position and type)
 * </pre>
 *
 * <p>All fields follow little-endian byte order per Section 2.1.
 * Entity type and NBT payload are opaque UTF-8 / raw bytes — never interpreted in msf-core.
 *
 * @see MsfSpec Section 8 — entity block
 * @see MsfSpec Section 8.2 — normative requirements
 */
public final class MsfEntity {

    /** Maximum NBT payload size in bytes per Section 8.2. */
    public static final int MAX_NBT_PAYLOAD_BYTES = 65535;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Entity X position relative to the schematic anchor (f64, little-endian). */
    private final double positionX;

    /** Entity Y position relative to the schematic anchor (f64, little-endian). */
    private final double positionY;

    /** Entity Z position relative to the schematic anchor (f64, little-endian). */
    private final double positionZ;

    /** Entity yaw rotation in degrees (f32, little-endian). */
    private final float yaw;

    /** Entity pitch rotation in degrees (f32, little-endian). */
    private final float pitch;

    /**
     * Entity type identifier string (e.g. {@code "minecraft:armor_stand"}).
     * Opaque UTF-8 — never validated against a registry in msf-core.
     */
    private final String entityType;

    /**
     * Raw binary NBT payload after UUID stripping and exclusion of the {@code id} tag.
     * Stored as a defensive copy; returned as a defensive copy from {@link #nbtPayload()}.
     */
    private final byte[] nbtPayload;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private MsfEntity(
        double positionX,
        double positionY,
        double positionZ,
        float yaw,
        float pitch,
        String entityType,
        byte[] nbtPayload
    ) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.entityType = entityType;
        this.nbtPayload = Arrays.copyOf(nbtPayload, nbtPayload.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the X position of the entity relative to the schematic anchor.
     *
     * @return X coordinate (IEEE 754 f64)
     */
    public double positionX() {
        return positionX;
    }

    /**
     * Returns the Y position of the entity relative to the schematic anchor.
     *
     * @return Y coordinate (IEEE 754 f64)
     */
    public double positionY() {
        return positionY;
    }

    /**
     * Returns the Z position of the entity relative to the schematic anchor.
     *
     * @return Z coordinate (IEEE 754 f64)
     */
    public double positionZ() {
        return positionZ;
    }

    /**
     * Returns the yaw rotation of the entity in degrees.
     *
     * @return yaw (IEEE 754 f32)
     */
    public float yaw() {
        return yaw;
    }

    /**
     * Returns the pitch rotation of the entity in degrees.
     *
     * @return pitch (IEEE 754 f32)
     */
    public float pitch() {
        return pitch;
    }

    /**
     * Returns the entity type identifier string (e.g. {@code "minecraft:armor_stand"}).
     * This value is opaque UTF-8 — it is never validated against a registry in msf-core.
     *
     * @return entity type identifier
     */
    public String entityType() {
        return entityType;
    }

    /**
     * Returns a defensive copy of the raw binary NBT payload.
     *
     * <p>The payload excludes the {@code id} tag (entity type is in {@link #entityType()})
     * and UUID tags (stripped by the writer per Section 8.2). The bytes are raw binary NBT —
     * never deserialized in msf-core.
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
        if (!(o instanceof MsfEntity other)) return false;
        return Double.compare(positionX, other.positionX) == 0
            && Double.compare(positionY, other.positionY) == 0
            && Double.compare(positionZ, other.positionZ) == 0
            && Float.compare(yaw, other.yaw) == 0
            && Float.compare(pitch, other.pitch) == 0
            && entityType.equals(other.entityType)
            && Arrays.equals(nbtPayload, other.nbtPayload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(positionX, positionY, positionZ, yaw, pitch, entityType);
        result = 31 * result + Arrays.hashCode(nbtPayload);
        return result;
    }

    @Override
    public String toString() {
        return "MsfEntity{type=" + entityType
            + ", pos=(" + positionX + "," + positionY + "," + positionZ + ")"
            + ", nbtBytes=" + nbtPayload.length + "}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for constructing {@link MsfEntity} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MsfEntity}.
     */
    public static final class Builder {

        private double positionX = 0.0;
        private double positionY = 0.0;
        private double positionZ = 0.0;
        private float yaw = 0.0f;
        private float pitch = 0.0f;
        private String entityType = "";
        private byte[] nbtPayload = new byte[0];

        private Builder() {}

        /**
         * Sets all three position coordinates.
         *
         * @param x X coordinate
         * @param y Y coordinate
         * @param z Z coordinate
         * @return this builder
         */
        public Builder position(double x, double y, double z) {
            this.positionX = x;
            this.positionY = y;
            this.positionZ = z;
            return this;
        }

        /**
         * Sets the yaw and pitch rotation values.
         *
         * @param yaw   yaw rotation in degrees
         * @param pitch pitch rotation in degrees
         * @return this builder
         */
        public Builder rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        /**
         * Sets the entity type identifier string (e.g. {@code "minecraft:armor_stand"}).
         *
         * @param entityType opaque UTF-8 entity type identifier
         * @return this builder
         */
        public Builder entityType(String entityType) {
            this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
            return this;
        }

        /**
         * Sets the raw binary NBT payload.
         *
         * <p>The payload MUST NOT include the {@code id} tag or UUID tags — those are
         * handled by the writer. A defensive copy is made.
         *
         * @param nbtPayload raw binary NBT bytes; must not be {@code null}; may be empty
         * @return this builder
         */
        public Builder nbtPayload(byte[] nbtPayload) {
            this.nbtPayload = Objects.requireNonNull(nbtPayload, "nbtPayload must not be null").clone();
            return this;
        }

        /**
         * Builds and returns the {@link MsfEntity} instance.
         *
         * @return a new {@link MsfEntity}
         * @throws IllegalArgumentException if the NBT payload exceeds
         *                                  {@value MsfEntity#MAX_NBT_PAYLOAD_BYTES} bytes
         */
        public MsfEntity build() {
            // Spec Section 8.2: NBT payload MUST NOT exceed 65535 bytes
            if (nbtPayload.length > MAX_NBT_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(String.format(
                    "Entity '%s' NBT payload size %d exceeds maximum permitted value of %d",
                    entityType, nbtPayload.length, MAX_NBT_PAYLOAD_BYTES
                ));
            }
            return new MsfEntity(positionX, positionY, positionZ, yaw, pitch, entityType, nbtPayload);
        }
    }
}

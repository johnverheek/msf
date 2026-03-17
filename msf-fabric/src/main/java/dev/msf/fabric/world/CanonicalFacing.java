package dev.msf.fabric.world;

import dev.msf.core.model.MsfMetadata;

/**
 * The four cardinal horizontal directions a schematic can face.
 *
 * <p>Values mirror {@link MsfMetadata#FACING_NORTH} etc. (Section 10.2).
 * Used to compute rotation transforms when placing a schematic in a different
 * orientation than it was authored.
 *
 * @see MsfMetadata#canonicalFacing()
 * @see PlacementOptions
 */
public enum CanonicalFacing {

    NORTH(MsfMetadata.FACING_NORTH),
    SOUTH(MsfMetadata.FACING_SOUTH),
    EAST(MsfMetadata.FACING_EAST),
    WEST(MsfMetadata.FACING_WEST);

    private final int msfValue;

    CanonicalFacing(int msfValue) {
        this.msfValue = msfValue;
    }

    /** The MSF canonical facing byte value for this direction (Section 10.2). */
    public int msfValue() {
        return msfValue;
    }

    /**
     * Looks up a {@code CanonicalFacing} from its MSF byte value.
     *
     * @param value MSF canonical facing value (0–3)
     * @return the corresponding enum constant
     * @throws IllegalArgumentException if {@code value} is not in [0, 3]
     */
    public static CanonicalFacing fromMsfValue(int value) {
        for (CanonicalFacing f : values()) {
            if (f.msfValue == value) return f;
        }
        throw new IllegalArgumentException(
            "Invalid canonical facing value " + value + "; expected 0 (North), 1 (South), 2 (East), or 3 (West)"
        );
    }

    /**
     * Returns the facing that is {@code cwSteps} clockwise 90° steps from this one.
     *
     * <p>Wraps modulo 4; negative values and values greater than 3 are handled correctly.
     * For example, {@code NORTH.rotateClockwise(1)} returns {@link #EAST}.
     *
     * @param cwSteps number of clockwise 90° steps to rotate (any integer)
     * @return the facing after rotation
     */
    public CanonicalFacing rotateClockwise(int cwSteps) {
        int target = ((cwOrdinal() + cwSteps) % 4 + 4) % 4;
        for (CanonicalFacing f : values()) {
            if (f.cwOrdinal() == target) return f;
        }
        throw new AssertionError("unreachable");
    }

    /**
     * Returns the clockwise ordinal of this direction (North=0, East=1, South=2, West=3).
     * Used to compute the {@link net.minecraft.util.BlockRotation} delta between two facings.
     */
    int cwOrdinal() {
        return switch (this) {
            case NORTH -> 0;
            case EAST  -> 1;
            case SOUTH -> 2;
            case WEST  -> 3;
        };
    }
}

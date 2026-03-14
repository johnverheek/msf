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
     * Looks up a {@code CanonicalFacing} from a clockwise ordinal (North=0, East=1, South=2, West=3).
     * The input is normalised modulo 4, so values outside [0,3] wrap correctly.
     *
     * @param ordinal clockwise ordinal of the target direction
     * @return the corresponding enum constant
     */
    public static CanonicalFacing fromCwOrdinal(int ordinal) {
        return switch (((ordinal % 4) + 4) % 4) {
            case 0 -> NORTH;
            case 1 -> EAST;
            case 2 -> SOUTH;
            default -> WEST;
        };
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

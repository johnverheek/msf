package dev.msf.fabric.world;

import net.minecraft.util.BlockMirror;

/**
 * The two horizontal mirror axes supported for schematic placement (Section 10.3).
 *
 * <p>Mirrors are applied <em>after</em> rotation. The axis label refers to the axis
 * the reflection plane is perpendicular to (matching Minecraft's own naming):
 * <ul>
 *   <li>{@link #NONE} — no mirroring.</li>
 *   <li>{@link #X}    — mirror through the X axis (flips Z coordinates).</li>
 *   <li>{@link #Z}    — mirror through the Z axis (flips X coordinates).</li>
 * </ul>
 *
 * @see PlacementOptions#mirror()
 * @see RegionPlacer
 */
public enum Mirror {

    /** No mirroring applied. */
    NONE(BlockMirror.NONE),

    /** Mirror across the X axis — flips the Z coordinate. */
    X(BlockMirror.LEFT_RIGHT),

    /** Mirror across the Z axis — flips the X coordinate. */
    Z(BlockMirror.FRONT_BACK);

    private final BlockMirror blockMirror;

    Mirror(BlockMirror blockMirror) {
        this.blockMirror = blockMirror;
    }

    /**
     * Returns the Minecraft {@link BlockMirror} constant corresponding to this mirror.
     * Used to apply the mirror transform to block states via
     * {@link net.minecraft.block.BlockState#mirror(BlockMirror)}.
     */
    public BlockMirror toBlockMirror() {
        return blockMirror;
    }
}

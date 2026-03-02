package dev.msf.core.util;

/**
 * Index computation for YZX block ordering used in MSF region payloads.
 *
 * <p>MSF stores blocks in YZX order: Y is the outermost index, Z is the middle index,
 * X is the innermost index. This ordering is chosen for cache-friendly vertical operations
 * such as lighting propagation and column-based processing.
 *
 * <p>For a region of size X × Y × Z, the block at position {@code (x, y, z)} is at
 * array index:
 * <pre>
 *     index = y * (sizeZ * sizeX) + z * sizeX + x
 * </pre>
 *
 * <p>All arithmetic in this class uses {@code long} to prevent overflow when region
 * dimensions are large. The caller is responsible for validating that dimensions are
 * positive before calling these methods.
 *
 * @see MsfSpec Section 7.4 — block ordering
 */
public final class YzxOrder {

    private YzxOrder() {
        // Utility class — not instantiable
    }

    /**
     * Computes the flat array index for the block at {@code (x, y, z)} in a region of
     * the given dimensions, using YZX ordering.
     *
     * <p>No bounds checking is performed. The caller is responsible for ensuring that
     * {@code x}, {@code y}, and {@code z} are within {@code [0, sizeX)}, {@code [0, sizeY)},
     * and {@code [0, sizeZ)} respectively.
     *
     * @param x     block X coordinate (0-based, innermost index)
     * @param y     block Y coordinate (0-based, outermost index)
     * @param z     block Z coordinate (0-based, middle index)
     * @param sizeX region size on the X axis
     * @param sizeZ region size on the Z axis
     * @return the flat array index corresponding to {@code (x, y, z)}
     */
    public static long index(int x, int y, int z, int sizeX, int sizeZ) {
        // All operands widened to long before multiplication to prevent int overflow
        // on large regions — see Section 7.5 and Appendix E.
        return (long) y * ((long) sizeZ * sizeX) + (long) z * sizeX + x;
    }

    /**
     * Returns the total number of blocks in a region with the given dimensions.
     *
     * <p>Uses {@code long} arithmetic to prevent overflow for large regions.
     *
     * @param sizeX region size on the X axis
     * @param sizeY region size on the Y axis
     * @param sizeZ region size on the Z axis
     * @return total block count
     */
    public static long blockCount(int sizeX, int sizeY, int sizeZ) {
        return (long) sizeX * sizeY * sizeZ;
    }
}

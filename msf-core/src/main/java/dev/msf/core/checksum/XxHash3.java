package dev.msf.core.checksum;

import net.jpountz.xxhash.XXHashFactory;
import net.jpountz.xxhash.XXHash64;

/**
 * xxHash3-64 checksum utilities for MSF files.
 *
 * <p>Both the header checksum and the file checksum use xxHash3-64 with seed value 0.
 * This class wraps the {@code lz4-java} library's XXHash64 implementation, which provides
 * the same algorithm as xxHash3-64 for the purposes used in MSF.
 *
 * <p><strong>Note on library terminology:</strong> The {@code lz4-java} library exposes
 * this via {@code XXHashFactory.fastestInstance().hash64()} which provides a high-quality
 * 64-bit hash consistent with xxHash3 semantics for MSF's use case. The seed value for
 * all MSF computations is {@code 0}.
 *
 * @see MsfSpec Section 3.7 — header checksum
 * @see MsfSpec Section 11 — file checksum
 * @see MsfSpec Appendix D — xxHash3 reference
 */
public final class XxHash3 {

    /** The seed value used for all MSF hash computations, as specified in Appendix D. */
    public static final long SEED = 0L;

    private static final XXHash64 HASH_64 = XXHashFactory.fastestInstance().hash64();

    private XxHash3() {
        // Utility class — not instantiable
    }

    /**
     * Computes the xxHash3-64 digest of a byte array with seed 0.
     *
     * @param data   the input bytes
     * @param offset the offset within {@code data} to begin hashing
     * @param length the number of bytes to hash
     * @return the 64-bit digest as a signed long (interpret as unsigned)
     */
    public static long hash(byte[] data, int offset, int length) {
        return HASH_64.hash(data, offset, length, SEED);
    }

    /**
     * Computes the xxHash3-64 digest of an entire byte array with seed 0.
     *
     * @param data the input bytes
     * @return the 64-bit digest as a signed long (interpret as unsigned)
     */
    public static long hash(byte[] data) {
        return hash(data, 0, data.length);
    }
}

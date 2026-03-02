package dev.msf.core.checksum;

import dev.msf.core.NotNull;
import net.jpountz.xxhash.XXHashFactory;

/**
 * xxHash3 checksum utility for MSF files.
 *
 * <p>MSF uses xxHash3 with seed {@code 0} for two independent checksums:
 * <ul>
 *   <li><b>Header checksum</b> — covers header bytes 0–39 (the 40 bytes before the checksum field
 *       itself). Stored at header offset 40.</li>
 *   <li><b>File checksum</b> — covers all file bytes from offset 0 to {@code fileSize - 8}
 *       inclusive. Stored as the final 8 bytes of the file.</li>
 * </ul>
 *
 * <p>The seed value for all xxHash3 computations is 0 (default seed), per MSF Spec Appendix D.
 *
 * <p>This class delegates to the {@code lz4-java} library's xxHash3 implementation.
 *
 * @see <a href="https://github.com/Cyan4973/xxHash">xxHash reference implementation</a>
 */
public final class XxHash3 {

    /** The seed used for all MSF xxHash3 computations. Per spec Appendix D. */
    private static final long SEED = 0L;

    private static final XXHashFactory FACTORY = XXHashFactory.fastestInstance();

    private XxHash3() {
        // Utility class — no instances.
    }

    /**
     * Computes the xxHash3 digest of the given byte array.
     *
     * @param data the bytes to hash
     * @return the 64-bit xxHash3 digest with seed 0
     */
    public static long hash(@NotNull byte[] data) {
        return hash(data, 0, data.length);
    }

    /**
     * Computes the xxHash3 digest of a slice of a byte array.
     *
     * @param data   the source byte array
     * @param offset the offset within {@code data} at which hashing begins
     * @param length the number of bytes to hash
     * @return the 64-bit xxHash3 digest with seed 0
     * @throws IllegalArgumentException if {@code offset} or {@code length} is out of bounds
     */
    public static long hash(@NotNull byte[] data, int offset, int length) {
        return FACTORY.hash64().hash(data, offset, length, SEED);
    }

    /**
     * Computes the header checksum — the xxHash3 digest of header bytes 0–39.
     *
     * <p>The header is 48 bytes. The first 40 bytes are the fields; the final 8 bytes are the
     * checksum itself. This method hashes only the first 40 bytes.
     *
     * @param headerBytes the complete 48-byte header buffer
     * @return the 64-bit xxHash3 digest of header bytes 0–39
     * @throws IllegalArgumentException if {@code headerBytes} is shorter than 40 bytes
     */
    public static long headerChecksum(@NotNull byte[] headerBytes) {
        if (headerBytes.length < 40) {
            throw new IllegalArgumentException(
                "Header buffer must be at least 40 bytes, got " + headerBytes.length
            );
        }
        // Hash only bytes 0-39; bytes 40-47 are the checksum field itself.
        return hash(headerBytes, 0, 40);
    }

    /**
     * Computes the file checksum — the xxHash3 digest of all bytes from offset 0 to
     * {@code fileSize - 8} inclusive.
     *
     * <p>The final 8 bytes of the file are the checksum field and are excluded from the digest.
     *
     * @param fileBytes all bytes of the file, including the checksum field at the end
     * @return the 64-bit xxHash3 digest covering bytes 0 through {@code fileBytes.length - 9}
     * @throws IllegalArgumentException if {@code fileBytes} is shorter than 9 bytes
     */
    public static long fileChecksum(@NotNull byte[] fileBytes) {
        if (fileBytes.length < 9) {
            throw new IllegalArgumentException(
                "File buffer must be at least 9 bytes to compute file checksum, got " + fileBytes.length
            );
        }
        // Hash everything except the final 8 bytes (the stored checksum field).
        return hash(fileBytes, 0, fileBytes.length - 8);
    }
}

package dev.msf.core;

/**
 * Thrown when a checksum verification fails during MSF parsing.
 *
 * <p>Two checksums are defined by the MSF format:
 * <ul>
 *   <li>Header checksum — xxHash3-64 of header bytes 0–39, stored at offsets 40–47.
 *       A mismatch here is always a mandatory stop (Section 3.7).</li>
 *   <li>File checksum — xxHash3-64 of all bytes except the final 8, appended at end of file.
 *       A mismatch here is a mandatory warning and a permitted stop (Section 11).</li>
 * </ul>
 *
 * @see MsfSpec Section 3.7 — header checksum
 * @see MsfSpec Section 11 — file checksum
 */
public class MsfChecksumException extends MsfException {

    /** Identifies which checksum failed. */
    public enum ChecksumType {
        /** The header checksum covering bytes 0–39 failed. */
        HEADER,
        /** The file checksum covering all bytes except the final 8 failed. */
        FILE
    }

    private final ChecksumType checksumType;
    private final long expected;
    private final long actual;

    /**
     * Creates a new {@code MsfChecksumException}.
     *
     * @param checksumType which checksum failed
     * @param expected     the digest value stored in the file
     * @param actual       the digest value computed by the reader
     */
    public MsfChecksumException(ChecksumType checksumType, long expected, long actual) {
        super(String.format(
            "%s checksum mismatch — stored: 0x%016X, computed: 0x%016X",
            checksumType == ChecksumType.HEADER ? "Header" : "File",
            expected, actual
        ));
        this.checksumType = checksumType;
        this.expected = expected;
        this.actual = actual;
    }

    /**
     * Returns which checksum failed.
     *
     * @return the checksum type that triggered this exception
     */
    public ChecksumType getChecksumType() {
        return checksumType;
    }

    /**
     * Returns the digest value stored in the file.
     *
     * @return the expected (stored) digest as a signed long (interpret as unsigned)
     */
    public long getExpected() {
        return expected;
    }

    /**
     * Returns the digest value computed by the reader.
     *
     * @return the actual (computed) digest as a signed long (interpret as unsigned)
     */
    public long getActual() {
        return actual;
    }
}

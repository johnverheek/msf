package dev.msf.core;

/**
 * Thrown when an MSF checksum verification fails.
 *
 * <p>MSF uses xxHash3 checksums in two places:
 * <ul>
 *   <li><b>Header checksum</b> — covers bytes 0–39 of the header. Verified first, before any
 *       other header field is trusted. A failure here indicates header corruption and the reader
 *       MUST stop immediately.</li>
 *   <li><b>File checksum</b> — covers all file bytes except the final 8 bytes. A failure here
 *       indicates data corruption. Readers MUST warn the user and MAY offer to continue at the
 *       user's explicit request.</li>
 * </ul>
 *
 * @see dev.msf.core.checksum.XxHash3
 */
public class MsfChecksumException extends MsfException {

    /** Whether this failure is in the header checksum ({@code true}) or file checksum ({@code false}). */
    private final boolean headerChecksum;

    /** The checksum value computed from the actual data. */
    private final long computed;

    /** The checksum value stored in the file. */
    private final long stored;

    /**
     * Constructs an {@code MsfChecksumException}.
     *
     * @param headerChecksum {@code true} if the header checksum failed, {@code false} if the
     *                       file checksum failed
     * @param computed       the xxHash3 value computed from the data
     * @param stored         the xxHash3 value stored in the file
     */
    public MsfChecksumException(boolean headerChecksum, long computed, long stored) {
        super(String.format(
            "%s checksum mismatch — computed 0x%016X, stored 0x%016X",
            headerChecksum ? "Header" : "File",
            computed, stored
        ));
        this.headerChecksum = headerChecksum;
        this.computed = computed;
        this.stored = stored;
    }

    /**
     * Returns {@code true} if this exception was caused by a header checksum failure.
     * Returns {@code false} if it was caused by a file checksum failure.
     *
     * @return {@code true} for header checksum failure, {@code false} for file checksum failure
     */
    public boolean isHeaderChecksum() {
        return headerChecksum;
    }

    /**
     * Returns the xxHash3 digest computed from the actual file bytes.
     *
     * @return the computed checksum value
     */
    public long getComputed() {
        return computed;
    }

    /**
     * Returns the xxHash3 digest stored in the file.
     *
     * @return the stored checksum value
     */
    public long getStored() {
        return stored;
    }
}

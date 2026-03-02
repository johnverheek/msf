package dev.msf.core.io;

/**
 * Configuration options for {@link MsfReader}.
 *
 * <p>The default configuration matches the normative behaviour described in the MSF specification:
 * file checksum failures throw {@link dev.msf.core.MsfChecksumException} after emitting a warning.
 *
 * <p>Use {@link #allowChecksumFailure()} to obtain a configuration that continues past file
 * checksum failures at the caller's explicit request, as permitted by Section 11.
 *
 * @param continueOnFileChecksumFailure if {@code true}, the reader continues parsing after a
 *                                      file checksum failure rather than throwing
 *                                      {@link dev.msf.core.MsfChecksumException}; the warning
 *                                      is still emitted in both cases
 *
 * @see MsfSpec Section 11 — file checksum
 * @see MsfSpec Section 12.3 — universal V1 compatibility (permitted rejection)
 */
public record MsfReaderConfig(boolean continueOnFileChecksumFailure) {

    /** Default configuration: throw on file checksum failure after emitting a warning. */
    public static final MsfReaderConfig DEFAULT = new MsfReaderConfig(false);

    /**
     * Returns a configuration that allows the reader to continue past a file checksum failure.
     *
     * <p>Use this only when the caller explicitly understands and accepts the risk of reading
     * potentially corrupted data.
     *
     * @return a config with {@code continueOnFileChecksumFailure = true}
     */
    public static MsfReaderConfig allowChecksumFailure() {
        return new MsfReaderConfig(true);
    }
}

package dev.msf.core;

/**
 * Thrown when an MSF file declares a major version that this reader does not support.
 *
 * <p>Minor version differences MUST NOT produce this exception. Per MSF Spec Section 3.2,
 * a V1 reader must attempt to read any V1 file regardless of minor version. This exception
 * is reserved exclusively for major version mismatches (e.g. a V2 file read by a V1 reader).
 *
 * <p>When thrown, the reader MUST stop immediately and MUST NOT attempt to read further.
 * See MSF Spec Section 12.3 — reader failure precedence.
 */
public class MsfVersionException extends MsfException {

    /** The major version declared in the file that triggered this exception. */
    private final int fileMajorVersion;

    /** The maximum major version this reader supports. */
    private final int supportedMajorVersion;

    /**
     * Constructs an {@code MsfVersionException}.
     *
     * @param fileMajorVersion      the major version declared in the MSF file
     * @param supportedMajorVersion the major version this reader supports
     */
    public MsfVersionException(int fileMajorVersion, int supportedMajorVersion) {
        super(String.format(
            "Unsupported MSF major version %d (this reader supports major version %d)",
            fileMajorVersion, supportedMajorVersion
        ));
        this.fileMajorVersion = fileMajorVersion;
        this.supportedMajorVersion = supportedMajorVersion;
    }

    /**
     * Returns the major version declared in the MSF file that triggered this exception.
     *
     * @return the file's major version
     */
    public int getFileMajorVersion() {
        return fileMajorVersion;
    }

    /**
     * Returns the maximum major version this reader supports.
     *
     * @return the supported major version
     */
    public int getSupportedMajorVersion() {
        return supportedMajorVersion;
    }
}

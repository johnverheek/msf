package dev.msf.core;

/**
 * Thrown when an MSF file declares a major version that this reader does not support.
 *
 * <p>Per spec Section 3.2, a reader implementing V1 MUST throw this exception when the
 * major version field contains any value other than 1, including 0. The exception message
 * MUST include both the major version found in the file and the major version supported
 * by the reader. No partial read result is valid when this exception is thrown.
 *
 * @see MsfSpec Section 3.2 — version fields
 */
public class MsfVersionException extends MsfException {

    private static final long serialVersionUID = 1L;

    /** The major version found in the file. */
    private final int foundVersion;

    /** The major version supported by this reader. */
    private final int supportedVersion;

    /**
     * Creates a new {@code MsfVersionException}.
     *
     * @param foundVersion     the major version declared in the file header
     * @param supportedVersion the major version this reader supports
     */
    public MsfVersionException(int foundVersion, int supportedVersion) {
        super(String.format(
            "Unsupported MSF major version %d — this reader supports major version %d only",
            foundVersion, supportedVersion
        ));
        this.foundVersion = foundVersion;
        this.supportedVersion = supportedVersion;
    }

    /**
     * Returns the major version declared in the file header.
     *
     * @return the major version found in the file
     */
    public int getFoundVersion() {
        return foundVersion;
    }

    /**
     * Returns the major version supported by this reader.
     *
     * @return the major version this reader supports
     */
    public int getSupportedVersion() {
        return supportedVersion;
    }
}

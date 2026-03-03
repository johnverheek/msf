package dev.msf.core;

/**
 * Thrown when an MSF file has a malformed or structurally invalid structure.
 *
 * <p>Examples include: truncated files, invalid magic bytes, mismatched length fields,
 * out-of-range palette IDs, and invalid field values that cannot be recovered from.
 *
 * @see MsfSpec Section 3.1 — magic bytes
 * @see MsfSpec Section 3.5 — block offsets
 * @see MsfSpec Section 7.5 — bit packing validation
 */
public class MsfParseException extends MsfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@code MsfParseException} with the given message.
     *
     * @param message human-readable description of the parse error, including relevant context
     *                such as the file byte offset, expected value, and actual value
     */
    public MsfParseException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code MsfParseException} with the given message and cause.
     *
     * @param message human-readable description of the parse error
     * @param cause   the underlying exception that triggered this error
     */
    public MsfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

package dev.msf.core;

/**
 * Base checked exception for all MSF format errors.
 *
 * <p>All MSF-specific exceptions extend this class. Callers that wish to catch any MSF error
 * without distinguishing between subtypes may catch {@code MsfException} directly.
 *
 * @see MsfParseException
 * @see MsfVersionException
 * @see MsfChecksumException
 * @see MsfPaletteException
 * @see MsfCompressionException
 */
public class MsfException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@code MsfException} with the given message.
     *
     * @param message human-readable description of the error
     */
    public MsfException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code MsfException} with the given message and cause.
     *
     * @param message human-readable description of the error
     * @param cause   the underlying exception that triggered this error
     */
    public MsfException(String message, Throwable cause) {
        super(message, cause);
    }
}

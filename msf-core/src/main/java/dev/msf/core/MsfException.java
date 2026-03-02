package dev.msf.core;

/**
 * Root checked exception for all MSF format errors.
 *
 * <p>All MSF-specific errors are subtypes of this class. Callers that need to handle any
 * MSF error generically can catch {@code MsfException}. Callers that need to distinguish
 * between specific failure modes should catch the appropriate subtype.
 *
 * <p>IO errors that are not specific to the MSF format are reported as {@link java.io.IOException}
 * and are not wrapped in {@code MsfException}.
 *
 * @see MsfParseException
 * @see MsfVersionException
 * @see MsfChecksumException
 * @see MsfPaletteException
 * @see MsfCompressionException
 */
public class MsfException extends Exception {

    /**
     * Constructs an {@code MsfException} with the specified detail message.
     *
     * @param message the detail message
     */
    public MsfException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs an {@code MsfException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public MsfException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}

package dev.msf.core;

/**
 * Thrown when a compression or decompression operation fails.
 *
 * <p>Examples include: decompressed output length mismatch, unrecognized compression
 * type codes, and underlying codec failures for zstd, lz4, or brotli.
 *
 * @see MsfSpec Section 7.2 — compression types
 */
public class MsfCompressionException extends MsfException {

    /**
     * Creates a new {@code MsfCompressionException} with the given message.
     *
     * @param message human-readable description of the compression error
     */
    public MsfCompressionException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code MsfCompressionException} with the given message and cause.
     *
     * @param message human-readable description of the compression error
     * @param cause   the underlying exception that triggered this error
     */
    public MsfCompressionException(String message, Throwable cause) {
        super(message, cause);
    }
}

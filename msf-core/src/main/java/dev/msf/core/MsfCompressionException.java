package dev.msf.core;

import dev.msf.core.compression.CompressionType;

/**
 * Thrown when a region compression or decompression operation fails.
 *
 * <p>Examples of conditions that produce this exception:
 * <ul>
 *   <li>Decompression of a region payload fails (corrupted compressed data)</li>
 *   <li>Compression type declared in the region header is not supported by this reader</li>
 *   <li>Decompressed size does not match the expected uncompressed length</li>
 * </ul>
 *
 * <p>See MSF Spec Section 7.2 — compression types.
 */
public class MsfCompressionException extends MsfException {

    /** The compression type involved in the failure, if known. */
    @Nullable
    private final CompressionType compressionType;

    /**
     * Constructs an {@code MsfCompressionException} with a detail message.
     *
     * @param message the detail message
     */
    public MsfCompressionException(@NotNull String message) {
        super(message);
        this.compressionType = null;
    }

    /**
     * Constructs an {@code MsfCompressionException} with a detail message and the compression
     * type that was being used when the failure occurred.
     *
     * @param message         the detail message
     * @param compressionType the compression type involved in the failure
     */
    public MsfCompressionException(@NotNull String message, @Nullable CompressionType compressionType) {
        super(message + (compressionType != null ? " (compression: " + compressionType + ")" : ""));
        this.compressionType = compressionType;
    }

    /**
     * Constructs an {@code MsfCompressionException} with a detail message, compression type,
     * and underlying cause.
     *
     * @param message         the detail message
     * @param compressionType the compression type involved in the failure
     * @param cause           the underlying cause
     */
    public MsfCompressionException(
            @NotNull String message,
            @Nullable CompressionType compressionType,
            @NotNull Throwable cause) {
        super(message + (compressionType != null ? " (compression: " + compressionType + ")" : ""), cause);
        this.compressionType = compressionType;
    }

    /**
     * Returns the compression type that was in use when this failure occurred, if known.
     *
     * @return the compression type, or empty if not applicable
     */
    @NotNull
    public java.util.Optional<CompressionType> getCompressionType() {
        return java.util.Optional.ofNullable(compressionType);
    }
}

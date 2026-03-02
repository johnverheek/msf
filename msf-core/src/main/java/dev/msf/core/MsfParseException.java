package dev.msf.core;

/**
 * Thrown when an MSF file has a malformed structure that prevents parsing.
 *
 * <p>Examples of conditions that produce this exception:
 * <ul>
 *   <li>Magic bytes at offset 0 do not match {@code MSF!} (0x4D 0x53 0x46 0x21)</li>
 *   <li>A required block is missing or has an invalid length prefix</li>
 *   <li>A string field contains invalid UTF-8</li>
 *   <li>A field value is outside its valid range</li>
 * </ul>
 *
 * <p>When a magic byte mismatch is detected, the reader MUST throw this exception immediately
 * and MUST NOT read further into the file. See MSF Spec Section 12.3 — reader failure precedence.
 */
public class MsfParseException extends MsfException {

    /** File offset at which the parse failure occurred, or {@code -1} if unknown. */
    private final long offset;

    /**
     * Constructs an {@code MsfParseException} with the specified message and file offset.
     *
     * @param message the detail message describing the parse failure
     * @param offset  the file offset at which the failure occurred, or {@code -1} if unknown
     */
    public MsfParseException(@NotNull String message, long offset) {
        super(message + " (offset " + offset + ")");
        this.offset = offset;
    }

    /**
     * Constructs an {@code MsfParseException} with a message, file offset, and cause.
     *
     * @param message the detail message
     * @param offset  the file offset at which the failure occurred, or {@code -1} if unknown
     * @param cause   the underlying cause
     */
    public MsfParseException(@NotNull String message, long offset, @NotNull Throwable cause) {
        super(message + " (offset " + offset + ")", cause);
        this.offset = offset;
    }

    /**
     * Returns the file offset at which parsing failed.
     *
     * @return the byte offset into the file, or {@code -1} if the offset is unknown
     */
    public long getOffset() {
        return offset;
    }
}

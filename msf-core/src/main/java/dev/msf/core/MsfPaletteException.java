package dev.msf.core;

/**
 * Thrown when a palette encoding or lookup error occurs.
 *
 * <p>Examples include: duplicate palette entries, invalid blockstate strings,
 * out-of-range palette IDs in block data, and palette entry counts that exceed
 * the u16 maximum of 65535.
 *
 * @see MsfSpec Section 4.3 — normative palette requirements
 * @see MsfSpec Section 7.5 — palette ID validation in block data
 */
public class MsfPaletteException extends MsfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@code MsfPaletteException} with the given message.
     *
     * @param message human-readable description of the palette error
     */
    public MsfPaletteException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code MsfPaletteException} with the given message and cause.
     *
     * @param message human-readable description of the palette error
     * @param cause   the underlying exception that triggered this error
     */
    public MsfPaletteException(String message, Throwable cause) {
        super(message, cause);
    }
}

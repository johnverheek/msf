package dev.msf.core;

/**
 * Thrown when a palette encoding or lookup operation fails.
 *
 * <p>Examples of conditions that produce this exception:
 * <ul>
 *   <li>Palette ID 0 does not map to {@code minecraft:air} (format invariant violation)</li>
 *   <li>A block data array references a palette ID that does not exist in the palette</li>
 *   <li>Duplicate blockstate strings are detected during write</li>
 *   <li>The palette exceeds the maximum of 65535 entries</li>
 *   <li>A blockstate string is not valid UTF-8</li>
 * </ul>
 *
 * <p>See MSF Spec Section 4.3 — palette normative requirements.
 */
public class MsfPaletteException extends MsfException {

    /**
     * Constructs an {@code MsfPaletteException} with the specified detail message.
     *
     * @param message the detail message describing the palette error
     */
    public MsfPaletteException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs an {@code MsfPaletteException} with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public MsfPaletteException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}

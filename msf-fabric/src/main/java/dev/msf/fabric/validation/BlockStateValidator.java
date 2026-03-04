package dev.msf.fabric.validation;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.MsfWarning;
import dev.msf.core.model.MsfPalette;
import dev.msf.fabric.bridge.BlockStateBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Validates that all entries in an {@link MsfPalette} can be resolved against
 * the current game version's block registry.
 *
 * <p>Validation is advisory — this class never throws. Invalid entries are
 * returned in a list and optionally surfaced via a warning consumer.
 *
 * <p>Palette ID 0 ({@code "minecraft:air"}) is always considered valid and
 * is skipped without a registry lookup (Section 4.3).
 */
public final class BlockStateValidator {

    private BlockStateValidator() {}

    /**
     * Validates all entries in {@code palette} against the current game's block registry.
     *
     * @param palette         the palette to validate
     * @param warningConsumer receives a {@link MsfWarning.Code#DATA_VERSION_MISMATCH}
     *                        warning for each invalid entry; may be {@code null}
     * @return an unmodifiable list of blockstate strings that could not be resolved;
     *         empty if all entries are valid
     */
    public static List<String> validate(
        MsfPalette palette,
        Consumer<MsfWarning> warningConsumer
    ) {
        List<String> invalid = new ArrayList<>();

        for (String entry : palette.entries()) {
            // Section 4.3 — palette ID 0 is always air; no lookup needed
            if (MsfPalette.AIR.equals(entry)) {
                continue;
            }
            try {
                BlockStateBridge.fromString(entry);
            } catch (MsfPaletteException e) {
                invalid.add(entry);
                if (warningConsumer != null) {
                    warningConsumer.accept(MsfWarning.writeWarning(
                        MsfWarning.Code.DATA_VERSION_MISMATCH,
                        "Palette entry '" + entry
                        + "' is not valid for the current game version: " + e.getMessage()
                    ));
                }
            }
        }

        return List.copyOf(invalid);
    }
}

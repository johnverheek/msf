package dev.msf.fabric.validation;

import dev.msf.core.MsfWarning;
import dev.msf.core.model.MsfHeader;
import net.minecraft.SharedConstants;

import java.util.function.Consumer;

/**
 * Compares the MC data version stored in an MSF file header against the
 * currently running game version and emits a warning if they differ.
 *
 * <p>This check is advisory — it never throws. The caller decides how to act
 * on a {@link MsfWarning.Code#DATA_VERSION_MISMATCH} warning (e.g. prompt the
 * user, refuse to place, attempt NBT migration).
 *
 * @see MsfSpec Section 3.4 — MC data version
 */
public final class DataVersionChecker {

    private DataVersionChecker() {}

    /**
     * Emits {@link MsfWarning.Code#DATA_VERSION_MISMATCH} if the file's MC data
     * version differs from the current game's data version.
     *
     * <p>The current game data version is obtained from
     * {@code SharedConstants.getGameVersion().dataVersion().id()}.
     *
     * @param header          the MSF file header containing {@link MsfHeader#mcDataVersion()}
     * @param warningConsumer receives the warning if versions differ; may be {@code null}
     *                        (in which case the check still runs but produces no output)
     */
    public static void check(MsfHeader header, Consumer<MsfWarning> warningConsumer) {
        if (warningConsumer == null) {
            return;
        }
        int currentDataVersion = SharedConstants.getGameVersion().dataVersion().id();
        long fileDataVersion = header.mcDataVersion();

        if (fileDataVersion != currentDataVersion) {
            warningConsumer.accept(MsfWarning.writeWarning(
                MsfWarning.Code.DATA_VERSION_MISMATCH,
                String.format(
                    "File MC data version %d differs from current game version %d — "
                    + "blockstate strings and entity NBT may be incompatible (Section 3.4)",
                    fileDataVersion, currentDataVersion
                )
            ));
        }
    }
}

package dev.msf.fabric.test;

import dev.msf.core.MsfWarning;
import dev.msf.core.model.MsfHeader;
import dev.msf.fabric.validation.DataVersionChecker;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for {@link DataVersionChecker} — version match / mismatch warning emission.
 */
public class DataVersionCheckerTest implements FabricGameTest {

    private static int currentDataVersion() {
        return SharedConstants.getGameVersion().getSaveVersion().getId();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matchingVersionEmitsNoWarning(TestContext ctx) {
        int version = currentDataVersion();
        MsfHeader header = MsfHeader.builder().mcDataVersion(version).build();

        List<MsfWarning> warnings = new ArrayList<>();
        DataVersionChecker.check(header, warnings::add);

        ctx.assertTrue(warnings.isEmpty(),
            "Expected no warnings for matching data version, got: " + warnings);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mismatchVersionEmitsWarning(TestContext ctx) {
        int version = currentDataVersion();
        MsfHeader header = MsfHeader.builder().mcDataVersion(version + 9999L).build();

        List<MsfWarning> warnings = new ArrayList<>();
        DataVersionChecker.check(header, warnings::add);

        ctx.assertTrue(!warnings.isEmpty(), "Expected DATA_VERSION_MISMATCH warning, got none");
        ctx.assertTrue(
            warnings.stream().anyMatch(w -> w.code() == MsfWarning.Code.DATA_VERSION_MISMATCH),
            "Expected DATA_VERSION_MISMATCH code, got: " + warnings
        );
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nullConsumerDoesNotThrow(TestContext ctx) {
        MsfHeader header = MsfHeader.builder().mcDataVersion(0L).build();
        // Must not throw regardless of version mismatch
        DataVersionChecker.check(header, null);
        ctx.complete();
    }
}

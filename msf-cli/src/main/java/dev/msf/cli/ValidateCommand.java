package dev.msf.cli;

import dev.msf.core.MsfChecksumException;
import dev.msf.core.MsfException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfVersionException;
import dev.msf.core.MsfWarning;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Runs a structured set of checks against an MSF file and reports pass/fail/warn per check.
 *
 * <p>Uses {@link MsfReaderConfig#allowChecksumFailure()} so that all warnings are collected
 * even when the file checksum fails, allowing all problems to be reported rather than stopping
 * at the first failure.
 *
 * <p>Exit codes: 0 = all checks pass (warnings do not affect exit code),
 * 1 = one or more checks fail, 2 = file not found or unreadable.
 */
@Command(
    name = "validate",
    mixinStandardHelpOptions = true,
    description = "Validate an MSF file and report pass/fail for each structural check."
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<file>", description = "Path to the .msf file.")
    private Path file;

    @Override
    public Integer call() {
        System.out.println("Validating: " + file);
        System.out.println(MsfCli.TOOL_NAME + ": " + MsfCli.IMPL_VERSION);
        System.out.println();

        // --- Check 1: file exists and is readable ---
        if (!Files.exists(file)) {
            System.err.println("Error: file not found: " + file);
            return 2;
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(file);
        } catch (IOException e) {
            System.err.println("Error: cannot read file: " + file + " — " + e.getMessage());
            return 2;
        }

        List<CheckResult> checks = new ArrayList<>();
        List<MsfWarning> warnings = new ArrayList<>();

        // Run the full parse with checksum-continue enabled.
        // We catch specific exception types to map them to named check failures.
        // Warnings are collected throughout and mapped to named checks below.
        MsfFile msf = null;
        Exception parseException = null;

        try {
            msf = MsfReader.readFile(fileBytes, MsfReaderConfig.allowChecksumFailure(), warnings::add);
        } catch (MsfChecksumException e) {
            parseException = e;
        } catch (MsfVersionException e) {
            parseException = e;
        } catch (MsfParseException e) {
            parseException = e;
        } catch (MsfException e) {
            parseException = e;
        }

        // -----------------------------------------------------------------------
        // Determine the phase in which parsing stopped (or succeeded) so we can
        // report which checks passed before the failure.
        // -----------------------------------------------------------------------

        // Phase determination: the exceptions thrown by readHeaderFromBytes fire in order:
        //   1. Too-short MsfParseException  → stops before magic check
        //   2. Magic MsfParseException       → stops at magic
        //   3. MsfChecksumException(HEADER)  → stops at header checksum
        //   4. MsfVersionException           → stops at version
        //   5. Required-offset MsfParseException → stops at required offsets
        //   After those: warnings for size, offsets, file checksum, feature flags.
        //   Then content MsfParseException: palette, region dimensions, etc.

        boolean reachedMagic          = true;
        boolean reachedHeaderChecksum = true;
        boolean reachedVersion        = true;
        boolean reachedRequiredOffsets = true;
        boolean reachedContent        = true;

        String exceptionCheck  = null; // name of the check that the exception maps to
        String exceptionDetail = null;

        if (parseException != null) {
            String msg = parseException.getMessage() != null ? parseException.getMessage() : "";

            if (parseException instanceof MsfChecksumException cse) {
                if (cse.getChecksumType() == MsfChecksumException.ChecksumType.HEADER) {
                    reachedVersion = false;
                    reachedRequiredOffsets = false;
                    reachedContent = false;
                    exceptionCheck = "Header checksum";
                    exceptionDetail = String.format("mismatch (stored 0x%016X, computed 0x%016X)",
                            cse.getExpected(), cse.getActual());
                } else {
                    // FILE checksum with allowChecksumFailure() should never throw — treat as content error
                    reachedContent = false;
                    exceptionCheck = "File checksum";
                    exceptionDetail = msg;
                }

            } else if (parseException instanceof MsfVersionException) {
                reachedRequiredOffsets = false;
                reachedContent = false;
                exceptionCheck = "Major version (1)";
                exceptionDetail = msg;

            } else if (parseException instanceof MsfParseException) {
                // Narrow to specific check by message content
                if (msg.contains("too short") || msg.contains("minimum")) {
                    reachedMagic = false;
                    reachedHeaderChecksum = false;
                    reachedVersion = false;
                    reachedRequiredOffsets = false;
                    reachedContent = false;
                    exceptionCheck = "File readable (\u2265 48 bytes)";
                    exceptionDetail = msg;

                } else if (msg.contains("Magic byte")) {
                    reachedHeaderChecksum = false;
                    reachedVersion = false;
                    reachedRequiredOffsets = false;
                    reachedContent = false;
                    exceptionCheck = "Magic bytes";
                    exceptionDetail = msg;

                } else if (msg.contains("offset") && (msg.contains("is 0") || msg.contains("MUST NOT be 0"))) {
                    reachedContent = false;
                    exceptionCheck = "Required block offsets present";
                    exceptionDetail = msg;

                } else if (msg.contains("at or beyond")) {
                    reachedContent = false;
                    exceptionCheck = "All offsets within file bounds";
                    exceptionDetail = msg;

                } else if (msg.contains("Duplicate palette") || msg.contains("Palette entry")) {
                    exceptionCheck = "Palette integrity";
                    exceptionDetail = msg;

                } else if (msg.contains("invalid size") || msg.contains("region count is 0")
                        || msg.contains("region size")) {
                    exceptionCheck = "Region dimensions";
                    exceptionDetail = msg;

                } else if (msg.contains("biome") || msg.contains("packed_array") || msg.contains("array_length")) {
                    exceptionCheck = "Packed array lengths";
                    exceptionDetail = msg;

                } else if (msg.contains("out-of-range") || msg.contains("palette ID")) {
                    exceptionCheck = "Palette ID bounds";
                    exceptionDetail = msg;

                } else if (msg.contains("decompressed length") || msg.contains("decompress")) {
                    exceptionCheck = "Region decompression";
                    exceptionDetail = msg;

                } else if (msg.contains("layer count") || msg.contains("layer ID") || msg.contains("Layer")) {
                    exceptionCheck = "Layer structure";
                    exceptionDetail = msg;

                } else {
                    exceptionCheck = "Parse";
                    exceptionDetail = msg;
                }
            } else {
                exceptionCheck = "Parse";
                exceptionDetail = parseException.getMessage();
            }
        }

        // -----------------------------------------------------------------------
        // Build the ordered check list
        // -----------------------------------------------------------------------

        // Check: file readable (≥ 48 bytes)
        if (!reachedMagic) {
            checks.add(CheckResult.fail("File readable (\u2265 48 bytes)", exceptionDetail));
        } else {
            checks.add(CheckResult.pass("File readable (\u2265 48 bytes)"));
        }

        // Check: magic bytes
        if (reachedMagic) {
            if ("Magic bytes".equals(exceptionCheck)) {
                checks.add(CheckResult.fail("Magic bytes", exceptionDetail));
            } else {
                checks.add(CheckResult.pass("Magic bytes"));
            }
        }

        // Check: header checksum
        if (reachedHeaderChecksum) {
            if ("Header checksum".equals(exceptionCheck)) {
                checks.add(CheckResult.fail("Header checksum", exceptionDetail));
            } else {
                checks.add(CheckResult.pass("Header checksum"));
            }
        }

        // Check: major version
        if (reachedVersion) {
            if ("Major version (1)".equals(exceptionCheck)) {
                checks.add(CheckResult.fail("Major version (1)", exceptionDetail));
            } else {
                checks.add(CheckResult.pass("Major version (1)"));
            }
        }

        // Check: required block offsets
        if (reachedRequiredOffsets) {
            if ("Required block offsets present".equals(exceptionCheck)) {
                checks.add(CheckResult.fail("Required block offsets present", exceptionDetail));
            } else {
                checks.add(CheckResult.pass("Required block offsets present"));
            }
        }

        // Warning-based checks — reported whenever we got far enough to collect warnings.
        // These are independent of whether content parsing succeeded.

        boolean fileSizeMismatch = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.FILE_SIZE_MISMATCH);
        if (reachedRequiredOffsets || fileSizeMismatch) {
            if (fileSizeMismatch) {
                String detail = warnings.stream()
                        .filter(w -> w.code() == MsfWarning.Code.FILE_SIZE_MISMATCH)
                        .map(MsfWarning::message).findFirst().orElse("");
                checks.add(CheckResult.warn("File size matches", detail));
            } else if (reachedRequiredOffsets) {
                checks.add(CheckResult.pass("File size matches"));
            }
        }

        boolean offsetBeyond = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.OFFSET_BEYOND_FILE_SIZE);
        if (reachedRequiredOffsets || offsetBeyond) {
            if (offsetBeyond) {
                String detail = warnings.stream()
                        .filter(w -> w.code() == MsfWarning.Code.OFFSET_BEYOND_FILE_SIZE)
                        .map(MsfWarning::message).findFirst().orElse("");
                checks.add(CheckResult.warn("All offsets within file bounds", detail));
            } else if (reachedRequiredOffsets && !"All offsets within file bounds".equals(exceptionCheck)) {
                checks.add(CheckResult.pass("All offsets within file bounds"));
            }
        }

        boolean fileChecksumFailed = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE);
        if (reachedRequiredOffsets || fileChecksumFailed) {
            if (fileChecksumFailed) {
                String detail = warnings.stream()
                        .filter(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE)
                        .map(MsfWarning::message).findFirst().orElse("");
                checks.add(CheckResult.fail("File checksum", detail));
            } else if (reachedRequiredOffsets) {
                checks.add(CheckResult.pass("File checksum"));
            }
        }

        // Content checks — only shown when we reached content parsing
        if (reachedContent) {
            if (msf != null) {
                // Parse succeeded: content checks all pass
                int paletteCount = msf.palette().entries().size();
                checks.add(CheckResult.pass("Palette (" + paletteCount + " entries, no duplicates)"));

                long regionCount = msf.layerIndex().layers().stream()
                        .mapToLong(l -> l.regions().size()).sum();
                checks.add(CheckResult.pass("Region dimensions (" + regionCount + " region"
                        + (regionCount != 1 ? "s" : "") + ")"));
                checks.add(CheckResult.pass("Packed array lengths"));
                checks.add(CheckResult.pass("Palette ID bounds"));
            } else if (exceptionCheck != null) {
                // We reached content but hit a content-level parse exception.
                // All structural checks passed; report the specific content failure.
                String check = exceptionCheck;
                String detail = exceptionDetail != null ? exceptionDetail : "";

                // Pass checks that come before the failing one
                if (!"Palette integrity".equals(check)) {
                    checks.add(CheckResult.pass("Palette integrity"));
                } else {
                    checks.add(CheckResult.fail("Palette integrity", detail));
                }

                if (!"Region dimensions".equals(check)) {
                    checks.add(CheckResult.pass("Region dimensions"));
                } else {
                    checks.add(CheckResult.fail("Region dimensions", detail));
                }

                if (!"Packed array lengths".equals(check) && !"Region decompression".equals(check)) {
                    checks.add(CheckResult.pass("Packed array lengths"));
                } else {
                    checks.add(CheckResult.fail("Packed array lengths / decompression", detail));
                }

                if (!"Palette ID bounds".equals(check)) {
                    checks.add(CheckResult.pass("Palette ID bounds"));
                } else {
                    checks.add(CheckResult.fail("Palette ID bounds", detail));
                }

                if (!List.of("Palette integrity", "Region dimensions",
                             "Packed array lengths", "Region decompression",
                             "Palette ID bounds", "Layer structure", "Parse")
                        .contains(check) && !"Header checksum".equals(check)) {
                    // Unrecognized content error
                    checks.add(CheckResult.fail(check, detail));
                }
            }
        }

        // Feature flag consistency (warning-based)
        boolean flagConflict = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.FEATURE_FLAG_CONFLICT);
        if (reachedRequiredOffsets) {
            if (flagConflict) {
                String detail = warnings.stream()
                        .filter(w -> w.code() == MsfWarning.Code.FEATURE_FLAG_CONFLICT)
                        .map(MsfWarning::message).findFirst().orElse("");
                checks.add(CheckResult.fail("Feature flag consistency", detail));
            } else {
                checks.add(CheckResult.pass("Feature flag consistency"));
            }
        }

        // Reserved bits (advisory warn)
        boolean reservedSet = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_SET);
        if (reachedRequiredOffsets && reservedSet) {
            String detail = warnings.stream()
                    .filter(w -> w.code() == MsfWarning.Code.RESERVED_FLAG_SET)
                    .map(MsfWarning::message).findFirst().orElse("");
            checks.add(CheckResult.warn("Reserved feature flag bits", detail));
        }

        // -----------------------------------------------------------------------
        // Print results
        // -----------------------------------------------------------------------
        // Story V1.1: include format version if the file parsed successfully
        if (msf != null) {
            System.out.println("Format: V" + msf.header().majorVersion()
                    + "." + msf.header().minorVersion());
            System.out.println();
        }
        for (CheckResult cr : checks) {
            System.out.println(cr.toLine());
        }
        System.out.println();

        long failCount = checks.stream().filter(c -> c.status() == CheckResult.Status.FAIL).count();
        long warnCount = checks.stream().filter(c -> c.status() == CheckResult.Status.WARN).count();
        long passCount = checks.stream().filter(c -> c.status() == CheckResult.Status.PASS).count();
        long total = checks.size();

        if (failCount == 0) {
            if (warnCount == 0) {
                System.out.println("VALID \u2014 " + passCount + "/" + total + " checks passed");
            } else {
                System.out.println("VALID with warnings \u2014 " + passCount + "/" + total
                        + " checks passed, " + warnCount + " warning" + (warnCount != 1 ? "s" : ""));
            }
            return 0;
        } else {
            StringBuilder summary = new StringBuilder("INVALID \u2014 ")
                    .append(failCount).append(" check").append(failCount != 1 ? "s" : "").append(" failed");
            if (warnCount > 0) {
                summary.append(", ").append(warnCount).append(" warning").append(warnCount != 1 ? "s" : "");
            }
            System.out.println(summary);
            return 1;
        }
    }
}

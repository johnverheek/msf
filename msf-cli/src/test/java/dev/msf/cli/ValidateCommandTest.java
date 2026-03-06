package dev.msf.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ValidateCommandTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private record RunResult(int exitCode, String stdout, String stderr) {}

    private RunResult run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(new PrintStream(outBuf));
        System.setErr(new PrintStream(errBuf));
        int code;
        try {
            code = new picocli.CommandLine(new MsfCli()).execute(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return new RunResult(code, outBuf.toString(), errBuf.toString());
    }

    private Path write(byte[] bytes, String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void validatePassesForValidFile() throws Exception {
        Path msf = write(FixtureUtil.minimalValidBytes(), "valid.msf");
        RunResult r = run("validate", msf.toString());

        assertEquals(0, r.exitCode(), "Exit code should be 0 for a valid file");
        assertTrue(r.stdout().contains("VALID"), "Summary should say VALID");
        assertFalse(r.stdout().contains("INVALID"), "Summary should not say INVALID");
    }

    @Test
    void validateFailsForBadFileChecksum() throws Exception {
        Path msf = write(FixtureUtil.badFileChecksumBytes(), "bad_checksum.msf");
        RunResult r = run("validate", msf.toString());

        assertEquals(1, r.exitCode(), "Exit code should be 1 when a check fails");
        assertTrue(r.stdout().contains("INVALID"), "Summary should say INVALID");
        // The 'File checksum' check should appear and be marked as failed (✗)
        String failLine = r.stdout().lines()
                .filter(l -> l.contains("File checksum") && l.startsWith("  \u2717"))
                .findFirst().orElse(null);
        assertNotNull(failLine, "Output should include a failed 'File checksum' check line");
    }

    @Test
    void validateFailsForDuplicatePaletteEntry() throws Exception {
        Path msf = write(FixtureUtil.duplicatePaletteBytes(), "dup_palette.msf");
        RunResult r = run("validate", msf.toString());

        assertEquals(1, r.exitCode(), "Exit code should be 1 for duplicate palette entry");
        assertTrue(r.stdout().contains("INVALID"), "Summary should say INVALID");
        // A palette-related check should appear as failed
        boolean hasPaletteFailLine = r.stdout().lines()
                .anyMatch(l -> l.startsWith("  \u2717") && l.toLowerCase().contains("palette"));
        assertTrue(hasPaletteFailLine, "Output should include a failed palette check line");
    }

    @Test
    void validateFailsForZeroSizeRegion() throws Exception {
        Path msf = write(FixtureUtil.zeroSizeRegionBytes(), "zero_size.msf");
        RunResult r = run("validate", msf.toString());

        assertEquals(1, r.exitCode(), "Exit code should be 1 for zero-size region");
        assertTrue(r.stdout().contains("INVALID"), "Summary should say INVALID");
        // A region-related check should appear as failed
        boolean hasRegionFailLine = r.stdout().lines()
                .anyMatch(l -> l.startsWith("  \u2717") && l.toLowerCase().contains("region"));
        assertTrue(hasRegionFailLine, "Output should include a failed region dimensions check line");
    }

    @Test
    void validateReportsAllFailures() throws Exception {
        Path msf = write(FixtureUtil.multiProblemBytes(), "multi_problem.msf");
        RunResult r = run("validate", msf.toString());

        assertEquals(1, r.exitCode(), "Exit code should be 1 when failures are present");
        assertTrue(r.stdout().contains("INVALID"), "Summary should say INVALID");

        // FILE_SIZE_MISMATCH → "File size matches" with ⚠ (warn)
        boolean hasSizeMismatch = r.stdout().lines()
                .anyMatch(l -> l.contains("File size") && (l.startsWith("  \u26A0") || l.startsWith("  \u2717")));
        assertTrue(hasSizeMismatch, "Output should report file size mismatch");

        // FILE_CHECKSUM_FAILURE → "File checksum" with ✗ (fail)
        boolean hasChecksumFail = r.stdout().lines()
                .anyMatch(l -> l.contains("File checksum") && l.startsWith("  \u2717"));
        assertTrue(hasChecksumFail, "Output should report file checksum failure");

        // Both reported (not just the first)
        long problemCount = r.stdout().lines()
                .filter(l -> l.startsWith("  \u2717") || l.startsWith("  \u26A0"))
                .count();
        assertTrue(problemCount >= 2, "At least 2 problems should be reported, got: " + problemCount);
    }

    @Test
    void validateHandlesNonexistentFile() {
        RunResult r = run("validate", "/nonexistent/path/file.msf");

        assertEquals(2, r.exitCode(), "Exit code should be 2 for file not found");
        assertTrue(r.stderr().contains("not found") || r.stderr().contains("Error"),
                "stderr should describe the error");
    }

    @Test
    void validateHandlesUnreadablePathAsError() {
        // Passing a directory instead of a file results in an I/O-level error.
        RunResult r = run("validate", tempDir.toString());

        // Directories cannot be read as an MSF file; exit 2 expected.
        assertNotEquals(0, r.exitCode(), "Exit code should be non-zero for an unreadable/directory path");
    }
}

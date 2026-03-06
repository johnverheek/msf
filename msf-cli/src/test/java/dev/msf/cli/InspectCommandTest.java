package dev.msf.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InspectCommandTest {

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
    void inspectProducesExpectedFieldValues() throws Exception {
        Path msf = write(FixtureUtil.minimalValidBytes(), "minimal.msf");
        RunResult r = run("inspect", msf.toString());

        assertEquals(0, r.exitCode(), "Exit code should be 0 for valid file");

        // --- File header ---
        assertTrue(r.stdout().contains("minimal.msf"), "Output should include the filename");
        assertTrue(r.stdout().contains("1.0"), "Output should include spec version 1.0");
        assertTrue(r.stdout().contains("3953"), "Output should include MC data version");

        // --- Metadata ---
        assertTrue(r.stdout().contains("Test Schematic"), "Output should include schematic name");
        assertTrue(r.stdout().contains("Test Author"), "Output should include author");
        assertTrue(r.stdout().contains("A test description"), "Output should include description");
        assertTrue(r.stdout().contains("test"), "Output should include tags");
        assertTrue(r.stdout().contains("MIT"), "Output should include license");
        assertTrue(r.stdout().contains("msf-test"), "Output should include tool name");
        assertTrue(r.stdout().contains("1.0.0"), "Output should include tool version");

        // --- Placement ---
        assertTrue(r.stdout().contains("North"), "Output should include canonical facing");

        // --- Palette ---
        assertTrue(r.stdout().contains("2"), "Output should show palette size (air + stone)");

        // --- Layers ---
        assertTrue(r.stdout().contains("Foundation"), "Output should include layer name");

        // --- Regions ---
        assertTrue(r.stdout().contains("8"), "Output should include total blocks (2×2×2)");

        // --- Checksums ---
        assertTrue(r.stdout().contains("Header checksum"), "Output should include header checksum section");
        assertTrue(r.stdout().contains("PASS"), "Header and file checksums should pass");

        // No warnings expected for a valid file
        assertFalse(r.stdout().contains("--- Warnings ---"), "No warnings section for a valid file");
        assertTrue(r.stderr().isEmpty(), "No stderr output for valid file");
    }

    @Test
    void inspectIncludesWarningsSectionWhenWarningsPresent() throws Exception {
        Path msf = write(FixtureUtil.withMalformedThumbnailBytes(), "warnings.msf");
        RunResult r = run("inspect", msf.toString());

        assertEquals(0, r.exitCode(), "Exit code should be 0 (warnings do not cause failure for inspect)");
        assertTrue(r.stdout().contains("--- Warnings ---"), "Warnings section should be present");
        assertTrue(r.stdout().contains("MALFORMED_THUMBNAIL"), "Warnings section should list the warning code");
    }

    @Test
    void inspectHandlesNonexistentFile() {
        RunResult r = run("inspect", "/nonexistent/path/file.msf");

        assertEquals(2, r.exitCode(), "Exit code should be 2 for file not found");
        assertTrue(r.stderr().contains("not found") || r.stderr().contains("Error"),
                "stderr should mention file not found");
    }
}

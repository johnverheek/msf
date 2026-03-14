package dev.msf.cli;

import dev.msf.cli.convert.LitematicaFormatTest;
import dev.msf.cli.convert.NbtReader;
import dev.msf.cli.convert.NbtTag;
import dev.msf.cli.convert.NbtWriter;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConvertCommandTest {

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

    /**
     * Builds a minimal 2×1×2 vanilla structure .nbt file in memory:
     *   DataVersion=3953, palette=[air, stone], 4 blocks (stone at y=0 corners, air elsewhere).
     */
    private static byte[] minimalVanillaNbt() throws Exception {
        NbtTag.CompoundTag airEntry   = new NbtTag.CompoundTag(Map.of("Name", new NbtTag.StringTag("minecraft:air")));
        NbtTag.CompoundTag stoneEntry = new NbtTag.CompoundTag(Map.of("Name", new NbtTag.StringTag("minecraft:stone")));

        List<NbtTag> blocks = List.of(
                blockEntry(0, 0, 0, 1),  // stone
                blockEntry(1, 0, 0, 0),  // air
                blockEntry(0, 0, 1, 1),  // stone
                blockEntry(1, 0, 1, 0)   // air
        );

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("DataVersion", new NbtTag.IntTag(3953));
        root.put("size",        intList(2, 1, 2));
        root.put("palette",     new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(airEntry, stoneEntry)));
        root.put("blocks",      new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, blocks));
        root.put("entities",    new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));

        return NbtWriter.writeCompound(new NbtTag.CompoundTag(root), "");
    }

    private static NbtTag blockEntry(int x, int y, int z, int state) {
        Map<String, NbtTag> m = new LinkedHashMap<>();
        m.put("pos",   intList(x, y, z));
        m.put("state", new NbtTag.IntTag(state));
        return new NbtTag.CompoundTag(m);
    }

    private static NbtTag.ListTag intList(int... values) {
        var elems = new java.util.ArrayList<NbtTag>();
        for (int v : values) elems.add(new NbtTag.IntTag(v));
        return new NbtTag.ListTag(NbtTag.TYPE_INT, elems);
    }

    // -------------------------------------------------------------------------
    // .nbt ↔ .msf tests (existing)
    // -------------------------------------------------------------------------

    @Test
    void convertNbtToMsf_producesValidMsfFile() throws Exception {
        Path nbtFile = tempDir.resolve("structure.nbt");
        Path msfFile = tempDir.resolve("structure.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", nbtFile.toString(), msfFile.toString());

        assertEquals(0, r.exitCode(), "Exit code should be 0: " + r.stderr());
        assertTrue(Files.exists(msfFile), "Output MSF file must be created");

        byte[] msfBytes = Files.readAllBytes(msfFile);
        MsfFile msf = MsfReader.readFile(msfBytes, MsfReaderConfig.DEFAULT, null);
        assertEquals(3953L, msf.header().mcDataVersion());
        assertEquals(1, msf.layerIndex().layers().size());
        var region = msf.layerIndex().layers().get(0).regions().get(0);
        assertEquals(2, region.sizeX());
        assertEquals(1, region.sizeY());
        assertEquals(2, region.sizeZ());
        assertTrue(msf.palette().entries().contains("minecraft:stone"));
    }

    @Test
    void convertMsfToNbt_producesReadableNbtFile() throws Exception {
        Path nbtFile = tempDir.resolve("structure.nbt");
        Path msfFile = tempDir.resolve("structure.msf");
        Path nbtOut  = tempDir.resolve("structure_out.nbt");
        Files.write(nbtFile, minimalVanillaNbt());

        run("convert", nbtFile.toString(), msfFile.toString());
        RunResult r = run("convert", msfFile.toString(), nbtOut.toString());

        assertEquals(0, r.exitCode(), "Exit code should be 0: " + r.stderr());
        assertTrue(Files.exists(nbtOut), "Output .nbt file must be created");

        byte[] nbtBytes = Files.readAllBytes(nbtOut);
        NbtTag.CompoundTag root = NbtReader.readCompound(nbtBytes);
        assertTrue(root.entries().containsKey("DataVersion"), "Must have DataVersion");
        assertTrue(root.entries().containsKey("blocks"),      "Must have blocks");
        assertTrue(root.entries().containsKey("palette"),     "Must have palette");
        NbtTag.ListTag blocks = (NbtTag.ListTag) root.entries().get("blocks");
        assertEquals(4, blocks.elements().size(), "2×1×2 = 4 blocks");
    }

    @Test
    void convertNbtToMsf_roundTrip_preservesBlockCount() throws Exception {
        Path nbtIn   = tempDir.resolve("in.nbt");
        Path msfFile = tempDir.resolve("round.msf");
        Path nbtOut  = tempDir.resolve("out.nbt");
        Files.write(nbtIn, minimalVanillaNbt());

        run("convert", nbtIn.toString(),   msfFile.toString());
        run("convert", msfFile.toString(), nbtOut.toString());

        NbtTag.CompoundTag original  = NbtReader.readCompound(Files.readAllBytes(nbtIn));
        NbtTag.CompoundTag converted = NbtReader.readCompound(Files.readAllBytes(nbtOut));

        int origBlocks = ((NbtTag.ListTag) original.entries().get("blocks")).elements().size();
        int convBlocks = ((NbtTag.ListTag) converted.entries().get("blocks")).elements().size();
        assertEquals(origBlocks, convBlocks, "Block count must be preserved through round-trip");

        int origPalSize = ((NbtTag.ListTag) original.entries().get("palette")).elements().size();
        int convPalSize = ((NbtTag.ListTag) converted.entries().get("palette")).elements().size();
        assertEquals(origPalSize, convPalSize, "Palette size must be preserved through round-trip");
    }

    @Test
    void convertHandlesNonexistentFile() {
        RunResult r = run("convert", "/nonexistent/file.nbt", tempDir.resolve("out.msf").toString());
        assertEquals(2, r.exitCode(), "Exit code should be 2 for missing input file");
        assertTrue(r.stderr().contains("not found") || r.stderr().contains("Error"),
                "stderr should describe the error");
    }

    @Test
    void convertRejectsUnsupportedExtensionCombination() throws Exception {
        Path txtFile = tempDir.resolve("data.txt");
        Files.write(txtFile, new byte[]{1, 2, 3});
        RunResult r = run("convert", txtFile.toString(), tempDir.resolve("out.msf").toString());
        assertNotEquals(0, r.exitCode(), "Exit code should be non-zero for unsupported extension");
    }

    // -------------------------------------------------------------------------
    // .litematic ↔ .msf /.nbt tests (new)
    // -------------------------------------------------------------------------

    /**
     * .litematic → .msf round trip: read back converted file, block at known position matches.
     */
    @Test
    void convertLitematicToMsf_roundTrip_blockAtKnownPositionMatches() throws Exception {
        Path litematicFile = tempDir.resolve("test.litematic");
        Path msfFile       = tempDir.resolve("test.msf");
        Files.write(litematicFile, LitematicaFormatTest.twoSubregionFixtureBytes());

        RunResult r = run("convert", litematicFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "Exit code should be 0: " + r.stderr());
        assertTrue(Files.exists(msfFile), "Output MSF file must be created");

        byte[] msfBytes = Files.readAllBytes(msfFile);
        MsfFile msf = MsfReader.readFile(msfBytes, MsfReaderConfig.DEFAULT, null);

        assertEquals(2, msf.layerIndex().layers().size(), "Must have 2 layers from 2 subregions");

        // Subregion1 at anchor (0,0,0): block (0,0,0) must be stone
        var layer1 = msf.layerIndex().layers().stream()
                .filter(l -> "Subregion1".equals(l.name())).findFirst().orElseThrow();
        int stoneId = msf.palette().entries().indexOf("minecraft:stone");
        assertNotEquals(-1, stoneId, "stone must be in global palette");
        assertEquals(stoneId, layer1.regions().get(0).blockData()[0],
                "block at (0,0,0) in Subregion1 must be stone");
    }

    /**
     * .msf → .litematic round trip: read back converted file, subregion count matches layer count.
     */
    @Test
    void convertMsfToLitematic_roundTrip_subregionCountMatchesLayerCount() throws Exception {
        Path litematicIn  = tempDir.resolve("in.litematic");
        Path msfFile      = tempDir.resolve("mid.msf");
        Path litematicOut = tempDir.resolve("out.litematic");
        Files.write(litematicIn, LitematicaFormatTest.twoSubregionFixtureBytes());

        run("convert", litematicIn.toString(), msfFile.toString());
        RunResult r = run("convert", msfFile.toString(), litematicOut.toString());
        assertEquals(0, r.exitCode(), "Exit code should be 0: " + r.stderr());
        assertTrue(Files.exists(litematicOut), "Output .litematic must be created");

        byte[] litBytes = Files.readAllBytes(litematicOut);
        NbtTag.CompoundTag root = NbtReader.readCompound(litBytes);
        NbtTag.CompoundTag regions = (NbtTag.CompoundTag) root.entries().get("Regions");
        assertNotNull(regions, "Regions compound must be present");

        byte[] msfBytes = Files.readAllBytes(msfFile);
        MsfFile msf = MsfReader.readFile(msfBytes, MsfReaderConfig.DEFAULT, null);
        assertEquals(msf.layerIndex().layers().size(), regions.entries().size(),
                "subregion count must match MSF layer count");
    }

    /**
     * .litematic → .nbt via MSF intermediate: completes without error.
     */
    @Test
    void convertLitematicToNbt_viaMsfIntermediate_completesWithoutError() throws Exception {
        Path litematicFile = tempDir.resolve("test.litematic");
        Path nbtOut        = tempDir.resolve("test.nbt");
        Files.write(litematicFile, LitematicaFormatTest.twoSubregionFixtureBytes());

        RunResult r = run("convert", litematicFile.toString(), nbtOut.toString());
        assertEquals(0, r.exitCode(), "Exit code should be 0: " + r.stderr());
        assertTrue(Files.exists(nbtOut), "Output .nbt file must be created");

        byte[] nbtBytes = Files.readAllBytes(nbtOut);
        NbtTag.CompoundTag root = NbtReader.readCompound(nbtBytes);
        assertTrue(root.entries().containsKey("blocks"),  "Must have blocks");
        assertTrue(root.entries().containsKey("palette"), "Must have palette");
    }

    // =========================================================================
    // C1.1: --compressor flag tests
    // =========================================================================

    @Test
    void convertWithLz4Compressor_producesValidMsf() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_lz4.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--compressor", "lz4", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "lz4 convert must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertEquals(CompressionType.LZ4,
                msf.layerIndex().layers().get(0).regions().get(0).compressionType(),
                "Region compression type must be LZ4");
    }

    @Test
    void convertWithNoneCompressor_producesValidMsf() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_none.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--compressor", "none", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "none compressor must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertEquals(CompressionType.NONE,
                msf.layerIndex().layers().get(0).regions().get(0).compressionType(),
                "Region compression type must be NONE");
    }

    @Test
    void convertWithInvalidCompressor_rejectsWithExitCode2() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_bad.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--compressor", "bogus", nbtFile.toString(), msfFile.toString());
        assertEquals(2, r.exitCode(), "Invalid compressor must produce exit code 2");
        assertTrue(r.stderr().toLowerCase().contains("error") || r.stderr().toLowerCase().contains("invalid"),
                "Error message expected in stderr");
    }

    @Test
    void convertWithCompressionLevel9_producesValidMsf() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_l9.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--compression-level", "9", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "compression-level 9 must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertNotNull(msf);
    }

    @Test
    void convertWithInvalidCompressionLevel_rejectsWithExitCode2() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_bad.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--compression-level", "99",
                nbtFile.toString(), msfFile.toString());
        assertEquals(2, r.exitCode(), "Out-of-range compression level must produce exit code 2");
    }

    @Test
    void convertRoundTrip_allCompressors_preserveBlockData() throws Exception {
        for (String comp : List.of("zstd", "lz4", "none")) {
            Path nbtFile = tempDir.resolve("s_" + comp + ".nbt");
            Path msfFile = tempDir.resolve("s_" + comp + ".msf");
            Path nbtOut  = tempDir.resolve("s_" + comp + "_out.nbt");
            Files.write(nbtFile, minimalVanillaNbt());

            run("convert", "--compressor", comp, nbtFile.toString(), msfFile.toString());
            run("convert", msfFile.toString(), nbtOut.toString());

            NbtTag.CompoundTag origNbt = NbtReader.readCompound(Files.readAllBytes(nbtFile));
            NbtTag.CompoundTag convNbt = NbtReader.readCompound(Files.readAllBytes(nbtOut));

            int origBlocks = ((NbtTag.ListTag) origNbt.entries().get("blocks")).elements().size();
            int convBlocks = ((NbtTag.ListTag) convNbt.entries().get("blocks")).elements().size();
            assertEquals(origBlocks, convBlocks,
                    "Block count must survive round-trip with compressor=" + comp);
        }
    }

    // =========================================================================
    // C1.2: --entities flag tests
    // =========================================================================

    @Test
    void convertWithEntitiesFalse_outputHasNoEntityBlocks() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_noent.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--entities", "false", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "--entities false must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertTrue(msf.entities().isEmpty(), "No entity block when --entities false");
        assertTrue(msf.blockEntities().isEmpty(), "No block entity block when --entities false");
        assertEquals(0, msf.header().featureFlags()
                & (dev.msf.core.model.MsfHeader.FeatureFlags.HAS_ENTITIES
                   | dev.msf.core.model.MsfHeader.FeatureFlags.HAS_BLOCK_ENTITIES),
                "Feature flags must have HAS_ENTITIES and HAS_BLOCK_ENTITIES cleared");
    }

    @Test
    void convertWithEntitiesTrue_defaultBehaviourUnchanged() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfDefaults = tempDir.resolve("s_defaults.msf");
        Path msfExplicit = tempDir.resolve("s_explicit.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        run("convert", nbtFile.toString(), msfDefaults.toString());
        run("convert", "--entities", "true", nbtFile.toString(), msfExplicit.toString());

        MsfFile def = MsfReader.readFile(Files.readAllBytes(msfDefaults), MsfReaderConfig.DEFAULT, null);
        MsfFile exp = MsfReader.readFile(Files.readAllBytes(msfExplicit), MsfReaderConfig.DEFAULT, null);

        assertEquals(def.palette().entries(), exp.palette().entries(),
                "--entities true must produce same palette as default");
    }

    // =========================================================================
    // C1.3: --name and --author flag tests
    // =========================================================================

    @Test
    void convertWithNameOverride_setsNameInMetadata() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_named.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--name", "My Schematic", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "--name must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertEquals("My Schematic", msf.metadata().name(), "Metadata name must match --name override");
    }

    @Test
    void convertWithAuthorOverride_setsAuthorInMetadata() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s_auth.msf");
        Files.write(nbtFile, minimalVanillaNbt());

        RunResult r = run("convert", "--author", "Builder Bot", nbtFile.toString(), msfFile.toString());
        assertEquals(0, r.exitCode(), "--author must succeed: " + r.stderr());

        MsfFile msf = MsfReader.readFile(Files.readAllBytes(msfFile), MsfReaderConfig.DEFAULT, null);
        assertEquals("Builder Bot", msf.metadata().author(), "Metadata author must match --author override");
    }

    // =========================================================================
    // V1.1: version disambiguation tests
    // =========================================================================

    @Test
    void inspect_showsFormatVersionAndImplVersion() throws Exception {
        // Build a minimal valid MSF and run inspect on it
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s.msf");
        Files.write(nbtFile, minimalVanillaNbt());
        run("convert", nbtFile.toString(), msfFile.toString());

        RunResult r = run("inspect", msfFile.toString());
        assertEquals(0, r.exitCode(), "inspect must succeed: " + r.stderr());

        // Story V1.1: "Format: V1.0" and "msf-cli: 1.1.0" must appear
        assertTrue(r.stdout().contains("Format:"), "Output must contain 'Format:' label");
        assertTrue(r.stdout().contains("V1."), "Output must contain 'V1.' format version");
        assertTrue(r.stdout().contains(MsfCli.TOOL_NAME + ":"), "Output must contain tool name label");
        assertTrue(r.stdout().contains(MsfCli.IMPL_VERSION), "Output must contain impl version");
    }

    @Test
    void validate_showsImplVersion() throws Exception {
        Path nbtFile = tempDir.resolve("s.nbt");
        Path msfFile = tempDir.resolve("s.msf");
        Files.write(nbtFile, minimalVanillaNbt());
        run("convert", nbtFile.toString(), msfFile.toString());

        RunResult r = run("validate", msfFile.toString());
        assertEquals(0, r.exitCode(), "validate must succeed: " + r.stderr());
        assertTrue(r.stdout().contains(MsfCli.IMPL_VERSION), "validate output must contain impl version");
    }

    // =========================================================================
    // parseCompressor helper unit tests
    // =========================================================================

    @Test
    void parseCompressor_validValues() {
        assertEquals(CompressionType.ZSTD,   ConvertCommand.parseCompressor("zstd"));
        assertEquals(CompressionType.LZ4,    ConvertCommand.parseCompressor("lz4"));
        assertEquals(CompressionType.BROTLI, ConvertCommand.parseCompressor("brotli"));
        assertEquals(CompressionType.NONE,   ConvertCommand.parseCompressor("none"));
        // Case-insensitive
        assertEquals(CompressionType.ZSTD,   ConvertCommand.parseCompressor("ZSTD"));
    }

    @Test
    void parseCompressor_invalidValue_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> ConvertCommand.parseCompressor("gzip"),
                "Unknown compressor name must throw IllegalArgumentException");
    }
}

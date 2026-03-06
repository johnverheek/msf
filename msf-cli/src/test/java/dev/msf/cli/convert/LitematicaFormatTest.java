package dev.msf.cli.convert;

import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfRegion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LitematicaFormat} — .litematic ↔ MSF conversion.
 */
public class LitematicaFormatTest {

    // =========================================================================
    // Fixture generation
    // =========================================================================

    /**
     * Generates a two-subregion .litematic compound (v7, uncompressed) in memory.
     *
     * <p>Structure:
     * <ul>
     *   <li>Subregion "Subregion1": 2×1×2 at position (0,0,0), palette=[air, stone, dirt[snowy=false]],
     *       blocks: stone at all positions except (1,0,1)=air</li>
     *   <li>Subregion "Subregion2": 2×1×2 at position (3,0,0), palette=[air, oak_log],
     *       blocks: oak_log at (0,0,0) and (1,0,1)</li>
     * </ul>
     *
     * <p>Also includes a chest block entity in Subregion1 at subregion-relative (0,0,0).
     */
    static NbtTag.CompoundTag buildTwoSubregionFixture() {
        // --- Subregion1: 2×1×2, palette=[air, stone, dirt[snowy=false]] ---
        // Block data (YZX): (0,0,0)=stone, (1,0,0)=stone, (0,0,1)=stone, (1,0,1)=air
        // Local IDs in YZX order: idx = y*sZ*sX + z*sX + x
        //   (y=0,z=0,x=0)→idx0=stone=1, (y=0,z=0,x=1)→idx1=stone=1,
        //   (y=0,z=1,x=0)→idx2=stone=1, (y=0,z=1,x=1)→idx3=air=0
        // dirt with property to verify round-trip
        NbtTag.CompoundTag airEntry1  = compound("Name", str("minecraft:air"));
        NbtTag.CompoundTag stoneEntry = compound("Name", str("minecraft:stone"));
        Map<String, NbtTag> dirtPropMap = new LinkedHashMap<>();
        dirtPropMap.put("Name", str("minecraft:dirt"));
        dirtPropMap.put("Properties", new NbtTag.CompoundTag(Map.of("snowy", str("false"))));
        NbtTag.CompoundTag dirtEntry = new NbtTag.CompoundTag(dirtPropMap);

        // Palette for Subregion1: [air, stone, dirt[snowy=false]] — 3 entries → bpe = max(2, ceil(log2(3))) = 2
        // entriesPerWord = 64/2 = 32. 4 blocks fit in one word.
        // IDs: stone=1, stone=1, stone=1, air=0
        // Word: 0b 00_01_01_01 = 0x15 shifted appropriately
        // word = (1 << 0) | (1 << 2) | (1 << 4) | (0 << 6) = 1 + 4 + 16 = 21
        long[] blockStates1 = { 21L };

        // Chest block entity at subregion-relative (0,0,0)
        Map<String, NbtTag> beNbt = new LinkedHashMap<>();
        beNbt.put("id", str("minecraft:chest"));
        beNbt.put("x",  new NbtTag.IntTag(0));
        beNbt.put("y",  new NbtTag.IntTag(0));
        beNbt.put("z",  new NbtTag.IntTag(0));
        beNbt.put("CustomName", str("{\"text\":\"Loot\"}"));

        Map<String, NbtTag> sr1 = new LinkedHashMap<>();
        sr1.put("BlockStatePalette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND,
                List.of(airEntry1, stoneEntry, dirtEntry)));
        sr1.put("BlockStates",  new NbtTag.LongArrayTag(blockStates1));
        sr1.put("TileEntities", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND,
                List.of(new NbtTag.CompoundTag(beNbt))));
        sr1.put("Entities",    new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        sr1.put("Position",    xyz(0, 0, 0));
        sr1.put("Size",        xyz(2, 1, 2));

        // --- Subregion2: 2×1×2 at position (3,0,0), palette=[air, oak_log] ---
        // Blocks: (0,0,0)=oak_log=1, others=air=0
        // bpe = max(2, ceil(log2(2))) = max(2,1) = 2
        // IDs: 1, 0, 0, 0 → word = (1 << 0) = 1L
        NbtTag.CompoundTag airEntry2   = compound("Name", str("minecraft:air"));
        NbtTag.CompoundTag oakLogEntry = compound("Name", str("minecraft:oak_log"));
        long[] blockStates2 = { 1L };

        Map<String, NbtTag> sr2 = new LinkedHashMap<>();
        sr2.put("BlockStatePalette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND,
                List.of(airEntry2, oakLogEntry)));
        sr2.put("BlockStates",  new NbtTag.LongArrayTag(blockStates2));
        sr2.put("TileEntities", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        sr2.put("Entities",     new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        sr2.put("Position",     xyz(3, 0, 0));
        sr2.put("Size",         xyz(2, 1, 2));

        // Regions
        Map<String, NbtTag> regionsMap = new LinkedHashMap<>();
        regionsMap.put("Subregion1", new NbtTag.CompoundTag(sr1));
        regionsMap.put("Subregion2", new NbtTag.CompoundTag(sr2));

        // Metadata
        Map<String, NbtTag> metaMap = new LinkedHashMap<>();
        metaMap.put("Name",          str("Test Schematic"));
        metaMap.put("Author",        str("TestAuthor"));
        metaMap.put("Description",   str("A test schematic"));
        metaMap.put("TimeCreated",   new NbtTag.LongTag(1_000_000L));  // ms
        metaMap.put("TimeModified",  new NbtTag.LongTag(2_000_000L));  // ms
        metaMap.put("RegionCount",   new NbtTag.IntTag(2));
        metaMap.put("EnclosingSize", xyz(5, 1, 2));

        // Root
        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("MinecraftDataVersion", new NbtTag.IntTag(3953));
        root.put("Version",              new NbtTag.IntTag(7));
        root.put("Metadata",             new NbtTag.CompoundTag(metaMap));
        root.put("Regions",              new NbtTag.CompoundTag(regionsMap));
        return new NbtTag.CompoundTag(root);
    }

    /**
     * Generates a fixture with a negative-size subregion to test normalization.
     * Subregion at position (2,0,2), size (-2,-1,-2): actual extent X=[0,2), Y=[0,1), Z=[0,2)
     */
    static NbtTag.CompoundTag buildNegativeSizeFixture() {
        // Palette: [air, stone]
        NbtTag.CompoundTag airEntry   = compound("Name", str("minecraft:air"));
        NbtTag.CompoundTag stoneEntry = compound("Name", str("minecraft:stone"));
        // 2×1×2 = 4 blocks, all stone (local ID=1)
        // bpe=2, word = (1)|(1<<2)|(1<<4)|(1<<6) = 1+4+16+64 = 85
        long[] blockStates = { 85L };

        Map<String, NbtTag> sr = new LinkedHashMap<>();
        sr.put("BlockStatePalette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND,
                List.of(airEntry, stoneEntry)));
        sr.put("BlockStates",  new NbtTag.LongArrayTag(blockStates));
        sr.put("TileEntities", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        sr.put("Entities",     new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        sr.put("Position",     xyz(2, 0, 2));
        sr.put("Size",         xyz(-2, -1, -2)); // extends to (0,0,0) → min corner (0,0,0)

        Map<String, NbtTag> regionsMap = new LinkedHashMap<>();
        regionsMap.put("NegativeRegion", new NbtTag.CompoundTag(sr));

        Map<String, NbtTag> metaMap = new LinkedHashMap<>();
        metaMap.put("Name",          str("Negative Size Test"));
        metaMap.put("Author",        str("tester"));
        metaMap.put("Description",   str(""));
        metaMap.put("TimeCreated",   new NbtTag.LongTag(0L));
        metaMap.put("TimeModified",  new NbtTag.LongTag(0L));
        metaMap.put("RegionCount",   new NbtTag.IntTag(1));
        metaMap.put("EnclosingSize", xyz(2, 1, 2));

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("MinecraftDataVersion", new NbtTag.IntTag(3953));
        root.put("Version",              new NbtTag.IntTag(7));
        root.put("Metadata",             new NbtTag.CompoundTag(metaMap));
        root.put("Regions",              new NbtTag.CompoundTag(regionsMap));
        return new NbtTag.CompoundTag(root);
    }

    /**
     * Writes the two-subregion fixture as a gzip-compressed .litematic file to the given path.
     */
    public static byte[] twoSubregionFixtureBytes() throws Exception {
        return NbtWriter.writeCompound(buildTwoSubregionFixture(), "");
    }

    /**
     * Saves the fixture to src/test/resources/fixtures/test.litematic if not already present.
     * Run once during test setup so the fixture is available for ConvertCommandTest.
     */
    @BeforeAll
    static void saveFixture() throws Exception {
        URL resourceRoot = LitematicaFormatTest.class.getClassLoader().getResource(".");
        if (resourceRoot == null) return;
        String resourcePath = resourceRoot.toURI().toString().replace("file:", "");
        File fixturesDir = new File(resourcePath, "fixtures");
        fixturesDir.mkdirs();
        File fixtureFile = new File(fixturesDir, "test.litematic");
        if (!fixtureFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(fixtureFile)) {
                fos.write(twoSubregionFixtureBytes());
            }
        }
    }

    // =========================================================================
    // Test 1: layer count matches subregion count
    // =========================================================================

    @Test
    void litematicToMsf_layerCountMatchesSubregionCount() throws Exception {
        NbtTag.CompoundTag root = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);
        assertEquals(2, msf.layerIndex().layers().size(), "layer count must equal subregion count");
    }

    // =========================================================================
    // Test 2: layer names match subregion names
    // =========================================================================

    @Test
    void litematicToMsf_layerNamesMatchSubregionNames() throws Exception {
        NbtTag.CompoundTag root = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);
        List<String> names = msf.layerIndex().layers().stream().map(MsfLayer::name).toList();
        assertTrue(names.contains("Subregion1"), "layer name must be Subregion1");
        assertTrue(names.contains("Subregion2"), "layer name must be Subregion2");
    }

    // =========================================================================
    // Test 3: block at known position matches expected blockstate
    // =========================================================================

    @Test
    void litematicToMsf_blockAtKnownPosition() throws Exception {
        NbtTag.CompoundTag root = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);

        // Subregion1 is 2×1×2 at world (0,0,0) → anchor (0,0,0) → MSF origin (0,0,0)
        // Layer 0 = Subregion1 (first in iteration order)
        MsfLayer layer1 = msf.layerIndex().layers().stream()
                .filter(l -> "Subregion1".equals(l.name())).findFirst().orElseThrow();
        MsfRegion region = layer1.regions().get(0);

        List<String> palette = msf.palette().entries();
        int stoneId = palette.indexOf("minecraft:stone");
        assertNotEquals(-1, stoneId, "stone must be in global palette");

        // Block at (0,0,0) in Subregion1 → YZX index = 0*2*2 + 0*2 + 0 = 0 → stone
        int[] blockData = region.blockData();
        assertEquals(stoneId, blockData[0], "block at (0,0,0) must be stone");
    }

    // =========================================================================
    // Test 4: negative-size subregion normalizes correctly
    // =========================================================================

    @Test
    void litematicToMsf_negativeSizeNormalizesCorrectly() throws Exception {
        NbtTag.CompoundTag root = buildNegativeSizeFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);

        assertEquals(1, msf.layerIndex().layers().size());
        MsfRegion region = msf.layerIndex().layers().get(0).regions().get(0);

        // Dimensions must be positive
        assertTrue(region.sizeX() > 0, "sizeX must be positive");
        assertTrue(region.sizeY() > 0, "sizeY must be positive");
        assertTrue(region.sizeZ() > 0, "sizeZ must be positive");

        // Expected: size = (2,1,2), origin = (0,0,0) (min corner = (0,0,0), anchor = (0,0,0))
        assertEquals(2, region.sizeX());
        assertEquals(1, region.sizeY());
        assertEquals(2, region.sizeZ());
        assertEquals(0, region.originX());
        assertEquals(0, region.originY());
        assertEquals(0, region.originZ());
    }

    // =========================================================================
    // Test 5: metadata maps correctly (name, author, timestamps /1000)
    // =========================================================================

    @Test
    void litematicToMsf_metadataMapping() throws Exception {
        NbtTag.CompoundTag root = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);

        MsfMetadata meta = msf.metadata();
        assertEquals("Test Schematic", meta.name(),   "name must be mapped");
        assertEquals("TestAuthor",     meta.author(), "author must be mapped");
        // TimeCreated=1_000_000 ms → 1000 s
        assertEquals(1000L,  meta.createdTimestamp(),  "createdTimestamp must be ms/1000");
        // TimeModified=2_000_000 ms → 2000 s
        assertEquals(2000L, meta.modifiedTimestamp(), "modifiedTimestamp must be ms/1000");
    }

    // =========================================================================
    // Test 6: block entity in source appears in MsfFile.blockEntities()
    // =========================================================================

    @Test
    void litematicToMsf_blockEntityPresent() throws Exception {
        NbtTag.CompoundTag root = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(root);

        assertTrue(msf.blockEntities().isPresent(), "blockEntities must be present");
        assertEquals(1, msf.blockEntities().get().size(), "must have exactly one block entity");

        MsfBlockEntity be = msf.blockEntities().get().get(0);
        assertEquals("minecraft:chest", be.blockEntityType(), "block entity type must be minecraft:chest");
        // "id" must have been stripped from payload
        NbtTag.CompoundTag payload = NbtReader.readCompoundRaw(be.nbtPayload());
        assertFalse(payload.entries().containsKey("id"), "id must be stripped from block entity payload");
        assertTrue(payload.entries().containsKey("CustomName"), "CustomName must be preserved");

        // Block entity at subregion-relative (0,0,0) in Subregion1 whose min corner is at world (0,0,0)
        // → anchor-relative (0,0,0)
        assertEquals(0, be.positionX(), "block entity X must be anchor-relative");
        assertEquals(0, be.positionY(), "block entity Y must be anchor-relative");
        assertEquals(0, be.positionZ(), "block entity Z must be anchor-relative");
    }

    // =========================================================================
    // Test 7: msfToLitematic — subregion count matches layer count
    // =========================================================================

    @Test
    void msfToLitematic_subregionCountMatchesLayerCount() throws Exception {
        NbtTag.CompoundTag litematic = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(litematic);
        NbtTag.CompoundTag result = LitematicaFormat.msfToLitematic(msf);

        NbtTag.CompoundTag regions = (NbtTag.CompoundTag) result.entries().get("Regions");
        assertEquals(msf.layerIndex().layers().size(), regions.entries().size(),
                "subregion count must match layer count");
    }

    // =========================================================================
    // Test 8: msfToLitematic — Version field is 7
    // =========================================================================

    @Test
    void msfToLitematic_versionIs7() throws Exception {
        NbtTag.CompoundTag litematic = buildTwoSubregionFixture();
        MsfFile msf = LitematicaFormat.litematicToMsf(litematic);
        NbtTag.CompoundTag result = LitematicaFormat.msfToLitematic(msf);

        int version = ((NbtTag.IntTag) result.entries().get("Version")).value();
        assertEquals(7, version, "exported Version must be 7");
    }

    // =========================================================================
    // Test 9: msfToLitematic — MSF metadata warning emitted on export
    // =========================================================================

    @Test
    void msfToLitematic_metadataWarningEmitted() throws Exception {
        // Capture stdout
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream old = System.out;
        System.setOut(new java.io.PrintStream(baos));
        try {
            NbtTag.CompoundTag litematic = buildTwoSubregionFixture();
            MsfFile msf = LitematicaFormat.litematicToMsf(litematic);
            LitematicaFormat.msfToLitematic(msf);
        } finally {
            System.setOut(old);
        }
        String output = baos.toString();
        assertTrue(output.contains("MSF-specific metadata was not exported"),
                "must emit MSF metadata warning on export");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static NbtTag.CompoundTag compound(String key, NbtTag value) {
        return new NbtTag.CompoundTag(Map.of(key, value));
    }

    private static NbtTag.StringTag str(String value) {
        return new NbtTag.StringTag(value);
    }

    private static NbtTag.CompoundTag xyz(int x, int y, int z) {
        Map<String, NbtTag> m = new LinkedHashMap<>();
        m.put("x", new NbtTag.IntTag(x));
        m.put("y", new NbtTag.IntTag(y));
        m.put("z", new NbtTag.IntTag(z));
        return new NbtTag.CompoundTag(m);
    }
}

package dev.msf.cli.convert;

import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VanillaStructureFormat} — nbt↔msf conversion.
 */
class VanillaStructureFormatTest {

    // =========================================================================
    // Helpers: build a minimal vanilla structure NBT in memory
    // =========================================================================

    /**
     * Builds a 2×1×2 vanilla structure NBT compound (no gzip):
     *   palette[0] = minecraft:air
     *   palette[1] = minecraft:stone
     *   palette[2] = minecraft:dirt[snowy=false]
     *
     *   blocks (YZX layout in vanilla = unordered list):
     *     (0,0,0) → state=1 (stone)
     *     (1,0,0) → state=2 (dirt)
     *     (0,0,1) → state=1 (stone)
     *     (1,0,1) → state=0 (air)
     *
     *   DataVersion = 3953
     */
    private static NbtTag.CompoundTag minimalStructureNbt() {
        // palette
        NbtTag.CompoundTag airEntry   = compound("Name", str("minecraft:air"));
        NbtTag.CompoundTag stoneEntry = compound("Name", str("minecraft:stone"));
        // dirt with a property to verify Properties round-trip
        Map<String, NbtTag> dirtMap = new LinkedHashMap<>();
        dirtMap.put("Name", str("minecraft:dirt"));
        dirtMap.put("Properties", new NbtTag.CompoundTag(Map.of("snowy", str("false"))));
        NbtTag.CompoundTag dirtEntry = new NbtTag.CompoundTag(dirtMap);

        // blocks
        NbtTag block00 = block(0, 0, 0, 1);  // stone
        NbtTag block10 = block(1, 0, 0, 2);  // dirt
        NbtTag block01 = block(0, 0, 1, 1);  // stone
        NbtTag block11 = block(1, 0, 1, 0);  // air

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("DataVersion", new NbtTag.IntTag(3953));
        root.put("size",        intList(2, 1, 2));
        root.put("palette",     new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(airEntry, stoneEntry, dirtEntry)));
        root.put("blocks",      new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(block00, block10, block01, block11)));
        root.put("entities",    new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        return new NbtTag.CompoundTag(root);
    }

    // =========================================================================
    // Test 1: nbt → msf — palette, block data, dimensions, mcDataVersion
    // =========================================================================

    @Test
    void nbtToMsf_basicStructure() throws Exception {
        NbtTag.CompoundTag nbt = minimalStructureNbt();
        MsfFile msf = VanillaStructureFormat.nbtToMsf(nbt);

        assertEquals(3953L, msf.header().mcDataVersion());
        assertEquals(1, msf.layerIndex().layers().size());
        assertEquals(1, msf.layerIndex().layers().get(0).regions().size());

        var region = msf.layerIndex().layers().get(0).regions().get(0);
        assertEquals(2, region.sizeX());
        assertEquals(1, region.sizeY());
        assertEquals(2, region.sizeZ());

        // Palette: air + stone + dirt[snowy=false]
        List<String> paletteEntries = msf.palette().entries();
        assertTrue(paletteEntries.contains("minecraft:air"),                 "palette must contain air");
        assertTrue(paletteEntries.contains("minecraft:stone"),               "palette must contain stone");
        assertTrue(paletteEntries.contains("minecraft:dirt[snowy=false]"),   "palette must contain dirt[snowy=false]");

        // Block data YZX: y=0, z=0: [stone(x=0), dirt(x=1)]; z=1: [stone(x=0), air(x=1)]
        int airId   = paletteEntries.indexOf("minecraft:air");
        int stoneId = paletteEntries.indexOf("minecraft:stone");
        int dirtId  = paletteEntries.indexOf("minecraft:dirt[snowy=false]");

        int[] data = region.blockData();
        // index = y*sZ*sX + z*sX + x  (sX=2, sZ=2)
        assertEquals(stoneId, data[0 * 2 * 2 + 0 * 2 + 0], "block(0,0,0) should be stone");
        assertEquals(dirtId,  data[0 * 2 * 2 + 0 * 2 + 1], "block(1,0,0) should be dirt");
        assertEquals(stoneId, data[0 * 2 * 2 + 1 * 2 + 0], "block(0,0,1) should be stone");
        assertEquals(airId,   data[0 * 2 * 2 + 1 * 2 + 1], "block(1,0,1) should be air");

        assertFalse(msf.entities().isPresent());
        assertFalse(msf.blockEntities().isPresent());
    }

    // =========================================================================
    // Test 2: msf → nbt round-trip — size, palette entries, block states
    // =========================================================================

    @Test
    void msfToNbt_basicStructure() throws Exception {
        NbtTag.CompoundTag original = minimalStructureNbt();
        MsfFile msf = VanillaStructureFormat.nbtToMsf(original);
        NbtTag.CompoundTag converted = VanillaStructureFormat.msfToNbt(msf);

        // DataVersion
        assertEquals(3953, ((NbtTag.IntTag) converted.entries().get("DataVersion")).value());

        // Size
        NbtTag.ListTag size = (NbtTag.ListTag) converted.entries().get("size");
        assertEquals(2, ((NbtTag.IntTag) size.elements().get(0)).value());
        assertEquals(1, ((NbtTag.IntTag) size.elements().get(1)).value());
        assertEquals(2, ((NbtTag.IntTag) size.elements().get(2)).value());

        // Palette must include stone and dirt entries
        NbtTag.ListTag palette = (NbtTag.ListTag) converted.entries().get("palette");
        List<String> names = palette.elements().stream()
                .map(e -> ((NbtTag.StringTag) ((NbtTag.CompoundTag) e).entries().get("Name")).value())
                .toList();
        assertTrue(names.contains("minecraft:stone"));
        assertTrue(names.contains("minecraft:dirt"));

        // Blocks list length = sizeX * sizeY * sizeZ = 4
        NbtTag.ListTag blocks = (NbtTag.ListTag) converted.entries().get("blocks");
        assertEquals(4, blocks.elements().size());
    }

    // =========================================================================
    // Test 3: nbt → msf with a block entity (nbt field in block)
    // =========================================================================

    @Test
    void nbtToMsf_blockEntity() throws Exception {
        // Build a 1×1×1 structure with a chest at (0,0,0)
        NbtTag.CompoundTag stoneEntry = compound("Name", str("minecraft:chest"));

        Map<String, NbtTag> beNbt = new LinkedHashMap<>();
        beNbt.put("id", str("minecraft:chest"));
        beNbt.put("x", new NbtTag.IntTag(0));
        beNbt.put("y", new NbtTag.IntTag(0));
        beNbt.put("z", new NbtTag.IntTag(0));
        beNbt.put("CustomName", str("{\"text\":\"Loot\"}"));

        Map<String, NbtTag> blockMap = new LinkedHashMap<>();
        blockMap.put("pos",   intList(0, 0, 0));
        blockMap.put("state", new NbtTag.IntTag(0));
        blockMap.put("nbt",   new NbtTag.CompoundTag(beNbt));

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("DataVersion", new NbtTag.IntTag(3953));
        root.put("size",    intList(1, 1, 1));
        root.put("palette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(stoneEntry)));
        root.put("blocks",  new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(new NbtTag.CompoundTag(blockMap))));
        root.put("entities", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));

        MsfFile msf = VanillaStructureFormat.nbtToMsf(new NbtTag.CompoundTag(root));

        assertTrue(msf.blockEntities().isPresent());
        assertEquals(1, msf.blockEntities().get().size());
        MsfBlockEntity be = msf.blockEntities().get().get(0);
        assertEquals("minecraft:chest", be.blockEntityType());
        assertEquals(0, be.positionX());
        assertEquals(0, be.positionY());
        assertEquals(0, be.positionZ());
        // "id" must have been stripped from the payload
        NbtTag.CompoundTag restoredBe = NbtReader.readCompoundRaw(be.nbtPayload());
        assertFalse(restoredBe.entries().containsKey("id"),   "id must be stripped from block entity payload");
        assertFalse(restoredBe.entries().containsKey("x"),    "x must be stripped from block entity payload");
        assertTrue(restoredBe.entries().containsKey("CustomName"), "CustomName must be preserved in payload");
    }

    // =========================================================================
    // Test 4: nbt → msf with an entity
    // =========================================================================

    @Test
    void nbtToMsf_entity() throws Exception {
        // Build entity NBT with "id" field (as vanilla stores it)
        Map<String, NbtTag> entityNbt = new LinkedHashMap<>();
        entityNbt.put("id", str("minecraft:cow"));
        entityNbt.put("Health", new NbtTag.FloatTag(10.0f));

        Map<String, NbtTag> entityEntry = new LinkedHashMap<>();
        entityEntry.put("pos",      doubleList(1.5, 64.0, 2.5));
        entityEntry.put("blockPos", intList(1, 64, 2));
        entityEntry.put("nbt",      new NbtTag.CompoundTag(entityNbt));

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("DataVersion", new NbtTag.IntTag(3953));
        root.put("size",    intList(4, 1, 4));
        root.put("palette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(compound("Name", str("minecraft:air")))));
        root.put("blocks",  new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of()));
        root.put("entities", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, List.of(new NbtTag.CompoundTag(entityEntry))));

        MsfFile msf = VanillaStructureFormat.nbtToMsf(new NbtTag.CompoundTag(root));

        assertTrue(msf.entities().isPresent());
        assertEquals(1, msf.entities().get().size());
        MsfEntity entity = msf.entities().get().get(0);
        assertEquals("minecraft:cow", entity.entityType());
        assertEquals(1.5,  entity.positionX(), 1e-10);
        assertEquals(64.0, entity.positionY(), 1e-10);
        assertEquals(2.5,  entity.positionZ(), 1e-10);
        // "id" must have been stripped from the payload
        NbtTag.CompoundTag restoredNbt = NbtReader.readCompoundRaw(entity.nbtPayload());
        assertFalse(restoredNbt.entries().containsKey("id"), "id must be stripped from entity payload");
        assertTrue(restoredNbt.entries().containsKey("Health"), "Health must be preserved in entity payload");
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

    private static NbtTag block(int x, int y, int z, int state) {
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

    private static NbtTag.ListTag doubleList(double... values) {
        var elems = new java.util.ArrayList<NbtTag>();
        for (double v : values) elems.add(new NbtTag.DoubleTag(v));
        return new NbtTag.ListTag(NbtTag.TYPE_DOUBLE, elems);
    }
}

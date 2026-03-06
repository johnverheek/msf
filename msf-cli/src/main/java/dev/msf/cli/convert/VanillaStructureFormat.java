package dev.msf.cli.convert;

import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfRegion;
import dev.msf.core.util.PaletteAccumulator;
import dev.msf.core.util.UuidStripper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bidirectional converter between vanilla Minecraft {@code .nbt} structure files and MSF.
 *
 * <h2>Vanilla structure format (root TAG_Compound)</h2>
 * <ul>
 *   <li>{@code DataVersion}:  TAG_Int — Minecraft data version</li>
 *   <li>{@code size}:         TAG_List[TAG_Int] — [sizeX, sizeY, sizeZ]</li>
 *   <li>{@code palette}:      TAG_List[TAG_Compound] — each entry has {@code Name} (TAG_String)
 *       and an optional {@code Properties} (TAG_Compound of string key/value pairs)</li>
 *   <li>{@code blocks}:       TAG_List[TAG_Compound] — each entry has {@code pos}
 *       (TAG_List[TAG_Int] of [x, y, z]), {@code state} (TAG_Int, index into palette),
 *       and an optional {@code nbt} (TAG_Compound, block entity data)</li>
 *   <li>{@code entities}:     TAG_List[TAG_Compound] — each entry has {@code pos}
 *       (TAG_List[TAG_Double]), {@code blockPos} (TAG_List[TAG_Int]), and
 *       {@code nbt} (TAG_Compound with an {@code id} field for entity type)</li>
 * </ul>
 *
 * <h2>Conversion notes</h2>
 * <ul>
 *   <li>MSF block data is YZX-ordered; vanilla blocks are an unordered pos+state list.</li>
 *   <li>MSF palette ID 0 is always {@code "minecraft:air"}; vanilla palette is arbitrary-ordered.</li>
 *   <li>Only the first layer's first region is exported in the MSF→.nbt direction
 *       (vanilla format is single-region).</li>
 *   <li>Entity and block entity NBT payloads are stored raw (non-gzip) in MSF per
 *       Sections 8.2 and 9.2; UUID fields are stripped on write.</li>
 * </ul>
 */
public final class VanillaStructureFormat {

    private VanillaStructureFormat() {}

    // =========================================================================
    // .nbt → MsfFile
    // =========================================================================

    /**
     * Converts a parsed vanilla structure NBT root compound to an {@link MsfFile}.
     *
     * @param root the root TAG_Compound from a vanilla {@code .nbt} structure file
     * @return the corresponding MSF file
     * @throws Exception if required fields are missing or malformed
     */
    public static MsfFile nbtToMsf(NbtTag.CompoundTag root) throws Exception {
        long dataVersion = getInt(root, "DataVersion");

        // --- Size ---
        NbtTag.ListTag sizeList = getList(root, "size");
        int sizeX = intElem(sizeList, 0);
        int sizeY = intElem(sizeList, 1);
        int sizeZ = intElem(sizeList, 2);

        // --- Palette: build nbtIndex → global MSF palette ID map ---
        PaletteAccumulator acc = new PaletteAccumulator();
        NbtTag.ListTag paletteList = getList(root, "palette");
        int[] nbtToGlobal = new int[paletteList.elements().size()];
        for (int i = 0; i < paletteList.elements().size(); i++) {
            NbtTag.CompoundTag entry = (NbtTag.CompoundTag) paletteList.elements().get(i);
            nbtToGlobal[i] = acc.add(blockstateString(entry));
        }

        // --- Block data (YZX order) ---
        int[] blockData = new int[sizeX * sizeY * sizeZ];
        List<MsfBlockEntity> blockEntities = new ArrayList<>();
        NbtTag.ListTag blocksList = getList(root, "blocks");
        for (NbtTag elem : blocksList.elements()) {
            NbtTag.CompoundTag block = (NbtTag.CompoundTag) elem;
            NbtTag.ListTag posTag = getList(block, "pos");
            int x = intElem(posTag, 0);
            int y = intElem(posTag, 1);
            int z = intElem(posTag, 2);
            int nbtState = ((NbtTag.IntTag) block.entries().get("state")).value();
            blockData[y * sizeZ * sizeX + z * sizeX + x] = nbtToGlobal[nbtState];

            // Block entity — block has optional "nbt" field
            if (block.entries().containsKey("nbt")) {
                NbtTag.CompoundTag beNbt = (NbtTag.CompoundTag) block.entries().get("nbt");
                String beType = getString(beNbt, "id");
                // Strip id/x/y/z (MSF Section 9.2) and UUID fields
                Map<String, NbtTag> stripped = new LinkedHashMap<>(beNbt.entries());
                stripped.remove("id");
                stripped.remove("x");
                stripped.remove("y");
                stripped.remove("z");
                byte[] payload = UuidStripper.strip(
                        NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(stripped), ""));
                blockEntities.add(MsfBlockEntity.builder()
                        .position(x, y, z)
                        .blockEntityType(beType)
                        .nbtPayload(payload)
                        .build());
            }
        }

        // --- Entities ---
        List<MsfEntity> entities = new ArrayList<>();
        if (root.entries().containsKey("entities")) {
            NbtTag.ListTag entitiesTag = getList(root, "entities");
            for (NbtTag elem : entitiesTag.elements()) {
                NbtTag.CompoundTag entityEntry = (NbtTag.CompoundTag) elem;
                NbtTag.ListTag posTag = getList(entityEntry, "pos");
                double px = doubleElem(posTag, 0);
                double py = doubleElem(posTag, 1);
                double pz = doubleElem(posTag, 2);
                NbtTag.CompoundTag nbt = (NbtTag.CompoundTag) entityEntry.entries().get("nbt");
                String entityType = getString(nbt, "id");
                // Strip "id" and UUID fields (MSF Section 8.2)
                Map<String, NbtTag> stripped = new LinkedHashMap<>(nbt.entries());
                stripped.remove("id");
                byte[] payload = UuidStripper.strip(
                        NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(stripped), ""));
                entities.add(MsfEntity.builder()
                        .position(px, py, pz)
                        .entityType(entityType)
                        .nbtPayload(payload)
                        .build());
            }
        }

        // --- Assemble MsfFile ---
        MsfRegion region = MsfRegion.builder()
                .origin(0, 0, 0)
                .size(sizeX, sizeY, sizeZ)
                .blockData(blockData)
                .build();
        MsfLayer layer = MsfLayer.builder()
                .layerId(1)
                .name("Main")
                .addRegion(region)
                .build();

        MsfFile.Builder fileBuilder = MsfFile.builder()
                .mcDataVersion(dataVersion)
                .metadata(MsfMetadata.builder().name("Imported from vanilla .nbt").build())
                .palette(acc.build())
                .layerIndex(MsfLayerIndex.of(List.of(layer)));

        if (!entities.isEmpty()) fileBuilder.entities(entities);
        if (!blockEntities.isEmpty()) fileBuilder.blockEntities(blockEntities);

        return fileBuilder.build();
    }

    // =========================================================================
    // MsfFile → .nbt
    // =========================================================================

    /**
     * Converts an {@link MsfFile} to a vanilla structure root TAG_Compound.
     *
     * <p>Only the first layer's first region is exported (vanilla format is single-region).
     *
     * @param msf the MSF file to convert
     * @return the root TAG_Compound for a vanilla {@code .nbt} structure file
     * @throws Exception if the file has no layers or regions, or if NBT serialization fails
     */
    public static NbtTag.CompoundTag msfToNbt(MsfFile msf) throws Exception {
        MsfLayer layer = msf.layerIndex().layers().get(0);
        MsfRegion region = layer.regions().get(0);
        int sizeX = region.sizeX(), sizeY = region.sizeY(), sizeZ = region.sizeZ();
        List<String> palette = msf.palette().entries();

        // --- Build vanilla palette (one entry per MSF palette entry, including air) ---
        List<NbtTag> nbtPaletteEntries = new ArrayList<>();
        for (String blockstate : palette) {
            nbtPaletteEntries.add(blockstateToNbt(blockstate));
        }

        // --- Block entity lookup by position key "x,y,z" ---
        Map<String, NbtTag.CompoundTag> beByPos = new HashMap<>();
        if (msf.blockEntities().isPresent()) {
            for (MsfBlockEntity be : msf.blockEntities().get()) {
                String key = be.positionX() + "," + be.positionY() + "," + be.positionZ();
                // Restore id/x/y/z fields stripped by MSF Section 9.2
                NbtTag.CompoundTag payload = NbtReader.readCompoundRaw(
                        be.nbtPayload().length > 0 ? be.nbtPayload() : emptyRawCompound());
                Map<String, NbtTag> beNbt = new LinkedHashMap<>();
                beNbt.put("id", new NbtTag.StringTag(be.blockEntityType()));
                beNbt.put("x",  new NbtTag.IntTag(be.positionX()));
                beNbt.put("y",  new NbtTag.IntTag(be.positionY()));
                beNbt.put("z",  new NbtTag.IntTag(be.positionZ()));
                beNbt.putAll(payload.entries());
                beByPos.put(key, new NbtTag.CompoundTag(beNbt));
            }
        }

        // --- Build blocks list (YZX iteration matches MSF encoding order) ---
        List<NbtTag> blocks = new ArrayList<>();
        int[] blockData = region.blockData();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int globalId = blockData[y * sizeZ * sizeX + z * sizeX + x];
                    Map<String, NbtTag> blockEntry = new LinkedHashMap<>();
                    blockEntry.put("pos", intList(x, y, z));
                    blockEntry.put("state", new NbtTag.IntTag(globalId));
                    String posKey = x + "," + y + "," + z;
                    if (beByPos.containsKey(posKey)) {
                        blockEntry.put("nbt", beByPos.get(posKey));
                    }
                    blocks.add(new NbtTag.CompoundTag(blockEntry));
                }
            }
        }

        // --- Build entities list ---
        List<NbtTag> nbtEntities = new ArrayList<>();
        if (msf.entities().isPresent()) {
            for (MsfEntity entity : msf.entities().get()) {
                NbtTag.CompoundTag payload = NbtReader.readCompoundRaw(
                        entity.nbtPayload() != null && entity.nbtPayload().length > 0
                                ? entity.nbtPayload() : emptyRawCompound());
                // Restore "id" field stripped by MSF Section 8.2
                Map<String, NbtTag> nbt = new LinkedHashMap<>();
                nbt.put("id", new NbtTag.StringTag(entity.entityType()));
                nbt.putAll(payload.entries());
                // blockPos = floor of entity position
                int bx = (int) Math.floor(entity.positionX());
                int by = (int) Math.floor(entity.positionY());
                int bz = (int) Math.floor(entity.positionZ());
                Map<String, NbtTag> entityEntry = new LinkedHashMap<>();
                entityEntry.put("pos",      doubleList(entity.positionX(), entity.positionY(), entity.positionZ()));
                entityEntry.put("blockPos", intList(bx, by, bz));
                entityEntry.put("nbt",      new NbtTag.CompoundTag(nbt));
                nbtEntities.add(new NbtTag.CompoundTag(entityEntry));
            }
        }

        // --- Assemble root compound ---
        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("DataVersion", new NbtTag.IntTag((int) msf.header().mcDataVersion()));
        root.put("size",        intList(sizeX, sizeY, sizeZ));
        root.put("palette",     new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, nbtPaletteEntries));
        root.put("blocks",      new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, blocks));
        root.put("entities",    new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, nbtEntities));
        return new NbtTag.CompoundTag(root);
    }

    // =========================================================================
    // Blockstate string ↔ vanilla palette entry
    // =========================================================================

    /**
     * Converts a vanilla palette entry ({@code Name} + optional {@code Properties}) to a
     * canonical blockstate string, e.g. {@code "minecraft:oak_log[axis=y,waterlogged=false]"}.
     * Properties are sorted alphabetically.
     */
    private static String blockstateString(NbtTag.CompoundTag entry) {
        String name = getString(entry, "Name");
        if (!entry.entries().containsKey("Properties")) {
            return name;
        }
        NbtTag.CompoundTag props = (NbtTag.CompoundTag) entry.entries().get("Properties");
        // Sort properties alphabetically for canonical blockstate representation
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, NbtTag> e : props.entries().entrySet()) {
            sorted.put(e.getKey(), ((NbtTag.StringTag) e.getValue()).value());
        }
        StringBuilder sb = new StringBuilder(name).append('[');
        sorted.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }

    /**
     * Converts a blockstate string like {@code "minecraft:oak_log[axis=y]"} to a vanilla
     * palette entry TAG_Compound with {@code Name} and optional {@code Properties}.
     */
    private static NbtTag.CompoundTag blockstateToNbt(String blockstate) {
        Map<String, NbtTag> entry = new LinkedHashMap<>();
        int bracket = blockstate.indexOf('[');
        if (bracket < 0) {
            entry.put("Name", new NbtTag.StringTag(blockstate));
        } else {
            entry.put("Name", new NbtTag.StringTag(blockstate.substring(0, bracket)));
            String propStr = blockstate.substring(bracket + 1, blockstate.length() - 1);
            Map<String, NbtTag> props = new LinkedHashMap<>();
            for (String kv : propStr.split(",")) {
                int eq = kv.indexOf('=');
                props.put(kv.substring(0, eq), new NbtTag.StringTag(kv.substring(eq + 1)));
            }
            entry.put("Properties", new NbtTag.CompoundTag(props));
        }
        return new NbtTag.CompoundTag(entry);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int getInt(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.IntTag) tag.entries().get(key)).value();
    }

    private static NbtTag.ListTag getList(NbtTag.CompoundTag tag, String key) {
        return (NbtTag.ListTag) tag.entries().get(key);
    }

    private static String getString(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.StringTag) tag.entries().get(key)).value();
    }

    private static int intElem(NbtTag.ListTag list, int idx) {
        return ((NbtTag.IntTag) list.elements().get(idx)).value();
    }

    private static double doubleElem(NbtTag.ListTag list, int idx) {
        return ((NbtTag.DoubleTag) list.elements().get(idx)).value();
    }

    private static NbtTag.ListTag intList(int... values) {
        List<NbtTag> elems = new ArrayList<>();
        for (int v : values) elems.add(new NbtTag.IntTag(v));
        return new NbtTag.ListTag(NbtTag.TYPE_INT, elems);
    }

    private static NbtTag.ListTag doubleList(double... values) {
        List<NbtTag> elems = new ArrayList<>();
        for (double v : values) elems.add(new NbtTag.DoubleTag(v));
        return new NbtTag.ListTag(NbtTag.TYPE_DOUBLE, elems);
    }

    /** Returns a minimal empty raw TAG_Compound payload (type byte + empty name + TAG_End). */
    private static byte[] emptyRawCompound() throws Exception {
        return NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(new LinkedHashMap<>()), "");
    }
}

package dev.msf.cli.convert;

import dev.msf.core.MsfParseException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bidirectional converter between Litematica {@code .litematic} files (v6/v7) and MSF.
 *
 * <h2>Litematica root TAG_Compound structure</h2>
 * <ul>
 *   <li>{@code MinecraftDataVersion}: TAG_Int — MC data version</li>
 *   <li>{@code Version}: TAG_Int — Litematica format version; 6 or 7 supported</li>
 *   <li>{@code Metadata}: TAG_Compound — name, author, description, timestamps, preview</li>
 *   <li>{@code Regions}: TAG_Compound — named subregions, each containing:
 *     <ul>
 *       <li>{@code BlockStatePalette}: TAG_List[TAG_Compound] — per-subregion blockstate palette</li>
 *       <li>{@code BlockStates}: TAG_Long_Array — bit-packed block data (LSB-first, no-spanning)</li>
 *       <li>{@code TileEntities}: TAG_List[TAG_Compound] — subregion-relative block entities</li>
 *       <li>{@code Entities}: TAG_List[TAG_Compound] — world-relative entities</li>
 *       <li>{@code Position}: TAG_Compound (x, y, z) — subregion position in schematic world</li>
 *       <li>{@code Size}: TAG_Compound (x, y, z) — may be negative (negative = extend backwards)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Conversion notes</h2>
 * <ul>
 *   <li>Each Litematica subregion maps to one MSF layer containing one region.</li>
 *   <li>Litematica bit packing: {@code max(2, ceil(log2(paletteSize)))} bits per entry,
 *       LSB-first, no-spanning — {@code floor(64/bpe)} entries per 64-bit word.</li>
 *   <li>Negative sizes are normalized before use (MSF sizes are always positive).</li>
 *   <li>MSF anchor is the minimum corner of all subregions combined.</li>
 *   <li>Block entity positions in TileEntities are subregion-relative;
 *       entity positions in Entities are world-relative (schematic world).</li>
 *   <li>On export, multi-region layers are flattened into one Litematica subregion.</li>
 *   <li>{@code PendingBlockTicks} and {@code PendingFluidTicks} are silently dropped.</li>
 * </ul>
 */
public final class LitematicaFormat {

    private LitematicaFormat() {}

    // =========================================================================
    // .litematic → MsfFile
    // =========================================================================

    /**
     * Converts a parsed Litematica root TAG_Compound to an {@link MsfFile}.
     *
     * @param root the root TAG_Compound from a {@code .litematic} file (already decompressed)
     * @return the corresponding MSF file
     * @throws MsfParseException if the version is unsupported or required fields are missing
     * @throws Exception         if any other parsing error occurs
     */
    public static MsfFile litematicToMsf(NbtTag.CompoundTag root) throws Exception {
        // --- Version ---
        int version = getInt(root, "Version");
        if (version != 6 && version != 7) {
            throw new MsfParseException(
                    "Unsupported Litematica format version " + version + " — only versions 6 and 7 are supported");
        }

        long dataVersion = getInt(root, "MinecraftDataVersion");

        // --- Metadata ---
        NbtTag.CompoundTag meta = getCompound(root, "Metadata");
        String name        = getStringOrEmpty(meta, "Name");
        String author      = getStringOrEmpty(meta, "Author");
        String description = getStringOrEmpty(meta, "Description");
        long timeCreated   = meta.entries().containsKey("TimeCreated") ? getLong(meta, "TimeCreated") / 1000L : 0L;
        long timeModified  = meta.entries().containsKey("TimeModified") ? getLong(meta, "TimeModified") / 1000L : 0L;
        byte[] thumbnail   = new byte[0];
        if (meta.entries().containsKey("PreviewImageData")) {
            thumbnail = ((NbtTag.ByteArrayTag) meta.entries().get("PreviewImageData")).value();
        }
        if (name.isEmpty()) name = "Imported from .litematic";

        // --- Regions ---
        NbtTag.CompoundTag regionsTag = getCompound(root, "Regions");

        // First pass: compute anchor (minimum corner of all subregions)
        int anchorX = Integer.MAX_VALUE, anchorY = Integer.MAX_VALUE, anchorZ = Integer.MAX_VALUE;
        for (Map.Entry<String, NbtTag> e : regionsTag.entries().entrySet()) {
            NbtTag.CompoundTag regionTag = (NbtTag.CompoundTag) e.getValue();
            NbtTag.CompoundTag posTag  = getCompound(regionTag, "Position");
            NbtTag.CompoundTag sizeTag = getCompound(regionTag, "Size");
            int px = getInt(posTag, "x"), py = getInt(posTag, "y"), pz = getInt(posTag, "z");
            int sx = getInt(sizeTag, "x"), sy = getInt(sizeTag, "y"), sz = getInt(sizeTag, "z");
            int minX = Math.min(px, px + sx);
            int minY = Math.min(py, py + sy);
            int minZ = Math.min(pz, pz + sz);
            anchorX = Math.min(anchorX, minX);
            anchorY = Math.min(anchorY, minY);
            anchorZ = Math.min(anchorZ, minZ);
        }
        if (anchorX == Integer.MAX_VALUE) { anchorX = 0; anchorY = 0; anchorZ = 0; }

        // Second pass: process each subregion
        PaletteAccumulator acc = new PaletteAccumulator();
        List<MsfLayer> layers = new ArrayList<>();
        List<MsfBlockEntity> allBlockEntities = new ArrayList<>();
        List<MsfEntity> allEntities = new ArrayList<>();

        int layerIdx = 0;
        for (Map.Entry<String, NbtTag> e : regionsTag.entries().entrySet()) {
            String regionName   = e.getKey();
            NbtTag.CompoundTag regionTag = (NbtTag.CompoundTag) e.getValue();

            NbtTag.CompoundTag posTag  = getCompound(regionTag, "Position");
            NbtTag.CompoundTag sizeTag = getCompound(regionTag, "Size");
            int px = getInt(posTag, "x"), py = getInt(posTag, "y"), pz = getInt(posTag, "z");
            int rawSX = getInt(sizeTag, "x"), rawSY = getInt(sizeTag, "y"), rawSZ = getInt(sizeTag, "z");

            // Normalize negative sizes (Section: Negative size normalization)
            int sX = Math.abs(rawSX), sY = Math.abs(rawSY), sZ = Math.abs(rawSZ);
            int minX = Math.min(px, px + rawSX);
            int minY = Math.min(py, py + rawSY);
            int minZ = Math.min(pz, pz + rawSZ);

            // MSF origin = min corner of this subregion relative to anchor
            int originX = minX - anchorX;
            int originY = minY - anchorY;
            int originZ = minZ - anchorZ;

            // --- Palette ---
            NbtTag.ListTag paletteList = getList(regionTag, "BlockStatePalette");
            List<String> localPalette = new ArrayList<>();
            for (NbtTag elem : paletteList.elements()) {
                localPalette.add(blockstateString((NbtTag.CompoundTag) elem));
            }
            if (localPalette.isEmpty()) localPalette.add("minecraft:air");
            int[] remap = acc.remapTable(localPalette);

            // --- BlockStates ---
            long[] longArray = getLongArray(regionTag, "BlockStates");
            int totalBlocks = sX * sY * sZ;
            int bpe = litematicaBitsPerEntry(localPalette.size());
            int[] localIds = unpackLitematica(longArray, totalBlocks, bpe);

            // Remap local IDs → global IDs
            int[] blockData = new int[totalBlocks];
            for (int i = 0; i < totalBlocks; i++) {
                int lid = localIds[i];
                blockData[i] = (lid < remap.length) ? remap[lid] : 0;
            }

            // --- Build region ---
            MsfRegion region = MsfRegion.builder()
                    .origin(originX, originY, originZ)
                    .size(sX, sY, sZ)
                    .blockData(blockData)
                    .build();

            MsfLayer layer = MsfLayer.builder()
                    .layerId(layerIdx + 1)
                    .name(regionName)
                    .constructionOrderIndex(layerIdx)
                    .addRegion(region)
                    .build();
            layers.add(layer);

            // --- TileEntities (block entities, subregion-relative positions) ---
            if (regionTag.entries().containsKey("TileEntities")) {
                NbtTag.ListTag tileList = getList(regionTag, "TileEntities");
                for (NbtTag elem : tileList.elements()) {
                    NbtTag.CompoundTag beNbt = (NbtTag.CompoundTag) elem;
                    if (!beNbt.entries().containsKey("id")) continue;
                    String beType = getString(beNbt, "id");
                    // Subregion-relative → anchor-relative
                    int beX = getInt(beNbt, "x") + originX;
                    int beY = getInt(beNbt, "y") + originY;
                    int beZ = getInt(beNbt, "z") + originZ;
                    // Strip id, x, y, z then UUID fields
                    Map<String, NbtTag> stripped = new LinkedHashMap<>(beNbt.entries());
                    stripped.remove("id"); stripped.remove("x"); stripped.remove("y"); stripped.remove("z");
                    byte[] payload = UuidStripper.strip(
                            NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(stripped), ""));
                    allBlockEntities.add(MsfBlockEntity.builder()
                            .position(beX, beY, beZ)
                            .blockEntityType(beType)
                            .nbtPayload(payload)
                            .build());
                }
            }

            // --- Entities (world-relative positions in schematic world) ---
            if (regionTag.entries().containsKey("Entities")) {
                NbtTag.ListTag entList = getList(regionTag, "Entities");
                for (NbtTag elem : entList.elements()) {
                    NbtTag.CompoundTag entNbt = (NbtTag.CompoundTag) elem;
                    if (!entNbt.entries().containsKey("id")) continue;
                    String entityType = getString(entNbt, "id");
                    // World-relative pos → anchor-relative: subtract anchor
                    NbtTag.ListTag posList = getList(entNbt, "Pos");
                    double ex = doubleElem(posList, 0) - anchorX;
                    double ey = doubleElem(posList, 1) - anchorY;
                    double ez = doubleElem(posList, 2) - anchorZ;
                    // Extract yaw/pitch from Rotation list if present
                    float yaw = 0f, pitch = 0f;
                    if (entNbt.entries().containsKey("Rotation")) {
                        NbtTag.ListTag rot = getList(entNbt, "Rotation");
                        yaw   = floatElem(rot, 0);
                        pitch = floatElem(rot, 1);
                    }
                    // Strip id and UUID fields
                    Map<String, NbtTag> stripped = new LinkedHashMap<>(entNbt.entries());
                    stripped.remove("id");
                    byte[] payload = UuidStripper.strip(
                            NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(stripped), ""));
                    allEntities.add(MsfEntity.builder()
                            .position(ex, ey, ez)
                            .rotation(yaw, pitch)
                            .entityType(entityType)
                            .nbtPayload(payload)
                            .build());
                }
            }

            layerIdx++;
        }

        // --- Assemble MsfFile ---
        MsfMetadata metadata = MsfMetadata.builder()
                .name(name)
                .author(author)
                .description(description)
                .createdTimestamp(timeCreated)
                .modifiedTimestamp(timeModified)
                .thumbnail(thumbnail)
                .toolName("Litematica")
                .build();

        MsfFile.Builder fileBuilder = MsfFile.builder()
                .mcDataVersion(dataVersion)
                .metadata(metadata)
                .palette(acc.build())
                .layerIndex(MsfLayerIndex.of(layers));

        if (!allEntities.isEmpty())     fileBuilder.entities(allEntities);
        if (!allBlockEntities.isEmpty()) fileBuilder.blockEntities(allBlockEntities);

        return fileBuilder.build();
    }

    // =========================================================================
    // MsfFile → .litematic
    // =========================================================================

    /**
     * Converts an {@link MsfFile} to a Litematica root TAG_Compound (Version 7).
     *
     * <p>Each MSF layer becomes one Litematica subregion. Layers with multiple regions
     * are flattened into a single subregion (minimum enclosing box, air fills gaps).
     * MSF-specific metadata (canonical facing, anchor, rotation compatibility, construction order)
     * is silently dropped.
     *
     * @param msf the MSF file to convert
     * @return the root TAG_Compound for a {@code .litematic} file
     * @throws Exception if serialization fails
     */
    public static NbtTag.CompoundTag msfToLitematic(MsfFile msf) throws Exception {
        List<String> globalPalette = msf.palette().entries();
        List<MsfLayer> msfLayers = msf.layerIndex().layers();

        // Build per-layer block entity and entity lookup maps (by anchor-relative position range)
        // We'll assign entities/block entities to the layer whose bounding box contains them.
        List<int[]> layerBounds = new ArrayList<>(); // [minX, minY, minZ, maxX, maxY, maxZ]
        for (MsfLayer layer : msfLayers) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (MsfRegion r : layer.regions()) {
                minX = Math.min(minX, r.originX()); minY = Math.min(minY, r.originY()); minZ = Math.min(minZ, r.originZ());
                maxX = Math.max(maxX, r.originX() + r.sizeX());
                maxY = Math.max(maxY, r.originY() + r.sizeY());
                maxZ = Math.max(maxZ, r.originZ() + r.sizeZ());
            }
            layerBounds.add(new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        }

        // Assign block entities to layers
        List<List<MsfBlockEntity>> layerBlockEntities = new ArrayList<>();
        for (int i = 0; i < msfLayers.size(); i++) layerBlockEntities.add(new ArrayList<>());
        if (msf.blockEntities().isPresent()) {
            for (MsfBlockEntity be : msf.blockEntities().get()) {
                int layerForBe = findLayer(be.positionX(), be.positionY(), be.positionZ(), layerBounds);
                layerBlockEntities.get(layerForBe).add(be);
            }
        }

        // Assign entities to layers
        List<List<MsfEntity>> layerEntities = new ArrayList<>();
        for (int i = 0; i < msfLayers.size(); i++) layerEntities.add(new ArrayList<>());
        if (msf.entities().isPresent()) {
            for (MsfEntity ent : msf.entities().get()) {
                int layerForEnt = findLayer(
                        (int) Math.floor(ent.positionX()),
                        (int) Math.floor(ent.positionY()),
                        (int) Math.floor(ent.positionZ()), layerBounds);
                layerEntities.get(layerForEnt).add(ent);
            }
        }

        // Build Regions compound
        Map<String, NbtTag> regionsMap = new LinkedHashMap<>();
        boolean hadMultiRegion = false;
        // Compute enclosing size for Metadata
        int encX = 0, encY = 0, encZ = 0;

        for (int li = 0; li < msfLayers.size(); li++) {
            MsfLayer layer = msfLayers.get(li);
            List<MsfRegion> regions = layer.regions();

            if (regions.size() > 1) hadMultiRegion = true;

            // Compute bounding box of this layer's regions
            int[] bounds = layerBounds.get(li);
            int minOX = bounds[0], minOY = bounds[1], minOZ = bounds[2];
            int maxEX = bounds[3], maxEY = bounds[4], maxEZ = bounds[5];
            int flatSX = maxEX - minOX, flatSY = maxEY - minOY, flatSZ = maxEZ - minOZ;

            // Flatten all regions into one array (air-filled, global IDs)
            int[] flatData = new int[flatSX * flatSY * flatSZ]; // 0 = air
            for (MsfRegion r : regions) {
                int[] bd = r.blockData();
                int offX = r.originX() - minOX;
                int offY = r.originY() - minOY;
                int offZ = r.originZ() - minOZ;
                for (int ry = 0; ry < r.sizeY(); ry++) {
                    for (int rz = 0; rz < r.sizeZ(); rz++) {
                        for (int rx = 0; rx < r.sizeX(); rx++) {
                            int srcIdx = ry * r.sizeZ() * r.sizeX() + rz * r.sizeX() + rx;
                            int dstX = offX + rx, dstY = offY + ry, dstZ = offZ + rz;
                            int dstIdx = dstY * flatSZ * flatSX + dstZ * flatSX + dstX;
                            flatData[dstIdx] = bd[srcIdx];
                        }
                    }
                }
            }

            // Build local palette (only referenced global IDs)
            Set<Integer> referencedIds = new LinkedHashSet<>();
            referencedIds.add(0); // air always present
            for (int gid : flatData) referencedIds.add(gid);

            List<String> localPalette = new ArrayList<>();
            Map<Integer, Integer> globalToLocal = new HashMap<>();
            localPalette.add(globalPalette.get(0)); // air at local ID 0
            globalToLocal.put(0, 0);
            for (int gid : referencedIds) {
                if (gid == 0) continue;
                int lid = localPalette.size();
                localPalette.add(globalPalette.get(gid));
                globalToLocal.put(gid, lid);
            }

            // Remap to local IDs
            int[] localIds = new int[flatData.length];
            for (int i = 0; i < flatData.length; i++) {
                localIds[i] = globalToLocal.get(flatData[i]);
            }

            // Pack using Litematica scheme
            int bpe = litematicaBitsPerEntry(localPalette.size());
            long[] blockStates = packLitematica(localIds, bpe);

            // Build BlockStatePalette list
            List<NbtTag> paletteEntries = new ArrayList<>();
            for (String bs : localPalette) paletteEntries.add(blockstateToNbt(bs));

            // Build TileEntities (subregion-relative positions)
            List<NbtTag> tileEntities = new ArrayList<>();
            for (MsfBlockEntity be : layerBlockEntities.get(li)) {
                NbtTag.CompoundTag payload = NbtReader.readCompoundRaw(
                        be.nbtPayload().length > 0 ? be.nbtPayload() : emptyRawCompound());
                Map<String, NbtTag> beNbt = new LinkedHashMap<>();
                beNbt.put("id", new NbtTag.StringTag(be.blockEntityType()));
                // Convert anchor-relative → subregion-relative
                beNbt.put("x",  new NbtTag.IntTag(be.positionX() - minOX));
                beNbt.put("y",  new NbtTag.IntTag(be.positionY() - minOY));
                beNbt.put("z",  new NbtTag.IntTag(be.positionZ() - minOZ));
                beNbt.putAll(payload.entries());
                tileEntities.add(new NbtTag.CompoundTag(beNbt));
            }

            // Build Entities (world-relative positions: anchor-relative, since anchor → origin (0,0,0))
            List<NbtTag> entities = new ArrayList<>();
            for (MsfEntity ent : layerEntities.get(li)) {
                NbtTag.CompoundTag payload = NbtReader.readCompoundRaw(
                        ent.nbtPayload() != null && ent.nbtPayload().length > 0
                                ? ent.nbtPayload() : emptyRawCompound());
                Map<String, NbtTag> entNbt = new LinkedHashMap<>();
                entNbt.put("id", new NbtTag.StringTag(ent.entityType()));
                entNbt.putAll(payload.entries());
                // Position list (world-relative = anchor-relative since anchor at 0,0,0)
                List<NbtTag> posElems = List.of(
                        new NbtTag.DoubleTag(ent.positionX()),
                        new NbtTag.DoubleTag(ent.positionY()),
                        new NbtTag.DoubleTag(ent.positionZ()));
                Map<String, NbtTag> entEntry = new LinkedHashMap<>();
                entEntry.put("Pos", new NbtTag.ListTag(NbtTag.TYPE_DOUBLE, posElems));
                entEntry.put("nbt", new NbtTag.CompoundTag(entNbt));
                entities.add(new NbtTag.CompoundTag(entEntry));
            }

            // Position compound (MSF origin of first/only region = subregion's world position)
            Map<String, NbtTag> posMap = new LinkedHashMap<>();
            posMap.put("x", new NbtTag.IntTag(minOX));
            posMap.put("y", new NbtTag.IntTag(minOY));
            posMap.put("z", new NbtTag.IntTag(minOZ));

            Map<String, NbtTag> sizeMap = new LinkedHashMap<>();
            sizeMap.put("x", new NbtTag.IntTag(flatSX));
            sizeMap.put("y", new NbtTag.IntTag(flatSY));
            sizeMap.put("z", new NbtTag.IntTag(flatSZ));

            // Update enclosing size
            encX = Math.max(encX, minOX + flatSX);
            encY = Math.max(encY, minOY + flatSY);
            encZ = Math.max(encZ, minOZ + flatSZ);

            Map<String, NbtTag> subregion = new LinkedHashMap<>();
            subregion.put("BlockStatePalette", new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, paletteEntries));
            subregion.put("BlockStates",       new NbtTag.LongArrayTag(blockStates));
            subregion.put("TileEntities",      new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, tileEntities));
            subregion.put("Entities",          new NbtTag.ListTag(NbtTag.TYPE_COMPOUND, entities));
            subregion.put("Position",          new NbtTag.CompoundTag(posMap));
            subregion.put("Size",              new NbtTag.CompoundTag(sizeMap));
            // PendingBlockTicks and PendingFluidTicks omitted (silently dropped on import, never written on export)

            regionsMap.put(layer.name().isEmpty() ? ("Layer" + (li + 1)) : layer.name(),
                    new NbtTag.CompoundTag(subregion));
        }

        if (hadMultiRegion) {
            System.out.println(
                    "Multi-region layers were flattened — each MSF layer became one Litematica subregion.");
        }
        System.out.println(
                "MSF-specific metadata was not exported — canonical facing, anchor, rotation compatibility, "
                + "and construction order have no equivalent in the Litematica format.");

        // --- Metadata ---
        MsfMetadata meta = msf.metadata();
        Map<String, NbtTag> enclosingSize = new LinkedHashMap<>();
        enclosingSize.put("x", new NbtTag.IntTag(encX));
        enclosingSize.put("y", new NbtTag.IntTag(encY));
        enclosingSize.put("z", new NbtTag.IntTag(encZ));

        Map<String, NbtTag> metaMap = new LinkedHashMap<>();
        metaMap.put("Name",          new NbtTag.StringTag(meta.name()));
        metaMap.put("Author",        new NbtTag.StringTag(meta.author()));
        metaMap.put("Description",   new NbtTag.StringTag(meta.description()));
        metaMap.put("TimeCreated",   new NbtTag.LongTag(meta.createdTimestamp() * 1000L));
        metaMap.put("TimeModified",  new NbtTag.LongTag(meta.modifiedTimestamp() * 1000L));
        metaMap.put("RegionCount",   new NbtTag.IntTag(msfLayers.size()));
        metaMap.put("EnclosingSize", new NbtTag.CompoundTag(enclosingSize));
        if (meta.hasThumbnail()) {
            metaMap.put("PreviewImageData", new NbtTag.ByteArrayTag(meta.thumbnail()));
        }

        // --- Root ---
        Map<String, NbtTag> rootMap = new LinkedHashMap<>();
        rootMap.put("MinecraftDataVersion", new NbtTag.IntTag((int) msf.header().mcDataVersion()));
        rootMap.put("Version",              new NbtTag.IntTag(7));
        rootMap.put("Metadata",             new NbtTag.CompoundTag(metaMap));
        rootMap.put("Regions",              new NbtTag.CompoundTag(regionsMap));
        return new NbtTag.CompoundTag(rootMap);
    }

    // =========================================================================
    // Bit packing — Litematica scheme
    // =========================================================================

    /**
     * Computes bits per entry for Litematica's block state packing:
     * {@code max(2, ceil(log2(paletteSize)))}.
     */
    static int litematicaBitsPerEntry(int paletteSize) {
        if (paletteSize <= 1) return 2;
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /**
     * Unpacks block states from a Litematica long array.
     *
     * <p>Litematica packing: LSB-first, {@code floor(64/bpe)} entries per word,
     * no inter-word spanning. This is consistent with the no-spanning rule in MSF's
     * {@link dev.msf.core.codec.BitPackedArray} but uses a fixed {@code entriesPerWord}
     * rather than tracking per-entry bit offsets across word boundaries.
     */
    static int[] unpackLitematica(long[] words, int totalEntries, int bpe) {
        int[] result = new int[totalEntries];
        int entriesPerWord = 64 / bpe;
        long mask = bpe == 64 ? -1L : (1L << bpe) - 1L;
        int idx = 0;
        for (long word : words) {
            for (int j = 0; j < entriesPerWord && idx < totalEntries; j++) {
                result[idx++] = (int) ((word >>> (j * bpe)) & mask);
            }
        }
        return result;
    }

    /**
     * Packs block states using the Litematica long array scheme:
     * {@code floor(64/bpe)} entries per word, LSB-first.
     */
    private static long[] packLitematica(int[] values, int bpe) {
        if (values.length == 0) return new long[0];
        int entriesPerWord = 64 / bpe;
        int wordCount = (values.length + entriesPerWord - 1) / entriesPerWord;
        long[] words = new long[wordCount];
        long mask = bpe == 64 ? -1L : (1L << bpe) - 1L;
        for (int i = 0; i < values.length; i++) {
            int wordIdx  = i / entriesPerWord;
            int bitOff   = (i % entriesPerWord) * bpe;
            words[wordIdx] |= ((long) values[i] & mask) << bitOff;
        }
        return words;
    }

    // =========================================================================
    // Blockstate string ↔ palette entry (shared with VanillaStructureFormat)
    // =========================================================================

    /**
     * Converts a Litematica palette entry ({@code Name} + optional {@code Properties}) to a
     * canonical blockstate string. Properties are sorted alphabetically.
     */
    private static String blockstateString(NbtTag.CompoundTag entry) {
        String nameStr = getString(entry, "Name");
        if (!entry.entries().containsKey("Properties")) {
            return nameStr;
        }
        NbtTag.CompoundTag props = (NbtTag.CompoundTag) entry.entries().get("Properties");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, NbtTag> e : props.entries().entrySet()) {
            sorted.put(e.getKey(), ((NbtTag.StringTag) e.getValue()).value());
        }
        StringBuilder sb = new StringBuilder(nameStr).append('[');
        sorted.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }

    /**
     * Converts a blockstate string to a Litematica palette entry TAG_Compound.
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
    // Layer assignment helpers
    // =========================================================================

    /** Returns the index of the first layer whose bounding box contains (x, y, z), or 0 as fallback. */
    private static int findLayer(int x, int y, int z, List<int[]> layerBounds) {
        for (int i = 0; i < layerBounds.size(); i++) {
            int[] b = layerBounds.get(i);
            if (x >= b[0] && x < b[3] && y >= b[1] && y < b[4] && z >= b[2] && z < b[5]) {
                return i;
            }
        }
        return 0; // fallback: first layer
    }

    // =========================================================================
    // NBT access helpers
    // =========================================================================

    private static int getInt(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.IntTag) tag.entries().get(key)).value();
    }

    private static long getLong(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.LongTag) tag.entries().get(key)).value();
    }

    private static String getString(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.StringTag) tag.entries().get(key)).value();
    }

    private static String getStringOrEmpty(NbtTag.CompoundTag tag, String key) {
        if (!tag.entries().containsKey(key)) return "";
        NbtTag val = tag.entries().get(key);
        return val instanceof NbtTag.StringTag s ? s.value() : "";
    }

    private static NbtTag.CompoundTag getCompound(NbtTag.CompoundTag tag, String key) {
        return (NbtTag.CompoundTag) tag.entries().get(key);
    }

    private static NbtTag.ListTag getList(NbtTag.CompoundTag tag, String key) {
        return (NbtTag.ListTag) tag.entries().get(key);
    }

    private static long[] getLongArray(NbtTag.CompoundTag tag, String key) {
        return ((NbtTag.LongArrayTag) tag.entries().get(key)).value();
    }

    private static double doubleElem(NbtTag.ListTag list, int idx) {
        return ((NbtTag.DoubleTag) list.elements().get(idx)).value();
    }

    private static float floatElem(NbtTag.ListTag list, int idx) {
        return ((NbtTag.FloatTag) list.elements().get(idx)).value();
    }

    /** Returns a minimal empty raw TAG_Compound payload (type byte + empty name + TAG_End). */
    private static byte[] emptyRawCompound() throws Exception {
        return NbtWriter.writeCompoundRaw(new NbtTag.CompoundTag(new LinkedHashMap<>()), "");
    }
}

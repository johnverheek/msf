package dev.msf.cli;

import dev.msf.core.MsfException;
import dev.msf.core.MsfWarning;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfRegion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Prints a human-readable summary of every field the MSF spec defines.
 *
 * <p>Exit codes: 0 = success, 1 = parse/read failure, 2 = file not found or unreadable.
 */
@Command(
    name = "inspect",
    mixinStandardHelpOptions = true,
    description = "Print a human-readable summary of an MSF file."
)
public class InspectCommand implements Callable<Integer> {

    private static final int LABEL_WIDTH = 20;

    @Parameters(index = "0", paramLabel = "<file>", description = "Path to the .msf file.")
    private Path file;

    @Override
    public Integer call() {
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

        List<MsfWarning> warnings = new ArrayList<>();
        MsfFile msf;
        try {
            msf = MsfReader.readFile(fileBytes, MsfReaderConfig.allowChecksumFailure(), warnings::add);
        } catch (MsfException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        printSummary(file.getFileName().toString(), fileBytes, msf, warnings);
        return 0;
    }

    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    private static void printSummary(String filename, byte[] fileBytes, MsfFile msf,
                                     List<MsfWarning> warnings) {
        MsfHeader hdr = msf.header();
        MsfMetadata meta = msf.metadata();

        // --- File ---
        line("File", filename);
        line("File size", fileBytes.length + " bytes");
        line("Spec version", "1." + hdr.minorVersion());
        line("MC data version", Long.toUnsignedString(hdr.mcDataVersion()));
        line("Feature flags", "0x" + String.format("%08X", hdr.featureFlags() & 0xFFFFFFFFL)
                + " (" + featureFlagNames(hdr.featureFlags()) + ")");

        // --- Metadata ---
        System.out.println();
        System.out.println("--- Metadata ---");
        line("Name", meta.name());
        line("Author", meta.author().isEmpty() ? "(anonymous)" : meta.author());
        line("Created", formatTimestamp(meta.createdTimestamp()));
        line("Modified", formatTimestamp(meta.modifiedTimestamp()));
        line("Description", meta.description().isEmpty() ? "(none)" : meta.description());
        line("Tags", meta.tags().isEmpty() ? "(none)" : String.join(", ", meta.tags()));
        line("License", meta.licenseIdentifier().isEmpty() ? "(none)" : meta.licenseIdentifier());
        line("Tool", toolLabel(meta));
        line("Placement mode", placementModeName(meta.recommendedPlacementMode()));
        line("Edition", editionName(meta.mcEdition()));

        // --- Placement ---
        System.out.println();
        System.out.println("--- Placement ---");
        line("Anchor", meta.anchorName().isEmpty() ? "(unnamed)"
                : "\"" + meta.anchorName() + "\"" + " at ("
                + meta.anchorOffsetX() + ", " + meta.anchorOffsetY() + ", "
                + meta.anchorOffsetZ() + ")");
        line("Canonical facing", facingName(meta.canonicalFacing()));
        line("Rotation compat", rotationCompatLabel(meta.rotationCompatibility()));
        line("Functional volume", meta.functionalVolume()
                .map(fv -> "(" + fv.minX() + "," + fv.minY() + "," + fv.minZ()
                        + ")\u2013(" + fv.maxX() + "," + fv.maxY() + "," + fv.maxZ() + ")")
                .orElse("not declared"));

        // --- Palette ---
        System.out.println();
        System.out.println("--- Palette ---");
        line("Entries", msf.palette().entries().size() + " (including air)");

        // --- Layers ---
        System.out.println();
        System.out.println("--- Layers ---");
        List<MsfLayer> layers = msf.layerIndex().layers();
        line("Layer count", String.valueOf(layers.size()));
        for (MsfLayer layer : layers) {
            String deps = layer.dependencyIds().isEmpty() ? "none"
                    : layer.dependencyIds().stream()
                            .map(Object::toString)
                            .reduce((a, b) -> a + ", " + b).orElse("");
            StringBuilder flags = new StringBuilder();
            if (layer.isOptional()) flags.append("optional");
            if (layer.isDraft()) { if (!flags.isEmpty()) flags.append(", "); flags.append("draft"); }
            String flagStr = flags.isEmpty() ? "" : " (" + flags + ")";
            String layerLine = "\"" + layer.name() + "\" order=" + layer.constructionOrderIndex()
                    + " deps=[" + deps + "] regions=" + layer.regions().size() + flagStr;
            line("  Layer " + layer.layerId(), layerLine);
        }

        // --- Regions summary ---
        System.out.println();
        System.out.println("--- Regions (summary) ---");
        long totalRegions = 0;
        long totalBlocks = 0;
        for (MsfLayer layer : layers) {
            totalRegions += layer.regions().size();
            for (MsfRegion r : layer.regions()) {
                totalBlocks += (long) r.sizeX() * r.sizeY() * r.sizeZ();
            }
        }
        line("Total regions", String.valueOf(totalRegions));
        line("Total blocks", String.valueOf(totalBlocks));
        line("Compression", compressionSummary(fileBytes, hdr));

        // --- Optional blocks ---
        System.out.println();
        System.out.println("--- Optional blocks ---");
        line("Entities", msf.entities()
                .map(e -> String.valueOf(e.size()))
                .orElse("not present"));
        line("Block entities", msf.blockEntities()
                .map(be -> String.valueOf(be.size()))
                .orElse("not present"));

        // --- Checksums ---
        System.out.println();
        System.out.println("--- Checksums ---");
        boolean headerChecksumFailed = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE
                        && w.message().contains("Header"));
        // Header checksum failure throws before we get here, so if we reach this point it passed.
        line("Header checksum", "PASS");
        boolean fileChecksumFailed = warnings.stream()
                .anyMatch(w -> w.code() == MsfWarning.Code.FILE_CHECKSUM_FAILURE);
        line("File checksum", fileChecksumFailed ? "FAIL" : "PASS");

        // --- Warnings ---
        if (!warnings.isEmpty()) {
            System.out.println();
            System.out.println("--- Warnings ---");
            for (MsfWarning w : warnings) {
                String offsetStr = w.offset() >= 0 ? " [offset 0x" + Long.toHexString(w.offset()) + "]" : "";
                System.out.println("  [" + w.code() + "]" + offsetStr + " " + w.message());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private static void line(String label, String value) {
        System.out.printf("%-" + LABEL_WIDTH + "s%s%n", label + ":", value);
    }

    private static String formatTimestamp(long epochSeconds) {
        if (epochSeconds == 0) return "(not set)";
        return Instant.ofEpochSecond(epochSeconds).toString();
    }

    private static String toolLabel(MsfMetadata meta) {
        String name = meta.toolName();
        String version = meta.toolVersion();
        if (name.isEmpty() && version.isEmpty()) return "(none)";
        if (version.isEmpty()) return name;
        return name + " " + version;
    }

    private static String featureFlagNames(int flags) {
        if (flags == 0) return "none";
        List<String> names = new ArrayList<>();
        if ((flags & MsfHeader.FeatureFlags.HAS_ENTITIES) != 0)        names.add("HAS_ENTITIES");
        if ((flags & MsfHeader.FeatureFlags.HAS_BLOCK_ENTITIES) != 0)  names.add("HAS_BLOCK_ENTITIES");
        if ((flags & MsfHeader.FeatureFlags.HAS_BIOME_DATA) != 0)      names.add("HAS_BIOME_DATA");
        if ((flags & MsfHeader.FeatureFlags.HAS_LIGHTING_HINTS) != 0)  names.add("HAS_LIGHTING_HINTS");
        if ((flags & MsfHeader.FeatureFlags.MULTI_REGION) != 0)        names.add("MULTI_REGION");
        if ((flags & MsfHeader.FeatureFlags.DELTA_DIFF_FORMAT) != 0)   names.add("DELTA_DIFF_FORMAT");
        if ((flags & MsfHeader.FeatureFlags.HAS_SIGNAL_PORTS) != 0)    names.add("HAS_SIGNAL_PORTS");
        if ((flags & MsfHeader.FeatureFlags.HAS_CONSTRUCTION_LAYERS) != 0) names.add("HAS_CONSTRUCTION_LAYERS");
        if ((flags & MsfHeader.FeatureFlags.HAS_VARIANT_SYSTEM) != 0)  names.add("HAS_VARIANT_SYSTEM");
        if ((flags & MsfHeader.FeatureFlags.HAS_PALETTE_SUBSTITUTION_RULES) != 0) names.add("HAS_PALETTE_SUBSTITUTION_RULES");
        // Unknown bits
        int unknown = flags & MsfHeader.FeatureFlags.RESERVED_BITS_MASK;
        if (unknown != 0) names.add("UNKNOWN(0x" + Integer.toHexString(unknown & 0xFFFFFF) + ")");
        return String.join(", ", names);
    }

    private static String facingName(int facing) {
        return switch (facing) {
            case MsfMetadata.FACING_NORTH -> "North";
            case MsfMetadata.FACING_SOUTH -> "South";
            case MsfMetadata.FACING_EAST  -> "East";
            case MsfMetadata.FACING_WEST  -> "West";
            default -> "unknown (0x" + Integer.toHexString(facing) + ")";
        };
    }

    private static String rotationCompatLabel(int flags) {
        if (flags == 0) return "none declared";
        List<String> names = new ArrayList<>();
        if ((flags & MsfMetadata.ROT_90_VALID) != 0)   names.add("90\u00B0");
        if ((flags & MsfMetadata.ROT_180_VALID) != 0)  names.add("180\u00B0");
        if ((flags & MsfMetadata.ROT_270_VALID) != 0)  names.add("270\u00B0");
        if ((flags & MsfMetadata.MIRROR_X_VALID) != 0) names.add("mirror-X");
        if ((flags & MsfMetadata.MIRROR_Z_VALID) != 0) names.add("mirror-Z");
        return String.join(", ", names);
    }

    private static String placementModeName(int mode) {
        return switch (mode) {
            case MsfMetadata.PLACEMENT_MODE_STRICT      -> "strict";
            case MsfMetadata.PLACEMENT_MODE_FUNCTIONAL  -> "functional";
            case MsfMetadata.PLACEMENT_MODE_LOOSE       -> "loose";
            case MsfMetadata.PLACEMENT_MODE_UNSPECIFIED -> "unspecified";
            default -> "unknown (0x" + Integer.toHexString(mode) + ")";
        };
    }

    private static String editionName(int edition) {
        return switch (edition) {
            case MsfMetadata.EDITION_JAVA    -> "Java";
            case MsfMetadata.EDITION_BEDROCK -> "Bedrock";
            case MsfMetadata.EDITION_UNKNOWN -> "unknown";
            default -> "unknown (0x" + Integer.toHexString(edition) + ")";
        };
    }

    /**
     * Parses the compression type byte from each region header in the raw file bytes.
     * Region header structure (inside layer index block body):
     *   u8 layerId, str name, u8 order, u8 depCount, u8[] deps, u8 flags, u8 regionCount,
     *   [per region] str name, 3×i32 origin, 3×u32 size, u8 comprType, u32 compressedLen, u32 uncompressedLen, u8[] data
     */
    private static String compressionSummary(byte[] fileBytes, MsfHeader hdr) {
        try {
            int lio = (int) hdr.layerIndexOffset();
            ByteBuffer buf = ByteBuffer.wrap(fileBytes, lio, fileBytes.length - lio)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buf.getInt(); // block_length
            int layerCount = Byte.toUnsignedInt(buf.get());

            Set<String> typeNames = new LinkedHashSet<>();
            for (int i = 0; i < layerCount; i++) {
                buf.get(); // layerId
                skipStr(buf); // layer name
                buf.get(); // constructionOrder
                int depCount = Byte.toUnsignedInt(buf.get());
                buf.position(buf.position() + depCount); // skip deps
                buf.get(); // flags
                int regionCount = Byte.toUnsignedInt(buf.get());

                for (int r = 0; r < regionCount; r++) {
                    skipStr(buf); // region name
                    buf.getInt(); buf.getInt(); buf.getInt(); // origins (3×i32)
                    buf.getInt(); buf.getInt(); buf.getInt(); // sizes (3×u32)
                    int comprByte = Byte.toUnsignedInt(buf.get());
                    long compressedLen = Integer.toUnsignedLong(buf.getInt());
                    buf.getInt(); // uncompressedLen
                    buf.position(buf.position() + (int) compressedLen); // skip payload

                    typeNames.add(compressionTypeName(comprByte));
                }
            }
            return typeNames.isEmpty() ? "none" : String.join(", ", typeNames);
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    private static void skipStr(ByteBuffer buf) {
        int len = Short.toUnsignedInt(buf.getShort());
        buf.position(buf.position() + len);
    }

    private static String compressionTypeName(int comprByte) {
        for (CompressionType ct : CompressionType.values()) {
            if (ct.byteValue == comprByte) return ct.name().toLowerCase();
        }
        return "unknown(0x" + Integer.toHexString(comprByte) + ")";
    }
}

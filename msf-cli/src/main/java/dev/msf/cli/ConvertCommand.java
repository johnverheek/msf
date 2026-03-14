package dev.msf.cli;

import dev.msf.cli.convert.LitematicaFormat;
import dev.msf.cli.convert.NbtReader;
import dev.msf.cli.convert.NbtTag;
import dev.msf.cli.convert.NbtWriter;
import dev.msf.cli.convert.VanillaStructureFormat;
import dev.msf.core.MsfException;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.model.MsfMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Converts between vanilla Minecraft {@code .nbt} structure files, Litematica
 * {@code .litematic} files, and MSF.
 *
 * <p>The conversion direction is inferred from the file extensions of the input and output paths:
 * <ul>
 *   <li>{@code .nbt} → {@code .msf}: reads and decompresses the vanilla structure NBT,
 *       then writes an MSF file.</li>
 *   <li>{@code .msf} → {@code .nbt}: reads the MSF file, then writes a gzip-compressed
 *       vanilla structure NBT file.</li>
 *   <li>{@code .litematic} → {@code .msf}: reads and decompresses the Litematica NBT,
 *       then writes an MSF file.</li>
 *   <li>{@code .msf} → {@code .litematic}: reads the MSF file, then writes a gzip-compressed
 *       Litematica v7 file.</li>
 *   <li>{@code .litematic} → {@code .nbt} and {@code .nbt} → {@code .litematic}: routed
 *       through MSF as an intermediate (no direct format-to-format path).</li>
 * </ul>
 *
 * <p>Exit codes: 0 = success, 1 = conversion/parse failure, 2 = file not found or bad arguments.
 */
@Command(
    name = "convert",
    mixinStandardHelpOptions = true,
    description = "Convert between .nbt, .litematic, and .msf files."
)
public class ConvertCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<input>",  description = "Input file (.nbt, .litematic, or .msf).")
    private Path input;

    @Parameters(index = "1", paramLabel = "<output>", description = "Output file (.msf, .nbt, or .litematic).")
    private Path output;

    // Story C1.1: compressor and compression-level flags
    @Option(
        names = "--compressor",
        paramLabel = "zstd|lz4|brotli|none",
        description = "Compression algorithm for .msf output (default: zstd). Per spec §7.2, zstd is RECOMMENDED.",
        defaultValue = "zstd"
    )
    private String compressor;

    @Option(
        names = "--compression-level",
        paramLabel = "<int>",
        description = "Compression level for zstd (1–22; default: 3 per spec §7.2). Ignored for other compressors.",
        defaultValue = "3"
    )
    private int compressionLevel;

    // Story C1.2: entities toggle (arity=1 so --entities false works, not just a flag)
    @Option(
        names = "--entities",
        paramLabel = "true|false",
        description = "Include entities and block entities in .msf output (default: true).",
        defaultValue = "true",
        arity = "1"
    )
    private boolean includeEntities;

    // Story C1.3: metadata override
    @Option(
        names = "--name",
        paramLabel = "<str>",
        description = "Override the schematic name in .msf metadata."
    )
    private String nameOverride;

    @Option(
        names = "--author",
        paramLabel = "<str>",
        description = "Override the author field in .msf metadata."
    )
    private String authorOverride;

    @Override
    public Integer call() {
        if (!Files.exists(input)) {
            System.err.println("Error: file not found: " + input);
            return 2;
        }

        // Validate compressor name early (Story C1.1)
        CompressionType compressionType;
        try {
            compressionType = parseCompressor(compressor);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }

        // Validate compression level for zstd (Story C1.1)
        if (compressionType == CompressionType.ZSTD
                && (compressionLevel < 1 || compressionLevel > 22)) {
            System.err.println("Error: invalid --compression-level " + compressionLevel
                    + " for zstd (valid range: 1–22)");
            return 2;
        }

        String inExt  = extension(input);
        String outExt = extension(output);

        byte[] inputBytes;
        try {
            inputBytes = Files.readAllBytes(input);
        } catch (IOException e) {
            System.err.println("Error: cannot read file: " + input + " — " + e.getMessage());
            return 2;
        }

        try {
            if (inExt.equals("nbt") && outExt.equals("msf")) {
                NbtTag.CompoundTag nbtRoot = NbtReader.readCompound(inputBytes);
                MsfFile msf = applyOptions(VanillaStructureFormat.nbtToMsf(nbtRoot));
                Files.write(output, MsfWriter.writeFile(msf, compressionType, compressionLevel, null));

            } else if (inExt.equals("msf") && outExt.equals("nbt")) {
                MsfFile msf = MsfReader.readFile(inputBytes, MsfReaderConfig.DEFAULT, null);
                NbtTag.CompoundTag nbtRoot = VanillaStructureFormat.msfToNbt(msf);
                Files.write(output, NbtWriter.writeCompound(nbtRoot, ""));

            } else if (inExt.equals("litematic") && outExt.equals("msf")) {
                NbtTag.CompoundTag litRoot = NbtReader.readCompound(inputBytes);
                MsfFile msf = applyOptions(LitematicaFormat.litematicToMsf(litRoot));
                Files.write(output, MsfWriter.writeFile(msf, compressionType, compressionLevel, null));

            } else if (inExt.equals("msf") && outExt.equals("litematic")) {
                MsfFile msf = MsfReader.readFile(inputBytes, MsfReaderConfig.DEFAULT, null);
                NbtTag.CompoundTag litRoot = LitematicaFormat.msfToLitematic(msf);
                Files.write(output, NbtWriter.writeCompound(litRoot, ""));

            } else if (inExt.equals("litematic") && outExt.equals("nbt")) {
                // Route through MSF as intermediate: .litematic → MsfFile → .nbt
                NbtTag.CompoundTag litRoot = NbtReader.readCompound(inputBytes);
                MsfFile msf = LitematicaFormat.litematicToMsf(litRoot);
                NbtTag.CompoundTag nbtRoot = VanillaStructureFormat.msfToNbt(msf);
                Files.write(output, NbtWriter.writeCompound(nbtRoot, ""));

            } else if (inExt.equals("nbt") && outExt.equals("litematic")) {
                // Route through MSF as intermediate: .nbt → MsfFile → .litematic
                NbtTag.CompoundTag nbtRoot = NbtReader.readCompound(inputBytes);
                MsfFile msf = VanillaStructureFormat.nbtToMsf(nbtRoot);
                NbtTag.CompoundTag litRoot = LitematicaFormat.msfToLitematic(msf);
                Files.write(output, NbtWriter.writeCompound(litRoot, ""));

            } else {
                System.err.println("Error: unsupported conversion: ." + inExt + " → ." + outExt
                        + " (supported: .nbt↔.msf, .litematic↔.msf, .litematic↔.nbt)");
                return 2;
            }

            System.out.println("Converted: " + input.getFileName() + " → " + output.getFileName());
            return 0;

        } catch (MsfException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    // -------------------------------------------------------------------------
    // Option application
    // -------------------------------------------------------------------------

    /**
     * Applies CLI option overrides (entities toggle, name/author) to an MsfFile produced
     * by a converter. Returns either the original or a rebuilt file with options applied.
     */
    private MsfFile applyOptions(MsfFile msf) throws MsfException {
        boolean needsRebuild = !includeEntities || nameOverride != null || authorOverride != null;
        if (!needsRebuild) {
            return msf;
        }

        MsfMetadata meta = msf.metadata();
        MsfMetadata.Builder metaBuilder = MsfMetadata.builder()
            .name(nameOverride != null ? nameOverride : meta.name())
            .author(authorOverride != null ? authorOverride : meta.author())
            .createdTimestamp(meta.createdTimestamp())
            .modifiedTimestamp(meta.modifiedTimestamp())
            .description(meta.description())
            .tags(meta.tags())
            .licenseIdentifier(meta.licenseIdentifier())
            .anchorName(meta.anchorName())
            .anchorOffset(meta.anchorOffsetX(), meta.anchorOffsetY(), meta.anchorOffsetZ())
            .canonicalFacing(meta.canonicalFacing())
            .rotationCompatibility(meta.rotationCompatibility())
            .toolName(meta.toolName())
            .toolVersion(meta.toolVersion())
            .recommendedPlacementMode(meta.recommendedPlacementMode())
            .mcEdition(meta.mcEdition());
        meta.functionalVolume().ifPresent(metaBuilder::functionalVolume);

        MsfFile.Builder fileBuilder = MsfFile.builder()
            .mcDataVersion(msf.header().mcDataVersion())
            .metadata(metaBuilder.build())
            .palette(msf.palette())
            .layerIndex(msf.layerIndex());

        if (includeEntities) {
            msf.entities().ifPresent(fileBuilder::entities);
            msf.blockEntities().ifPresent(fileBuilder::blockEntities);
        }
        // When includeEntities=false, feature flags must not include entity bits.
        // MsfFile.builder() derives feature flags from the presence of entities/blockEntities,
        // so simply omitting them is sufficient.

        return fileBuilder.build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the --compressor option value to a {@link CompressionType}.
     *
     * @throws IllegalArgumentException with a user-friendly message on invalid input
     */
    static CompressionType parseCompressor(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "zstd"   -> CompressionType.ZSTD;
            case "lz4"    -> CompressionType.LZ4;
            case "brotli" -> CompressionType.BROTLI;
            case "none"   -> CompressionType.NONE;
            default -> throw new IllegalArgumentException(
                "invalid --compressor '" + value + "' (valid: zstd, lz4, brotli, none)"
            );
        };
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}

package dev.msf.cli;

import dev.msf.cli.convert.LitematicaFormat;
import dev.msf.cli.convert.NbtReader;
import dev.msf.cli.convert.NbtTag;
import dev.msf.cli.convert.NbtWriter;
import dev.msf.cli.convert.VanillaStructureFormat;
import dev.msf.core.MsfException;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Override
    public Integer call() {
        MsfCli.printHeader();

        if (!Files.exists(input)) {
            System.err.println("Error: file not found: " + input);
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
                MsfFile msf = VanillaStructureFormat.nbtToMsf(nbtRoot);
                Files.write(output, MsfWriter.writeFile(msf, null));

            } else if (inExt.equals("msf") && outExt.equals("nbt")) {
                MsfFile msf = MsfReader.readFile(inputBytes, MsfReaderConfig.DEFAULT, null);
                NbtTag.CompoundTag nbtRoot = VanillaStructureFormat.msfToNbt(msf);
                Files.write(output, NbtWriter.writeCompound(nbtRoot, ""));

            } else if (inExt.equals("litematic") && outExt.equals("msf")) {
                NbtTag.CompoundTag litRoot = NbtReader.readCompound(inputBytes);
                MsfFile msf = LitematicaFormat.litematicToMsf(litRoot);
                Files.write(output, MsfWriter.writeFile(msf, null));

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

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}

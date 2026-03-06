package dev.msf.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.msf.core.MsfException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.world.CanonicalFacing;
import dev.msf.fabric.world.PlacementOptions;
import dev.msf.fabric.world.RegionExtractor;
import dev.msf.fabric.world.RegionPlacer;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers the {@code /msf extract} and {@code /msf place} commands.
 *
 * <h2>Commands</h2>
 * <ul>
 *   <li>{@code /msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>} — extracts the
 *       bounding box {@code (x1,y1,z1)–(x2,y2,z2)} as an MSF schematic and writes it
 *       to {@code msf-schematics/<filename>.msf}. The canonical facing is derived from
 *       the executing player's horizontal facing direction.</li>
 *   <li>{@code /msf place <filename> <x> <y> <z>} — reads
 *       {@code msf-schematics/<filename>.msf} and places it at world position
 *       {@code (x,y,z)} as the anchor. The target facing is the player's current
 *       horizontal facing; the canonical facing is read from the file's metadata.</li>
 * </ul>
 *
 * <h2>Output directory</h2>
 * Schematics are written to and read from a {@code msf-schematics/} directory relative
 * to the JVM working directory. The directory is created on demand.
 */
public final class MsfCommands {

    private static final Path SCHEMATICS_DIR = Path.of("msf-schematics");

    private MsfCommands() {}

    // =========================================================================
    // Command registration
    // =========================================================================

    /**
     * Registers {@code /msf extract} and {@code /msf place} with the given dispatcher.
     *
     * @param dispatcher the server command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("msf")
                .then(buildExtractCommand())
                .then(buildPlaceCommand())
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildExtractCommand() {
        return CommandManager.literal("extract")
            .then(CommandManager.argument("x1", IntegerArgumentType.integer())
            .then(CommandManager.argument("y1", IntegerArgumentType.integer())
            .then(CommandManager.argument("z1", IntegerArgumentType.integer())
            .then(CommandManager.argument("x2", IntegerArgumentType.integer())
            .then(CommandManager.argument("y2", IntegerArgumentType.integer())
            .then(CommandManager.argument("z2", IntegerArgumentType.integer())
            .then(CommandManager.argument("filename", StringArgumentType.word())
                .executes(ctx -> {
                    int x1 = IntegerArgumentType.getInteger(ctx, "x1");
                    int y1 = IntegerArgumentType.getInteger(ctx, "y1");
                    int z1 = IntegerArgumentType.getInteger(ctx, "z1");
                    int x2 = IntegerArgumentType.getInteger(ctx, "x2");
                    int y2 = IntegerArgumentType.getInteger(ctx, "y2");
                    int z2 = IntegerArgumentType.getInteger(ctx, "z2");
                    String filename = StringArgumentType.getString(ctx, "filename");

                    ServerCommandSource source = ctx.getSource();
                    BlockBox bounds = BlockBox.create(
                        new BlockPos(x1, y1, z1),
                        new BlockPos(x2, y2, z2)
                    );
                    Path outputPath = SCHEMATICS_DIR.resolve(filename + ".msf");

                    return executeExtract(
                        source.getWorld(), bounds, facingFromSource(source), outputPath,
                        msg -> source.sendFeedback(() -> msg, false)
                    );
                })
            )))))))
        ;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildPlaceCommand() {
        return CommandManager.literal("place")
            .then(CommandManager.argument("filename", StringArgumentType.word())
            .then(CommandManager.argument("x", IntegerArgumentType.integer())
            .then(CommandManager.argument("y", IntegerArgumentType.integer())
            .then(CommandManager.argument("z", IntegerArgumentType.integer())
                .executes(ctx -> {
                    String filename = StringArgumentType.getString(ctx, "filename");
                    int x = IntegerArgumentType.getInteger(ctx, "x");
                    int y = IntegerArgumentType.getInteger(ctx, "y");
                    int z = IntegerArgumentType.getInteger(ctx, "z");

                    ServerCommandSource source = ctx.getSource();
                    Path inputPath = SCHEMATICS_DIR.resolve(filename + ".msf");

                    return executePlace(
                        source.getWorld(), new BlockPos(x, y, z), facingFromSource(source),
                        inputPath, msg -> source.sendFeedback(() -> msg, false)
                    );
                })
            ))))
        ;
    }

    // =========================================================================
    // Core logic (public for testmod access)
    // =========================================================================

    /**
     * Extracts the given bounding box from {@code world} and writes an MSF file.
     *
     * <p>The anchor is always the minimum corner of {@code bounds}. The region is
     * stored in a single layer named {@code "layer"} with the given {@code facing}
     * as the canonical facing in the metadata.
     *
     * @param world      the source world
     * @param bounds     the region to extract
     * @param facing     canonical facing to embed in the MSF metadata
     * @param outputPath path to write the MSF file; the parent directory is created if absent
     * @param feedback   receives feedback text (success or error messages)
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executeExtract(
        ServerWorld world,
        BlockBox bounds,
        CanonicalFacing facing,
        Path outputPath,
        Consumer<Text> feedback
    ) {
        Path parent = outputPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                feedback.accept(Text.literal("Error creating directory: " + e.getMessage()));
                return 0;
            }
        }

        BlockPos anchor = new BlockPos(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
        List<String> palette = new ArrayList<>();
        MsfRegion region;
        try {
            region = RegionExtractor.extract(world, bounds, anchor, false, palette);
        } catch (MsfPaletteException e) {
            feedback.accept(Text.literal("Error: palette overflow — " + e.getMessage()));
            return 0;
        }

        try {
            MsfFile file = MsfFile.builder()
                .mcDataVersion(SharedConstants.getGameVersion().getSaveVersion().getId())
                .metadata(MsfMetadata.builder()
                    .name(outputPath.getFileName().toString())
                    .canonicalFacing(facing.msfValue())
                    .toolName("msf-fabric")
                    .mcEdition(MsfMetadata.EDITION_JAVA)
                    .build())
                .palette(MsfPalette.of(new ArrayList<>(palette)))
                .layerIndex(MsfLayerIndex.of(List.of(
                    MsfLayer.builder().layerId(1).name("layer").addRegion(region).build()
                )))
                .build();
            Files.write(outputPath, MsfWriter.writeFile(file, null));
        } catch (MsfException | IOException e) {
            feedback.accept(Text.literal("Error writing MSF file: " + e.getMessage()));
            return 0;
        }

        feedback.accept(Text.literal(
            "Extracted " + bounds.getBlockCountX() + "\u00d7"
                + bounds.getBlockCountY() + "\u00d7" + bounds.getBlockCountZ()
                + " region to " + outputPath.getFileName()
        ));
        return 1;
    }

    /**
     * Reads an MSF file and places it in {@code world} at the given anchor.
     *
     * <p>The canonical facing is read from the file's metadata. Blocks are placed with
     * the rotation needed to go from the canonical facing to {@code targetFacing}.
     *
     * @param world        the target world
     * @param anchor       world position to use as the MSF anchor point
     * @param targetFacing the direction the player is facing (determines rotation)
     * @param inputPath    path to read the MSF file from
     * @param feedback     receives feedback text (success or error messages)
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executePlace(
        ServerWorld world,
        BlockPos anchor,
        CanonicalFacing targetFacing,
        Path inputPath,
        Consumer<Text> feedback
    ) {
        if (!Files.exists(inputPath)) {
            feedback.accept(Text.literal("File not found: " + inputPath.getFileName()));
            return 0;
        }

        MsfFile file;
        try {
            byte[] bytes = Files.readAllBytes(inputPath);
            file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
        } catch (MsfException | IOException e) {
            feedback.accept(Text.literal("Error reading MSF file: " + e.getMessage()));
            return 0;
        }

        CanonicalFacing canonicalFacing = CanonicalFacing.fromMsfValue(file.metadata().canonicalFacing());
        PlacementOptions options = new PlacementOptions(false, true, true, canonicalFacing, targetFacing);

        try {
            RegionPlacer.place(file, world, anchor, options);
        } catch (MsfException e) {
            feedback.accept(Text.literal("Error placing MSF file: " + e.getMessage()));
            return 0;
        }

        feedback.accept(Text.literal(
            "Placed " + inputPath.getFileName()
                + " at (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ")"
        ));
        return 1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the {@link CanonicalFacing} corresponding to the player's current
     * horizontal facing direction. Falls back to {@link CanonicalFacing#NORTH} for
     * non-player sources (e.g. command blocks, server console).
     */
    private static CanonicalFacing facingFromSource(ServerCommandSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            Direction dir = player.getHorizontalFacing();
            return switch (dir) {
                case NORTH -> CanonicalFacing.NORTH;
                case SOUTH -> CanonicalFacing.SOUTH;
                case EAST  -> CanonicalFacing.EAST;
                case WEST  -> CanonicalFacing.WEST;
                default    -> CanonicalFacing.NORTH;
            };
        }
        return CanonicalFacing.NORTH;
    }
}

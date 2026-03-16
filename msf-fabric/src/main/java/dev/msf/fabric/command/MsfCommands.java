package dev.msf.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import dev.msf.core.MsfException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
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
 *   <li>{@code /msf extract <pos1> <pos2> <filename>} — extracts the bounding box
 *       {@code pos1–pos2} as an MSF schematic and writes it to
 *       {@code msf-schematics/<filename>.msf}. Positions accept absolute coordinates,
 *       relative ({@code ~}) notation, and local ({@code ^}) notation. The canonical
 *       facing is derived from the executing player's horizontal facing direction.</li>
 *   <li>{@code /msf place <filename> <pos>} — reads
 *       {@code msf-schematics/<filename>.msf} and places it with {@code pos} as the
 *       anchor. Accepts absolute, relative, and local coordinate notation. The target
 *       facing is the player's current horizontal facing; the canonical facing is read
 *       from the file's metadata.</li>
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
            .then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos())
            .then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos())
            .then(CommandManager.argument("filename", StringArgumentType.word())
                .executes(ctx -> {
                    BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx, "pos1");
                    BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx, "pos2");
                    String filename = StringArgumentType.getString(ctx, "filename");

                    ServerCommandSource source = ctx.getSource();
                    BlockBox bounds = BlockBox.create(pos1, pos2);
                    Path outputPath = SCHEMATICS_DIR.resolve(filename + ".msf");

                    return executeExtract(
                        source.getWorld(), bounds, facingFromSource(source), outputPath,
                        msg -> source.sendFeedback(() -> msg, false)
                    );
                })
            )))
        ;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildPlaceCommand() {
        return CommandManager.literal("place")
            .then(CommandManager.argument("filename", StringArgumentType.word())
            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                .executes(ctx -> {
                    String filename = StringArgumentType.getString(ctx, "filename");
                    BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");

                    ServerCommandSource source = ctx.getSource();
                    Path inputPath = SCHEMATICS_DIR.resolve(filename + ".msf");

                    return executePlace(
                        source.getWorld(), pos, facingFromSource(source),
                        inputPath, msg -> source.sendFeedback(() -> msg, false)
                    );
                })
            ))
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

        // Capture entities and block entities within the bounds (Section 8, Section 9)
        List<MsfEntity> entities = RegionExtractor.extractEntities(world, bounds, anchor);
        List<MsfBlockEntity> blockEntities = RegionExtractor.extractBlockEntities(world, bounds, anchor);

        try {
            MsfFile.Builder fileBuilder = MsfFile.builder()
                .mcDataVersion(SharedConstants.getGameVersion().dataVersion().id())
                .metadata(MsfMetadata.builder()
                    .name(outputPath.getFileName().toString())
                    .canonicalFacing(facing.msfValue())
                    .toolName("msf-fabric")
                    .mcEdition(MsfMetadata.EDITION_JAVA)
                    .build())
                .palette(MsfPalette.of(new ArrayList<>(palette)))
                .layerIndex(MsfLayerIndex.of(List.of(
                    MsfLayer.builder().layerId(1).name("layer").addRegion(region).build()
                )));
            // Only set entity/block-entity blocks when content was found;
            // absent blocks leave feature flag bits 0 and 1 clear (spec Section 3.3)
            if (!entities.isEmpty()) {
                fileBuilder.entities(entities);
            }
            if (!blockEntities.isEmpty()) {
                fileBuilder.blockEntities(blockEntities);
            }
            Files.write(outputPath, MsfWriter.writeFile(fileBuilder.build(), null));
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

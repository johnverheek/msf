package dev.msf.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.msf.core.MsfException;
import dev.msf.core.MsfPaletteException;
import dev.msf.core.io.MsfReader;
import dev.msf.core.io.MsfReaderConfig;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.fabric.bridge.BlockEntityBridge;
import dev.msf.fabric.world.CanonicalFacing;
import dev.msf.fabric.world.Mirror;
import dev.msf.fabric.world.PlacementOptions;
import dev.msf.fabric.world.RegionExtractor;
import dev.msf.fabric.world.RegionPlacer;
import net.minecraft.SharedConstants;
import net.minecraft.block.entity.BlockEntity;
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
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Registers {@code /msf} sub-commands: {@code extract}, {@code place}, {@code list},
 * and {@code info}.
 *
 * <h2>Commands</h2>
 * <ul>
 *   <li>{@code /msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename> [entities] [name]}
 *       — extracts the bounding box as an MSF schematic. {@code entities} is 0 or 1 (default 1).
 *       {@code name} overrides the schematic name in metadata.</li>
 *   <li>{@code /msf place <filename> <x> <y> <z> [facing] [mirror] [layer]}
 *       — places a schematic at the given anchor. {@code facing} (0–3) overrides the target
 *       facing derived from the player's direction. {@code mirror} is {@code none|x|z}.
 *       {@code layer} filters to a single named layer.</li>
 *   <li>{@code /msf list [page]} — lists available schematics (8 per page).</li>
 *   <li>{@code /msf info <filename>} — shows schematic metadata in chat.</li>
 * </ul>
 *
 * <h2>Output directory</h2>
 * Schematics are read from and written to {@code msf-schematics/} relative to the
 * JVM working directory, created on demand.
 */
public final class MsfCommands {

    private static final Path SCHEMATICS_DIR = Path.of("msf-schematics");
    public static final String FABRIC_VERSION = "1.1.0+1.21.1";
    private static final int LIST_PAGE_SIZE = 8;

    private MsfCommands() {}

    // =========================================================================
    // Command registration
    // =========================================================================

    /**
     * Registers all {@code /msf} sub-commands with the given dispatcher.
     *
     * @param dispatcher the server command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("msf")
                .then(buildExtractCommand())
                .then(buildPlaceCommand())
                .then(buildListCommand())
                .then(buildInfoCommand())
        );
    }

    // =========================================================================
    // Brigadier command builders
    // =========================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildExtractCommand() {
        return CommandManager.literal("extract")
            .then(CommandManager.argument("x1", IntegerArgumentType.integer())
            .then(CommandManager.argument("y1", IntegerArgumentType.integer())
            .then(CommandManager.argument("z1", IntegerArgumentType.integer())
            .then(CommandManager.argument("x2", IntegerArgumentType.integer())
            .then(CommandManager.argument("y2", IntegerArgumentType.integer())
            .then(CommandManager.argument("z2", IntegerArgumentType.integer())
            .then(CommandManager.argument("filename", StringArgumentType.word())
                // F3.1 — base form: entities=true, name derived from filename
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    BlockBox bounds = boundsFromCtx(ctx);
                    Path out = SCHEMATICS_DIR.resolve(
                        StringArgumentType.getString(ctx, "filename") + ".msf");
                    return executeExtract(src.getWorld(), bounds, facingFromSource(src),
                        out, true, null, msg -> src.sendFeedback(() -> msg, false));
                })
                // F3.1 — optional entities flag (0=skip, 1=include)
                .then(CommandManager.argument("entities", IntegerArgumentType.integer(0, 1))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        BlockBox bounds = boundsFromCtx(ctx);
                        Path out = SCHEMATICS_DIR.resolve(
                            StringArgumentType.getString(ctx, "filename") + ".msf");
                        boolean ents = IntegerArgumentType.getInteger(ctx, "entities") == 1;
                        return executeExtract(src.getWorld(), bounds, facingFromSource(src),
                            out, ents, null, msg -> src.sendFeedback(() -> msg, false));
                    })
                    // F3.1 — optional name override
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            BlockBox bounds = boundsFromCtx(ctx);
                            Path out = SCHEMATICS_DIR.resolve(
                                StringArgumentType.getString(ctx, "filename") + ".msf");
                            boolean ents = IntegerArgumentType.getInteger(ctx, "entities") == 1;
                            String name = StringArgumentType.getString(ctx, "name");
                            return executeExtract(src.getWorld(), bounds, facingFromSource(src),
                                out, ents, name, msg -> src.sendFeedback(() -> msg, false));
                        })
                    )
                )
            )))))))
        ;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlaceCommand() {
        return CommandManager.literal("place")
            .then(CommandManager.argument("filename", StringArgumentType.word())
            .then(CommandManager.argument("x", IntegerArgumentType.integer())
            .then(CommandManager.argument("y", IntegerArgumentType.integer())
            .then(CommandManager.argument("z", IntegerArgumentType.integer())
                // base form: use player facing, no mirror, all layers
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    Path inp = SCHEMATICS_DIR.resolve(
                        StringArgumentType.getString(ctx, "filename") + ".msf");
                    BlockPos anchor = anchorFromCtx(ctx);
                    return executePlace(src.getWorld(), anchor, facingFromSource(src),
                        Mirror.NONE, null, inp, msg -> src.sendFeedback(() -> msg, false));
                })
                // F1.1 — optional facing override (0=North,1=East,2=South,3=West CW ordinal)
                .then(CommandManager.argument("facing", IntegerArgumentType.integer(0, 3))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        Path inp = SCHEMATICS_DIR.resolve(
                            StringArgumentType.getString(ctx, "filename") + ".msf");
                        BlockPos anchor = anchorFromCtx(ctx);
                        CanonicalFacing target = CanonicalFacing.fromCwOrdinal(
                            IntegerArgumentType.getInteger(ctx, "facing"));
                        return executePlace(src.getWorld(), anchor, target,
                            Mirror.NONE, null, inp, msg -> src.sendFeedback(() -> msg, false));
                    })
                    // F1.1 — optional mirror (none|x|z)
                    .then(CommandManager.argument("mirror", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            Path inp = SCHEMATICS_DIR.resolve(
                                StringArgumentType.getString(ctx, "filename") + ".msf");
                            BlockPos anchor = anchorFromCtx(ctx);
                            CanonicalFacing target = CanonicalFacing.fromCwOrdinal(
                                IntegerArgumentType.getInteger(ctx, "facing"));
                            Mirror mirror = parseMirror(
                                StringArgumentType.getString(ctx, "mirror"));
                            return executePlace(src.getWorld(), anchor, target,
                                mirror, null, inp, msg -> src.sendFeedback(() -> msg, false));
                        })
                        // F2.1 — optional layer filter
                        .then(CommandManager.argument("layer", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerCommandSource src = ctx.getSource();
                                Path inp = SCHEMATICS_DIR.resolve(
                                    StringArgumentType.getString(ctx, "filename") + ".msf");
                                BlockPos anchor = anchorFromCtx(ctx);
                                CanonicalFacing target = CanonicalFacing.fromCwOrdinal(
                                    IntegerArgumentType.getInteger(ctx, "facing"));
                                Mirror mirror = parseMirror(
                                    StringArgumentType.getString(ctx, "mirror"));
                                String layer = StringArgumentType.getString(ctx, "layer");
                                return executePlace(src.getWorld(), anchor, target,
                                    mirror, layer, inp, msg -> src.sendFeedback(() -> msg, false));
                            })
                        )
                    )
                )
            ))))
        ;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildListCommand() {
        return CommandManager.literal("list")
            // F4.1 — base form: page 1
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                return executeList(1, msg -> src.sendFeedback(() -> msg, false));
            })
            // F4.1 — optional page number
            .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    int page = IntegerArgumentType.getInteger(ctx, "page");
                    return executeList(page, msg -> src.sendFeedback(() -> msg, false));
                })
            )
        ;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildInfoCommand() {
        return CommandManager.literal("info")
            .then(CommandManager.argument("filename", StringArgumentType.word())
                // F5.1 — show schematic metadata in chat
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    String filename = StringArgumentType.getString(ctx, "filename");
                    Path path = SCHEMATICS_DIR.resolve(filename + ".msf");
                    return executeInfo(path, msg -> src.sendFeedback(() -> msg, false));
                })
            )
        ;
    }

    // =========================================================================
    // Core logic (package-visible for testmod access)
    // =========================================================================

    /**
     * Extracts the given bounding box from {@code world} and writes an MSF file.
     * Backward-compatible overload: entities included, no name override.
     */
    public static int executeExtract(
        ServerWorld world,
        BlockBox bounds,
        CanonicalFacing facing,
        Path outputPath,
        Consumer<Text> feedback
    ) {
        return executeExtract(world, bounds, facing, outputPath, true, null, feedback);
    }

    /**
     * Extracts the given bounding box from {@code world} and writes an MSF file.
     *
     * <p>The anchor is the minimum corner of {@code bounds}. When {@code includeEntities}
     * is {@code true}, block entities within the bounds are included in the output (F3.1).
     * When {@code nameOverride} is non-null it replaces the filename-derived schematic name (F3.1).
     *
     * @param world           the source world
     * @param bounds          the region to extract
     * @param facing          canonical facing to embed in the MSF metadata
     * @param outputPath      path to write the MSF file; the parent directory is created if absent
     * @param includeEntities when {@code true}, block entities in the bounds are included
     * @param nameOverride    when non-null, overrides the schematic name in metadata
     * @param feedback        receives feedback text
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executeExtract(
        ServerWorld world,
        BlockBox bounds,
        CanonicalFacing facing,
        Path outputPath,
        boolean includeEntities,
        String nameOverride,
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

        String schematicName = nameOverride != null
            ? nameOverride
            : outputPath.getFileName().toString();

        try {
            MsfFile.Builder fileBuilder = MsfFile.builder()
                .mcDataVersion(SharedConstants.getGameVersion().getSaveVersion().getId())
                .metadata(MsfMetadata.builder()
                    .name(schematicName)
                    .canonicalFacing(facing.msfValue())
                    .toolName("msf-fabric")
                    .mcEdition(MsfMetadata.EDITION_JAVA)
                    .build())
                .palette(MsfPalette.of(new ArrayList<>(palette)))
                .layerIndex(MsfLayerIndex.of(List.of(
                    MsfLayer.builder().layerId(1).name("layer").addRegion(region).build()
                )));

            // F3.1 — collect block entities within bounds when includeEntities=true
            if (includeEntities) {
                List<MsfBlockEntity> blockEntities = extractBlockEntities(world, bounds, anchor);
                if (!blockEntities.isEmpty()) {
                    fileBuilder.blockEntities(blockEntities);
                }
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
     * Backward-compatible overload: no mirror, no layer filter.
     */
    public static int executePlace(
        ServerWorld world,
        BlockPos anchor,
        CanonicalFacing targetFacing,
        Path inputPath,
        Consumer<Text> feedback
    ) {
        return executePlace(world, anchor, targetFacing, Mirror.NONE, null, inputPath, feedback);
    }

    /**
     * Reads an MSF file and places it in {@code world} at the given anchor.
     *
     * <p>The canonical facing is read from the file's metadata. The rotation needed to go
     * from canonical to {@code targetFacing} is applied (Section 10.2). {@code mirror} is
     * applied after rotation (Section 10.3). When {@code layerFilter} is non-null, only
     * layers whose name equals {@code layerFilter} are placed; block entities are skipped
     * in this mode (F2.1).
     *
     * @param world        the target world
     * @param anchor       world position for the MSF anchor point
     * @param targetFacing the direction to orient the schematic toward (F1.1)
     * @param mirror       mirror axis applied after rotation (F1.1)
     * @param layerFilter  when non-null, only this layer name is placed (F2.1)
     * @param inputPath    path to read the MSF file from
     * @param feedback     receives feedback text
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executePlace(
        ServerWorld world,
        BlockPos anchor,
        CanonicalFacing targetFacing,
        Mirror mirror,
        String layerFilter,
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

        CanonicalFacing canonicalFacing = CanonicalFacing.fromMsfValue(
            file.metadata().canonicalFacing());
        PlacementOptions options = new PlacementOptions(
            false, true, true, canonicalFacing, targetFacing, mirror);

        try {
            if (layerFilter == null) {
                // Full placement including block entities (two-phase)
                RegionPlacer.place(file, world, anchor, options);
            } else {
                // F2.1 — layer-filtered placement: blocks only, skip block entities
                MsfPalette globalPalette = file.palette();
                int matched = 0;
                for (MsfLayer layer : file.layerIndex().layers()) {
                    if (!layerFilter.equals(layer.name())) continue;
                    for (MsfRegion region : layer.regions()) {
                        RegionPlacer.place(region, globalPalette, world, anchor, options);
                        matched++;
                    }
                }
                if (matched == 0) {
                    feedback.accept(Text.literal(
                        "No layer named '" + layerFilter + "' found in " + inputPath.getFileName()));
                    return 0;
                }
            }
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

    /**
     * Lists available schematics in the schematics directory, paginated.
     *
     * @param page     1-based page number
     * @param feedback receives feedback text
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executeList(int page, Consumer<Text> feedback) {
        if (!Files.exists(SCHEMATICS_DIR)) {
            feedback.accept(Text.literal("No schematics directory found."));
            return 0;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(SCHEMATICS_DIR)) {
            files = stream
                .filter(p -> p.getFileName().toString().endsWith(".msf"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            feedback.accept(Text.literal("Error listing schematics: " + e.getMessage()));
            return 0;
        }

        int total = files.size();
        if (total == 0) {
            feedback.accept(Text.literal("No schematics found."));
            return 1;
        }

        int totalPages = (total + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int start = (clampedPage - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, total);

        feedback.accept(Text.literal(
            "Schematics (" + clampedPage + "/" + totalPages + ", total: " + total + "):"));
        for (int i = start; i < end; i++) {
            feedback.accept(Text.literal("  " + files.get(i).getFileName()));
        }
        return 1;
    }

    /**
     * Reads an MSF file and outputs its metadata to chat (F5.1, V1.1).
     *
     * @param path     path to the MSF file (without the {@code .msf} extension is NOT added here)
     * @param feedback receives feedback text
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executeInfo(Path path, Consumer<Text> feedback) {
        if (!Files.exists(path)) {
            feedback.accept(Text.literal("File not found: " + path.getFileName()));
            return 0;
        }

        MsfFile file;
        try {
            byte[] bytes = Files.readAllBytes(path);
            file = MsfReader.readFile(bytes, MsfReaderConfig.DEFAULT, null);
        } catch (MsfException | IOException e) {
            feedback.accept(Text.literal("Error reading MSF file: " + e.getMessage()));
            return 0;
        }

        MsfMetadata meta = file.metadata();
        int layerCount = file.layerIndex().layers().size();
        int regionCount = file.layerIndex().layers().stream()
            .mapToInt(l -> l.regions().size()).sum();
        long blockCount = file.layerIndex().layers().stream()
            .flatMap(l -> l.regions().stream())
            .mapToLong(r -> (long) r.sizeX() * r.sizeY() * r.sizeZ())
            .sum();
        String facingName;
        try {
            facingName = CanonicalFacing.fromMsfValue(meta.canonicalFacing()).name();
        } catch (IllegalArgumentException e) {
            facingName = "unknown(" + meta.canonicalFacing() + ")";
        }

        // V1.1 — format version + msf-fabric impl version
        feedback.accept(Text.literal("=== " + path.getFileName() + " ==="));
        feedback.accept(Text.literal(
            "Format: V" + file.header().majorVersion() + "." + file.header().minorVersion()
            + "  msf-fabric: " + FABRIC_VERSION));
        if (meta.name() != null && !meta.name().isEmpty()) {
            feedback.accept(Text.literal("Name: " + meta.name()));
        }
        if (meta.author() != null && !meta.author().isEmpty()) {
            feedback.accept(Text.literal("Author: " + meta.author()));
        }
        if (meta.description() != null && !meta.description().isEmpty()) {
            feedback.accept(Text.literal("Description: " + meta.description()));
        }
        feedback.accept(Text.literal(
            "Layers: " + layerCount + "  Regions: " + regionCount
            + "  Blocks: " + blockCount));
        feedback.accept(Text.literal("Canonical facing: " + facingName));
        return 1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Scans the given bounding box for block entities and extracts them as MSF records.
     * Positions in the returned records are relative to {@code anchor}.
     */
    private static List<MsfBlockEntity> extractBlockEntities(
        ServerWorld world, BlockBox bounds, BlockPos anchor
    ) {
        List<MsfBlockEntity> result = new ArrayList<>();
        for (int bx = bounds.getMinX(); bx <= bounds.getMaxX(); bx++) {
            for (int by = bounds.getMinY(); by <= bounds.getMaxY(); by++) {
                for (int bz = bounds.getMinZ(); bz <= bounds.getMaxZ(); bz++) {
                    BlockEntity be = world.getBlockEntity(new BlockPos(bx, by, bz));
                    if (be != null) {
                        result.add(BlockEntityBridge.fromBlockEntity(be, anchor));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns the {@link CanonicalFacing} for the player's horizontal facing direction.
     * Falls back to {@link CanonicalFacing#NORTH} for non-player sources.
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

    /** Parses a mirror string ({@code none|x|z}); unrecognised values map to {@code NONE}. */
    private static Mirror parseMirror(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> Mirror.X;
            case "z" -> Mirror.Z;
            default  -> Mirror.NONE;
        };
    }

    /** Extracts the (x1,y1,z1)–(x2,y2,z2) bounding box from the command context. */
    private static BlockBox boundsFromCtx(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        return BlockBox.create(
            new BlockPos(
                IntegerArgumentType.getInteger(ctx, "x1"),
                IntegerArgumentType.getInteger(ctx, "y1"),
                IntegerArgumentType.getInteger(ctx, "z1")
            ),
            new BlockPos(
                IntegerArgumentType.getInteger(ctx, "x2"),
                IntegerArgumentType.getInteger(ctx, "y2"),
                IntegerArgumentType.getInteger(ctx, "z2")
            )
        );
    }

    /** Extracts the (x,y,z) anchor position from the command context. */
    private static BlockPos anchorFromCtx(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        return new BlockPos(
            IntegerArgumentType.getInteger(ctx, "x"),
            IntegerArgumentType.getInteger(ctx, "y"),
            IntegerArgumentType.getInteger(ctx, "z")
        );
    }
}

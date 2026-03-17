package dev.msf.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Formatting;
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
 *   <li>{@code /msf list [page <n>]} — lists all {@code .msf} files in the schematics
 *       directory with filename, size, format version, and layer count. Paginates at
 *       8 entries per page with clickable prev/next navigation.</li>
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
     * Registers {@code /msf extract}, {@code /msf place}, and {@code /msf list}
     * with the given dispatcher.
     *
     * @param dispatcher the server command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("msf")
                .then(buildExtractCommand())
                .then(buildPlaceCommand())
                .then(buildListCommand())
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildExtractCommand() {
        return CommandManager.literal("extract")
            .then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos())
            .then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos())
            .then(CommandManager.argument("filename", StringArgumentType.word())
                // No flags — existing v1.0.0 behaviour
                .executes(ctx -> executeExtractCmd(ctx, false, 1))
                // --living-mobs [--layers N]
                .then(CommandManager.literal("--living-mobs")
                    .executes(ctx -> executeExtractCmd(ctx, true, 1))
                    .then(CommandManager.literal("--layers")
                    .then(CommandManager.argument("numLayers", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeExtractCmd(
                            ctx, true, IntegerArgumentType.getInteger(ctx, "numLayers")))
                    ))
                )
                // --layers N [--living-mobs]
                .then(CommandManager.literal("--layers")
                .then(CommandManager.argument("numLayers", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeExtractCmd(
                        ctx, false, IntegerArgumentType.getInteger(ctx, "numLayers")))
                    .then(CommandManager.literal("--living-mobs")
                        .executes(ctx -> executeExtractCmd(
                            ctx, true, IntegerArgumentType.getInteger(ctx, "numLayers")))
                    )
                ))
            )))
        ;
    }

    /** Extracts pos1, pos2, filename, and source from {@code ctx} and delegates to {@link #executeExtract}. */
    private static int executeExtractCmd(
        CommandContext<ServerCommandSource> ctx,
        boolean includeLivingMobs,
        int numLayers
    ) {
        BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx, "pos2");
        String filename = StringArgumentType.getString(ctx, "filename");
        ServerCommandSource source = ctx.getSource();
        BlockBox bounds = BlockBox.create(pos1, pos2);
        Path outputPath = SCHEMATICS_DIR.resolve(filename + ".msf");
        return executeExtract(
            source.getWorld(), bounds, facingFromSource(source),
            includeLivingMobs, numLayers, outputPath,
            msg -> source.sendFeedback(() -> msg, false)
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildPlaceCommand() {
        return CommandManager.literal("place")
            .then(CommandManager.argument("filename", StringArgumentType.word())
            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                // No flags — existing v1.0.0 behaviour
                .executes(ctx -> executePlaceCmd(ctx, null, BlockMirror.NONE))
                // --rotate [--mirror]
                .then(CommandManager.literal("--rotate")
                .then(CommandManager.argument("degrees", IntegerArgumentType.integer(0, 270))
                    .suggests((ctx, b) -> { b.suggest(0); b.suggest(90); b.suggest(180); b.suggest(270); return b.buildFuture(); })
                    .executes(ctx -> executePlaceCmd(
                        ctx, IntegerArgumentType.getInteger(ctx, "degrees"), BlockMirror.NONE))
                    .then(CommandManager.literal("--mirror")
                    .then(CommandManager.argument("axis", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("x"); b.suggest("z"); return b.buildFuture(); })
                        .executes(ctx -> executePlaceCmd(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "degrees"),
                            mirrorFromAxis(StringArgumentType.getString(ctx, "axis"))
                        ))
                    ))
                ))
                // --mirror [--rotate]
                .then(CommandManager.literal("--mirror")
                .then(CommandManager.argument("axis", StringArgumentType.word())
                    .suggests((ctx, b) -> { b.suggest("x"); b.suggest("z"); return b.buildFuture(); })
                    .executes(ctx -> executePlaceCmd(
                        ctx, null, mirrorFromAxis(StringArgumentType.getString(ctx, "axis"))))
                    .then(CommandManager.literal("--rotate")
                    .then(CommandManager.argument("degrees", IntegerArgumentType.integer(0, 270))
                        .suggests((ctx, b) -> { b.suggest(0); b.suggest(90); b.suggest(180); b.suggest(270); return b.buildFuture(); })
                        .executes(ctx -> executePlaceCmd(
                            ctx,
                            IntegerArgumentType.getInteger(ctx, "degrees"),
                            mirrorFromAxis(StringArgumentType.getString(ctx, "axis"))
                        ))
                    ))
                ))
            ))
        ;
    }

    /** Extracts filename, pos, and source from {@code ctx} and delegates to {@link #executePlace}. */
    private static int executePlaceCmd(
        CommandContext<ServerCommandSource> ctx,
        Integer rotateDeg,
        BlockMirror mirror
    ) {
        String filename = StringArgumentType.getString(ctx, "filename");
        BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
        ServerCommandSource source = ctx.getSource();
        Path inputPath = SCHEMATICS_DIR.resolve(filename + ".msf");
        return executePlace(
            source.getWorld(), pos, facingFromSource(source),
            rotateDeg, mirror, inputPath,
            msg -> source.sendFeedback(() -> msg, false)
        );
    }

    /** Maps axis string {@code "x"} → {@link BlockMirror#LEFT_RIGHT}, {@code "z"} → {@link BlockMirror#FRONT_BACK}. */
    private static BlockMirror mirrorFromAxis(String axis) {
        return switch (axis) {
            case "x" -> BlockMirror.LEFT_RIGHT;
            case "z" -> BlockMirror.FRONT_BACK;
            default  -> BlockMirror.NONE;
        };
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildListCommand() {
        return CommandManager.literal("list")
            .executes(ctx -> executeList(ctx.getSource(), 1))
            .then(CommandManager.literal("page")
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeList(
                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")
                    ))
                )
            );
    }

    // =========================================================================
    // Core logic (public for testmod access)
    // =========================================================================

    /**
     * Extracts the given bounding box from {@code world} and writes a single-layer MSF file
     * with default entity capture policy (living mobs excluded).
     *
     * <p>Delegates to
     * {@link #executeExtract(ServerWorld, BlockBox, CanonicalFacing, boolean, int, Path, Consumer)}.
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
        return executeExtract(world, bounds, facing, false, 1, outputPath, feedback);
    }

    /**
     * Extracts the given bounding box from {@code world} and writes an MSF file.
     *
     * <h2>Layers</h2>
     * When {@code numLayers} is 1 (the default), the entire bounds is stored as a single
     * region in a single layer — identical to v1.0.0 behaviour. When {@code numLayers > 1},
     * the vertical extent is subdivided into {@code numLayers} horizontal layers using
     * ceiling division: each layer has height {@code ceil(totalHeight / numLayers)}, except
     * the last which receives the remainder. Each layer contains one region extracted
     * independently with the shared global palette.
     *
     * <h2>Entity capture policy</h2>
     * When {@code includeLivingMobs} is {@code false}, living mob entities (subtypes of
     * {@link net.minecraft.entity.LivingEntity} other than armor stands) are excluded from
     * the entity block. Armor stands and non-living entities (item frames, etc.) are always
     * captured. Pass {@code true} to opt in to capturing all non-player entities.
     *
     * <h2>Anchor</h2>
     * The anchor is always the minimum corner of {@code bounds}.
     *
     * @param world             the source world
     * @param bounds            the region to extract
     * @param facing            canonical facing to embed in the MSF metadata
     * @param includeLivingMobs when {@code true}, living mob entities are captured
     * @param numLayers         number of horizontal layers to split the region into (≥ 1)
     * @param outputPath        path to write the MSF file; parent directory created if absent
     * @param feedback          receives feedback text (success or error messages)
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executeExtract(
        ServerWorld world,
        BlockBox bounds,
        CanonicalFacing facing,
        boolean includeLivingMobs,
        int numLayers,
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

        // Shared palette across all layers (Section 4.3 — single global palette per file)
        List<String> palette = new ArrayList<>();

        // Build layers by subdividing the vertical extent
        int totalHeight = bounds.getBlockCountY();
        int baseLayerHeight = (totalHeight + numLayers - 1) / numLayers; // ceil division
        List<MsfLayer> layers = new ArrayList<>();

        int yOffset = 0;
        for (int i = 0; i < numLayers && yOffset < totalHeight; i++) {
            int thisHeight = Math.min(baseLayerHeight, totalHeight - yOffset);
            BlockBox layerBounds = new BlockBox(
                bounds.getMinX(), bounds.getMinY() + yOffset, bounds.getMinZ(),
                bounds.getMaxX(), bounds.getMinY() + yOffset + thisHeight - 1, bounds.getMaxZ()
            );
            MsfRegion region;
            try {
                region = RegionExtractor.extract(world, layerBounds, anchor, false, palette);
            } catch (MsfPaletteException e) {
                feedback.accept(Text.literal("Error: palette overflow — " + e.getMessage()));
                return 0;
            }
            // Single-layer: use "layer" for backwards compat; multi-layer: "layer1", "layer2", …
            String layerName = (numLayers == 1) ? "layer" : ("layer" + (i + 1));
            layers.add(MsfLayer.builder().layerId(i + 1).name(layerName).addRegion(region).build());
            yOffset += thisHeight;
        }

        // Capture entities and block entities from the full bounds (Section 8, Section 9)
        List<MsfEntity> entities = RegionExtractor.extractEntities(world, bounds, anchor, includeLivingMobs);
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
                .layerIndex(MsfLayerIndex.of(layers));
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

        String layerNote = (layers.size() > 1) ? " (" + layers.size() + " layers)" : "";
        feedback.accept(Text.literal(
            "Extracted " + bounds.getBlockCountX() + "\u00d7"
                + bounds.getBlockCountY() + "\u00d7" + bounds.getBlockCountZ()
                + " region to " + outputPath.getFileName() + layerNote
        ));
        return 1;
    }

    /**
     * Reads an MSF file and places it in {@code world} at the given anchor with no explicit
     * rotation override and no mirror. The rotation is derived from the delta between the
     * canonical facing in the file metadata and {@code targetFacing}.
     *
     * <p>Delegates to {@link #executePlace(ServerWorld, BlockPos, CanonicalFacing, Integer, BlockMirror, Path, Consumer)}.
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
        return executePlace(world, anchor, targetFacing, null, BlockMirror.NONE, inputPath, feedback);
    }

    /**
     * Reads an MSF file and places it in {@code world} at the given anchor, with optional
     * explicit rotation and mirror.
     *
     * <p>When {@code rotateDeg} is non-null the placement uses that explicit clockwise rotation
     * (0 / 90 / 180 / 270 degrees) instead of deriving rotation from the player's facing. When
     * {@code rotateDeg} is {@code null} the rotation is the delta from the file's canonical
     * facing to {@code targetFacing}, preserving the v1.0.0 behaviour.
     *
     * <p>The mirror transform (if any) is applied before rotation, matching Minecraft's
     * structure placement convention.
     *
     * @param world        the target world
     * @param anchor       world position to use as the MSF anchor point
     * @param targetFacing the player's facing; ignored when {@code rotateDeg} is non-null
     * @param rotateDeg    explicit clockwise rotation in degrees (0 / 90 / 180 / 270), or
     *                     {@code null} to derive rotation from {@code targetFacing}
     * @param mirror       mirror transform to apply; {@link BlockMirror#NONE} for no mirror
     * @param inputPath    path to read the MSF file from
     * @param feedback     receives feedback text (success or error messages)
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executePlace(
        ServerWorld world,
        BlockPos anchor,
        CanonicalFacing targetFacing,
        Integer rotateDeg,
        BlockMirror mirror,
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

        // Explicit --rotate N: compute effective target as canonical rotated by N/90 CW steps.
        // Null (player-facing mode): use targetFacing directly — identical to v1.0.0 behaviour.
        CanonicalFacing effectiveTarget;
        if (rotateDeg != null) {
            if (rotateDeg % 90 != 0) {
                feedback.accept(Text.literal("--rotate must be 0, 90, 180, or 270; got " + rotateDeg));
                return 0;
            }
            effectiveTarget = canonicalFacing.rotateClockwise(rotateDeg / 90);
        } else {
            effectiveTarget = targetFacing;
        }

        PlacementOptions options = new PlacementOptions(false, true, true, canonicalFacing, effectiveTarget, mirror);

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
    // List logic
    // =========================================================================

    private static final int LIST_PAGE_SIZE = 8;

    private static int executeList(ServerCommandSource source, int page) {
        Consumer<Text> feedback = msg -> source.sendFeedback(() -> msg, false);

        // Collect all .msf files, sorted by name
        List<Path> files;
        if (!Files.isDirectory(SCHEMATICS_DIR)) {
            feedback.accept(Text.literal("No schematics found (msf-schematics/ does not exist)."));
            return 1;
        }
        try (Stream<Path> stream = Files.list(SCHEMATICS_DIR)) {
            files = stream
                .filter(p -> p.getFileName().toString().endsWith(".msf"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            feedback.accept(Text.literal("Error listing schematics: " + e.getMessage()));
            return 0;
        }

        if (files.isEmpty()) {
            feedback.accept(Text.literal("No schematics found in msf-schematics/."));
            return 1;
        }

        int totalPages = Math.max(1, (files.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int clampedPage = Math.min(Math.max(page, 1), totalPages);
        int start = (clampedPage - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, files.size());

        feedback.accept(Text.literal(
            "--- MSF Schematics (page " + clampedPage + "/" + totalPages
                + ", " + files.size() + " file" + (files.size() != 1 ? "s" : "") + ") ---"
        ).formatted(Formatting.GOLD));

        for (int i = start; i < end; i++) {
            Path f = files.get(i);
            String name = f.getFileName().toString();
            String sizePart = formatFileSize(f);
            String metaPart = readFileMeta(f);
            feedback.accept(Text.literal(
                (i + 1) + ". " + name + "  " + sizePart + "  " + metaPart
            ));
        }

        // Pagination nav row
        if (totalPages > 1) {
            MutableText nav = Text.empty();
            if (clampedPage > 1) {
                nav.append(Text.literal("[← prev]").styled(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/msf list page " + (clampedPage - 1)))
                    .withFormatting(Formatting.AQUA)));
                nav.append(Text.literal("  "));
            }
            nav.append(Text.literal("Page " + clampedPage + "/" + totalPages));
            if (clampedPage < totalPages) {
                nav.append(Text.literal("  "));
                nav.append(Text.literal("[next →]").styled(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/msf list page " + (clampedPage + 1)))
                    .withFormatting(Formatting.AQUA)));
            }
            feedback.accept(nav);
        }

        return 1;
    }

    /** Returns a human-readable file size string, or "?" on error. */
    private static String formatFileSize(Path file) {
        try {
            long size = Files.size(file);
            if (size < 1024L) {
                return size + " B";
            } else if (size < 1048576L) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / 1048576.0);
            }
        } catch (IOException e) {
            return "?";
        }
    }

    /**
     * Reads the MSF header and layer index from a file and returns a display string
     * of the form {@code V1.0  3 layers}.
     * Returns {@code [error: <message>]} if the file cannot be parsed.
     */
    private static String readFileMeta(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MsfFile msf = MsfReader.readFile(bytes, MsfReaderConfig.allowChecksumFailure(), null);
            int major = msf.header().majorVersion();
            int minor = msf.header().minorVersion();
            int layers = msf.layerIndex().layers().size();
            return "V" + major + "." + minor + "  " + layers + " layer" + (layers != 1 ? "s" : "");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Truncate long messages to keep the chat line readable
            if (msg.length() > 60) msg = msg.substring(0, 57) + "...";
            return "[error: " + msg + "]";
        }
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

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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Registers the {@code /msf extract}, {@code /msf place}, {@code /msf list}, and
 * {@code /msf preview} commands.
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
 *   <li>{@code /msf preview <filename>} — shows a persistent end-rod particle wireframe
 *       bounding box at the player's look target. {@code /msf preview off} cancels it.</li>
 * </ul>
 *
 * <h2>Output directory</h2>
 * Schematics are written to and read from a {@code msf-schematics/} directory relative
 * to the JVM working directory. The directory is created on demand.
 */
public final class MsfCommands {

    private static final Path SCHEMATICS_DIR = Path.of("msf-schematics");

    // =========================================================================
    // Preview state
    // =========================================================================

    /** Stores the bounding box for one player's active bounding box preview. */
    private record ActivePreview(
        ServerWorld world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ) {}

    /** Active previews keyed by player UUID. */
    private static final Map<UUID, ActivePreview> activePreviews = new HashMap<>();

    private static int previewTickCounter = 0;

    /** Particle wireframe is refreshed every N ticks. */
    private static final int PREVIEW_TICK_INTERVAL = 10;

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
                .then(buildPreviewCommand())
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildPreviewCommand() {
        return CommandManager.literal("preview")
            // /msf preview off
            .then(CommandManager.literal("off")
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    Entity entity = source.getEntity();
                    if (entity instanceof ServerPlayerEntity player) {
                        clearPreview(player.getUuid());
                        source.sendFeedback(() -> Text.literal("Preview cancelled."), false);
                    }
                    return 1;
                })
            )
            // /msf preview <filename>
            .then(CommandManager.argument("filename", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    if (Files.isDirectory(SCHEMATICS_DIR)) {
                        try (Stream<Path> stream = Files.list(SCHEMATICS_DIR)) {
                            stream.filter(p -> p.getFileName().toString().endsWith(".msf"))
                                .forEach(p -> b.suggest(
                                    p.getFileName().toString().replace(".msf", "")));
                        } catch (IOException ignored) {}
                    }
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    Entity entity = source.getEntity();
                    if (!(entity instanceof ServerPlayerEntity player)) {
                        source.sendFeedback(
                            () -> Text.literal("preview requires a player source."), false);
                        return 0;
                    }
                    String filename = StringArgumentType.getString(ctx, "filename");
                    Path inputPath = SCHEMATICS_DIR.resolve(filename + ".msf");
                    return executePreview(source.getWorld(), player, inputPath,
                        msg -> source.sendFeedback(() -> msg, false));
                })
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
    // Preview logic
    // =========================================================================

    /**
     * Called every server tick. Refreshes active particle wireframes every
     * {@link #PREVIEW_TICK_INTERVAL} ticks.
     *
     * <p>Intended to be registered as a {@code ServerTickEvents.END_SERVER_TICK} handler.
     *
     * @param server the running Minecraft server
     */
    public static void tickPreviews(MinecraftServer server) {
        if (activePreviews.isEmpty()) return;
        previewTickCounter++;
        if (previewTickCounter % PREVIEW_TICK_INTERVAL != 0) return;
        for (Map.Entry<UUID, ActivePreview> entry : activePreviews.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            ActivePreview p = entry.getValue();
            spawnBoxParticles(p.world(), player, p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(), p.maxZ());
        }
    }

    /**
     * Removes the active preview for the given player UUID, if any.
     *
     * <p>Intended to be called from a {@code ServerPlayConnectionEvents.DISCONNECT} handler.
     *
     * @param playerUuid the UUID of the disconnecting or cancelling player
     */
    public static void clearPreview(UUID playerUuid) {
        activePreviews.remove(playerUuid);
    }

    /**
     * Registers a particle wireframe preview for {@code player} using the bounding box
     * computed from all regions in the given MSF file. The anchor is derived from the
     * player's look target (raycasted block face, or player feet if no block is hit).
     *
     * <p>The wireframe is re-spawned every {@link #PREVIEW_TICK_INTERVAL} ticks until
     * the player runs {@code /msf preview off} or disconnects.
     *
     * @param world     the world in which to spawn particles
     * @param player    the player who issued the preview command
     * @param inputPath path to the {@code .msf} file
     * @param feedback  receives feedback text (success or error messages)
     * @return {@code 1} on success, {@code 0} on failure
     */
    public static int executePreview(
        ServerWorld world,
        ServerPlayerEntity player,
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

        // Compute the union bounding box over all regions across all layers
        int minOX = Integer.MAX_VALUE, minOY = Integer.MAX_VALUE, minOZ = Integer.MAX_VALUE;
        int maxOX = Integer.MIN_VALUE, maxOY = Integer.MIN_VALUE, maxOZ = Integer.MIN_VALUE;
        for (MsfLayer layer : file.layerIndex().layers()) {
            for (MsfRegion region : layer.regions()) {
                minOX = Math.min(minOX, region.originX());
                minOY = Math.min(minOY, region.originY());
                minOZ = Math.min(minOZ, region.originZ());
                maxOX = Math.max(maxOX, region.originX() + region.sizeX() - 1);
                maxOY = Math.max(maxOY, region.originY() + region.sizeY() - 1);
                maxOZ = Math.max(maxOZ, region.originZ() + region.sizeZ() - 1);
            }
        }

        if (minOX == Integer.MAX_VALUE) {
            feedback.accept(Text.literal("File has no regions to preview."));
            return 0;
        }

        // Anchor: face of the block the player is looking at, or player feet
        BlockPos anchor = computePreviewAnchor(player);

        int worldMinX = anchor.getX() + minOX;
        int worldMinY = anchor.getY() + minOY;
        int worldMinZ = anchor.getZ() + minOZ;
        int worldMaxX = anchor.getX() + maxOX;
        int worldMaxY = anchor.getY() + maxOY;
        int worldMaxZ = anchor.getZ() + maxOZ;

        ActivePreview preview = new ActivePreview(
            world, worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ);
        activePreviews.put(player.getUuid(), preview);

        // Spawn immediately without waiting for the first tick interval
        spawnBoxParticles(world, player, worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ);

        int sX = worldMaxX - worldMinX + 1;
        int sY = worldMaxY - worldMinY + 1;
        int sZ = worldMaxZ - worldMinZ + 1;
        feedback.accept(Text.literal(
            "Preview: " + inputPath.getFileName() + " — "
                + sX + "\u00d7" + sY + "\u00d7" + sZ
                + " at (" + worldMinX + ", " + worldMinY + ", " + worldMinZ + ")"
        ));
        return 1;
    }

    /**
     * Returns the anchor {@link BlockPos} for a preview: the empty block face adjacent to
     * the block the player is looking at (up to 8 blocks away), or the player's feet
     * position if no block is hit.
     */
    private static BlockPos computePreviewAnchor(ServerPlayerEntity player) {
        HitResult hit = player.raycast(8.0, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            return blockHit.getBlockPos().offset(blockHit.getSide());
        }
        return player.getBlockPos();
    }

    /**
     * Spawns end-rod particles along all 12 edges of the axis-aligned bounding box
     * defined by the inclusive block coordinates {@code (minX,minY,minZ)}–{@code (maxX,maxY,maxZ)}.
     * One particle is spawned at the center of each block position along each edge.
     */
    private static void spawnBoxParticles(
        ServerWorld world, ServerPlayerEntity player,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
    ) {
        // 4 edges parallel to X
        spawnEdge(world, player, minX, minY, minZ, maxX, minY, minZ);
        spawnEdge(world, player, minX, maxY, minZ, maxX, maxY, minZ);
        spawnEdge(world, player, minX, minY, maxZ, maxX, minY, maxZ);
        spawnEdge(world, player, minX, maxY, maxZ, maxX, maxY, maxZ);
        // 4 edges parallel to Y
        spawnEdge(world, player, minX, minY, minZ, minX, maxY, minZ);
        spawnEdge(world, player, maxX, minY, minZ, maxX, maxY, minZ);
        spawnEdge(world, player, minX, minY, maxZ, minX, maxY, maxZ);
        spawnEdge(world, player, maxX, minY, maxZ, maxX, maxY, maxZ);
        // 4 edges parallel to Z
        spawnEdge(world, player, minX, minY, minZ, minX, minY, maxZ);
        spawnEdge(world, player, maxX, minY, minZ, maxX, minY, maxZ);
        spawnEdge(world, player, minX, maxY, minZ, minX, maxY, maxZ);
        spawnEdge(world, player, maxX, maxY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Spawns one end-rod particle at the center of each block position along the
     * axis-aligned edge from {@code (x1,y1,z1)} to {@code (x2,y2,z2)} (inclusive).
     * The edge must be axis-aligned (only one of X, Y, or Z varies).
     */
    private static void spawnEdge(
        ServerWorld world, ServerPlayerEntity player,
        int x1, int y1, int z1,
        int x2, int y2, int z2
    ) {
        int dx = Integer.signum(x2 - x1);
        int dy = Integer.signum(y2 - y1);
        int dz = Integer.signum(z2 - z1);
        int length = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        for (int i = 0; i <= length; i++) {
            world.spawnParticles(player, ParticleTypes.END_ROD, true, false,
                x1 + dx * i + 0.5, y1 + dy * i + 0.5, z1 + dz * i + 0.5,
                1, 0, 0, 0, 0);
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

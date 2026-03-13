package dev.msf.fabric.bridge;

import dev.msf.core.MsfPaletteException;
import dev.msf.core.model.MsfPalette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Collection;

/**
 * Converts between MSF blockstate strings and Minecraft {@link BlockState} objects.
 *
 * <h2>String format (Section 4.4)</h2>
 * <pre>
 *     minecraft:stone
 *     minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
 * </pre>
 *
 * <h2>Canonical property ordering (Section 4.3)</h2>
 * Properties are serialized in the order returned by
 * {@link StateManager#getProperties()}, which is backed by an
 * {@code ImmutableSortedMap} keyed by name — i.e. alphabetical by property name.
 * This matches the canonical Minecraft registration order referenced in the spec.
 *
 * <h2>Air shortcut (Section 4.3)</h2>
 * Palette ID 0 ({@code "minecraft:air"}) resolves to {@link Blocks#AIR}'s default
 * state without any registry lookup.
 */
public final class BlockStateBridge {

    private BlockStateBridge() {}

    // =========================================================================
    // fromString
    // =========================================================================

    /**
     * Parses a blockstate string into a Minecraft {@link BlockState}.
     *
     * <p>Palette ID 0 ({@code "minecraft:air"}) is resolved directly to
     * {@link Blocks#AIR#getDefaultState()} without a registry lookup (Section 4.3).
     *
     * @param blockstateString the canonical blockstate string
     * @return the corresponding {@link BlockState}
     * @throws MsfPaletteException if the block identifier is unknown or any property
     *                             name or value is invalid for the block
     */
    public static BlockState fromString(String blockstateString) throws MsfPaletteException {
        // Section 4.3 — palette ID 0 always resolves to air without registry lookup
        if (MsfPalette.AIR.equals(blockstateString)) {
            return Blocks.AIR.getDefaultState();
        }

        int bracketIdx = blockstateString.indexOf('[');
        String blockIdStr = bracketIdx == -1 ? blockstateString : blockstateString.substring(0, bracketIdx);

        Identifier blockId = Identifier.tryParse(blockIdStr);
        if (blockId == null) {
            throw new MsfPaletteException("Malformed block identifier: '" + blockIdStr + "'");
        }

        Block block = Registries.BLOCK.get(blockId);
        // DefaultedRegistry returns the default entry (air) when the id is absent
        if (block == Blocks.AIR && !MsfPalette.AIR.equals(blockIdStr)) {
            throw new MsfPaletteException("Unknown block: '" + blockIdStr + "'");
        }

        BlockState state = block.getDefaultState();

        if (bracketIdx != -1) {
            // Strip trailing ']'
            String propsStr = blockstateString.substring(bracketIdx + 1);
            if (!propsStr.endsWith("]")) {
                throw new MsfPaletteException(
                    "Blockstate string missing closing ']': '" + blockstateString + "'"
                );
            }
            propsStr = propsStr.substring(0, propsStr.length() - 1);

            for (String pair : propsStr.split(",", -1)) {
                int eq = pair.indexOf('=');
                if (eq == -1) {
                    throw new MsfPaletteException(
                        "Malformed property pair '" + pair + "' in: '" + blockstateString + "'"
                    );
                }
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                state = applyProperty(state, block, key, value, blockstateString);
            }
        }

        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(
        BlockState state, Block block, String key, String value, String fullString
    ) throws MsfPaletteException {
        Property<?> rawProp = block.getStateManager().getProperty(key);
        if (rawProp == null) {
            throw new MsfPaletteException(
                "Unknown property '" + key + "' for block '"
                + Registries.BLOCK.getId(block) + "' in: '" + fullString + "'"
            );
        }
        Property<T> property = (Property<T>) rawProp;
        return property.parse(value)
            .map(v -> state.with(property, v))
            .orElseThrow(() -> new MsfPaletteException(
                "Invalid value '" + value + "' for property '" + key
                + "' of block '" + Registries.BLOCK.getId(block) + "' in: '" + fullString + "'"
            ) {
                private static final long serialVersionUID = 1L;
            });
    }

    // =========================================================================
    // toString
    // =========================================================================

    /**
     * Serializes a {@link BlockState} to its canonical MSF blockstate string.
     *
     * <p>Properties are written in the order returned by
     * {@link StateManager#getProperties()} (alphabetical by name per Minecraft's
     * {@code ImmutableSortedMap} backing). All properties are included — none are
     * omitted for default values (Section 4.4).
     *
     * @param state the block state to serialize
     * @return the canonical blockstate string, e.g.
     *         {@code "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"}
     */
    public static String toString(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        String blockName = id.toString();

        Collection<Property<?>> properties = state.getBlock().getStateManager().getProperties();
        if (properties.isEmpty()) {
            return blockName;
        }

        StringBuilder sb = new StringBuilder(blockName).append('[');
        boolean first = true;
        for (Property<?> prop : properties) {
            if (!first) sb.append(',');
            sb.append(prop.getName()).append('=').append(valueName(state, prop));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }
}

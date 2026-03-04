package dev.msf.fabric.bridge;

import dev.msf.core.model.MsfPalette;

/**
 * Tests for {@link BlockStateBridge}.
 *
 * <p>All meaningful tests require a live Minecraft environment (registry access,
 * block state API) and must run via Fabric's gametest infrastructure
 * ({@code ./gradlew runGametest}).
 *
 * <p>Test cases to implement once Fabric Loom + testmod are set up:
 * <ol>
 *   <li><b>toStringCanonicalPropertyOrder</b> — {@code Blocks.OAK_STAIRS} with
 *       all four facings and both halves; assert properties appear in order
 *       {@code facing, half, shape, waterlogged} (alphabetical / registration order).</li>
 *   <li><b>fromStringRoundTrip</b> — parse a canonical string, serialize back,
 *       assert identical: stone, oak_log[axis=y], oak_stairs[...], repeater[...],
 *       trapdoor[...].</li>
 *   <li><b>fromStringUnknownBlockThrows</b> — assert {@link dev.msf.core.MsfPaletteException}
 *       for {@code "minecraft:nonexistent_block_xyz"}.</li>
 *   <li><b>airResolvesWithoutRegistryLookup</b> — call
 *       {@code fromString("minecraft:air")} and assert the returned state is
 *       {@code Blocks.AIR.getDefaultState()} (verifiable only with Minecraft
 *       initialized; the shortcut path is: no {@code Registries.BLOCK.get()} call
 *       for this string).</li>
 * </ol>
 *
 * <p>The only assertion that can be verified without Minecraft is that the
 * {@link MsfPalette#AIR} constant matches the string used in the shortcut path:
 */
public class BlockStateBridgeTest {

    // Compile-time assertion: the constant used by the bridge must be the spec value
    static {
        assert "minecraft:air".equals(MsfPalette.AIR)
            : "MsfPalette.AIR must be 'minecraft:air'";
    }

    // Gametest implementations go in the testmod source set once Fabric Loom is configured.
    // See docs/TESTING.md for gametest setup instructions.
}

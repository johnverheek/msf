package dev.msf.fabric.bridge;

import dev.msf.core.MsfParseException;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

/**
 * Converts between MSF biome identifier strings and Minecraft biome registry keys.
 *
 * <p>Biome identifiers in MSF are opaque UTF-8 strings such as {@code "minecraft:forest"}.
 * This bridge validates them against the live biome registry and converts back to string.
 */
public final class BiomeBridge {

    private BiomeBridge() {}

    // =========================================================================
    // fromString
    // =========================================================================

    /**
     * Resolves a biome identifier string to a {@link RegistryKey}.
     *
     * @param biomeIdentifier the MSF biome identifier string, e.g. {@code "minecraft:forest"}
     * @param registries      the registry lookup providing the biome registry
     * @return the registry key for the biome
     * @throws MsfParseException if the identifier is malformed or the biome does not
     *                           exist in the current registry
     */
    public static RegistryKey<Biome> fromString(
        String biomeIdentifier,
        RegistryWrapper.WrapperLookup registries
    ) throws MsfParseException {
        Identifier id = Identifier.tryParse(biomeIdentifier);
        if (id == null) {
            throw new MsfParseException("Malformed biome identifier: '" + biomeIdentifier + "'");
        }

        RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, id);
        RegistryWrapper.Impl<Biome> biomeRegistry = registries.getWrapperOrThrow(RegistryKeys.BIOME);
        if (biomeRegistry.getOptional(key).isEmpty()) {
            throw new MsfParseException("Unknown biome identifier: '" + biomeIdentifier + "'");
        }
        return key;
    }

    // =========================================================================
    // toString
    // =========================================================================

    /**
     * Serializes a biome registry key to its canonical identifier string.
     *
     * @param biomeKey the biome registry key
     * @return the identifier string, e.g. {@code "minecraft:forest"}
     */
    public static String toString(RegistryKey<Biome> biomeKey) {
        return biomeKey.getValue().toString();
    }
}

package dev.msf.fabric;

import dev.msf.fabric.command.MsfCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric mod initializer — registers MSF server commands and lifecycle events on startup.
 *
 * <p>Declared as the {@code main} entrypoint in {@code fabric.mod.json}.
 */
public class MsfModInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> MsfCommands.register(dispatcher)
        );
        // Refresh active preview particle wireframes every PREVIEW_TICK_INTERVAL ticks
        ServerTickEvents.END_SERVER_TICK.register(MsfCommands::tickPreviews);
        // Clear preview state when a player disconnects so the map doesn't grow unbounded
        ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) -> MsfCommands.clearPreview(handler.player.getUuid())
        );
    }
}

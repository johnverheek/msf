package dev.msf.fabric;

import dev.msf.fabric.command.MsfCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric mod initializer — registers MSF server commands on startup.
 *
 * <p>Declared as the {@code main} entrypoint in {@code fabric.mod.json}.
 */
public class MsfModInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> MsfCommands.register(dispatcher)
        );
    }
}

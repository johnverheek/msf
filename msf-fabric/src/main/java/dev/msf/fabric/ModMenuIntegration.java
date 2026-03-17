package dev.msf.fabric;

import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Registers msf-fabric with the Mod Menu API so it appears in the in-game mod list.
 *
 * <p>This class is declared as a {@code modmenu} entrypoint in {@code fabric.mod.json}.
 * It is only loaded when Mod Menu is installed; msf-fabric functions correctly without it.
 *
 * <p>No configuration screen is provided for v1.1.0 (msf-fabric has no user-configurable
 * settings at this time).
 */
public class ModMenuIntegration implements ModMenuApi {

    // No config screen for v1.1.0; default implementation returns screen -> null.
}

package dev.zprestige.prestige.client;

import dev.zprestige.prestige.config.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists the module/HUD state between launches.
 *
 * <p>The original client relied on its native module to restore the saved configuration and to write it back when the
 * game closed. Standalone, that responsibility moves here: the default snapshot lives in
 * {@code <gameDir>/prestige/config.json}, is applied once after the modules are registered, and is written when the
 * client stops. The Configs page keeps its own named snapshots under {@code prestige/configs/}.
 */
@Environment(EnvType.CLIENT)
public final class AutoConfig {
    private static final String FILE_NAME = "config.json";
    private static boolean loadAttempted;
    private static boolean saveRegistered;

    private AutoConfig() {}

    static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("prestige").resolve(FILE_NAME);
    }

    /** Loads the default config; safe to call when no file exists yet. */
    public static void load() {
        if (loadAttempted) return;
        loadAttempted = true;
        ConfigManager manager = Prestige.configManager;
        if (manager == null) {
            System.out.println("[Prestige] config load skipped: ConfigManager is not initialised");
            return;
        }
        Path file = file();
        if (!Files.exists(file)) {
            System.out.println("[Prestige] no saved config at " + file + " (first launch)");
            return;
        }
        try {
            String json = Files.readString(file);
            manager.loadFromJson(json);
            String report = manager.consumeLoadReport();
            System.out.println("[Prestige] config loaded from " + file + (report == null || report.isBlank() ? "" : ": " + report));
        } catch (Throwable error) {
            // A corrupt config must never stop the client from starting.
            System.err.println("[Prestige] could not load " + file + ": " + error);
            error.printStackTrace(System.err);
        }
    }

    /** Writes the current state; called when the client stops. */
    public static void save() {
        ConfigManager manager = Prestige.configManager;
        if (manager == null) return;
        try {
            String json = manager.saveToJson();
            if (json == null || json.isBlank()) return;
            Path file = file();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, json);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable error) {
            System.err.println("[Prestige] could not save the config: " + error);
            error.printStackTrace(System.err);
        }
    }

    /** Hooks the shutdown save once. */
    public static void registerSaveHook() {
        if (saveRegistered) return;
        saveRegistered = true;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }
}

package dev.zprestige.prestige.ui.mc;

import dev.zprestige.prestige.client.Prestige;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import dev.zprestige.prestige.util.Log;

/**
 * Fabric client entrypoint of the ClickGUI layer.
 *
 * <p>The original version polled GLFW for the menu key and drove the desktop imgui overlay. That path is gone: the
 * menu is now a normal Minecraft screen ({@code gui/ClickGuiScreen}) opened by the client's own KeybindManager when
 * the configured menu key is pressed, so it works with PojavLauncher's touch-to-key mapping and with a physical
 * keyboard alike.
 */
public final class Entry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        boolean android = isAndroid();
        Log.info("[prestige-ui] native ClickGUI ready (menu key opens it; platform=" + (android ? "android" : "desktop") + ")");
        if (!android) {
            // Desktop-only cosmetic splash window (Swing/AWT). It cannot exist on Android, where the JRE has no AWT peers.
            try {
                Skid.run();
            } catch (Throwable t) {
                Log.info("[prestige-ui] splash unavailable: " + t);
            }
        }
    }

    /** True on PojavLauncher/Android runtimes, where desktop-only AWT/GLFW windows are not available. */
    public static boolean isAndroid() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String vendor = System.getProperty("java.vendor", "").toLowerCase();
        if (os.contains("android") || vendor.contains("android")) return true;
        try {
            // Android JREs report a Linux VM but expose the Android runtime library.
            Class.forName("android.os.Build", false, Entry.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static {
        // Touch the client class early so Fabric reports a clear error if Prestige failed to load.
        if (FabricLoader.getInstance().isModLoaded("prestige") && Prestige.moduleManager == null) {
            Log.info("[prestige-ui] warning: Prestige has no module manager yet; the menu will retry once it initialises");
        }
    }
}

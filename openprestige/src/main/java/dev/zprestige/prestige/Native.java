package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.NativeId;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.gui.ClickGuiScreen;
import dev.zprestige.prestige.ui.bridge.Bootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;

/**
 * The former native-module surface of the client.
 *
 * <p>In the original build these methods were JNI natives implemented by prestige_native.dll, which also constructed
 * and registered the 121 modules. A standalone Fabric mod cannot ship or load that library on Android, so the two
 * methods that mattered are now implemented in Java:
 *
 * <ul>
 *   <li>{@link #registerModules(List)} builds every module listed in {@code prestige_ui/manifest_live.json} and adds it
 *       to the manager. Module constructors still call {@code ui.bridge.Bootstrap.pre(this)}, which injects the
 *       manifest settings, exactly as the native module did.</li>
 *   <li>{@link #toggleMenu()} / {@link #isMenuOpen()} drive the native Minecraft ClickGUI screen instead of the
 *       original desktop window.</li>
 * </ul>
 *
 * <p>The remaining fetch/submit hooks were unused by every module in this source tree (verified by searching for
 * {@code Native.} call sites); they are kept as documented no-ops so that any third-party code compiled against the
 * old class still links, and so the original interface remains visible. They never throw.
 */
public class Native {
    public static Class callbackClass;
    private static boolean modeWarningLogged;

    /**
     * Registers all modules from the live manifest into the given manager list.
     *
     * <p>Failures are reported with their stack trace and counted; one broken module never prevents the rest from
     * registering. Calling this twice is safe: classes already present are skipped.
     */
    @NativeId("f29b5f2e-3df2-4db8-bb6e-1c01a5f91f12")
    public static void registerModules(List modules) {
        if (modules == null) {
            System.err.println("[Prestige] Native.registerModules called without a module list; no modules registered");
            return;
        }
        Set<String> present = new HashSet<>();
        for (Object existing : (List<?>) modules) {
            if (existing != null) present.add(existing.getClass().getName());
        }
        int registered = 0, skipped = 0, failed = 0;
        ClassLoader loader = Native.class.getClassLoader();
        for (Map.Entry<String, Bootstrap.Mod> entry : Bootstrap.mods().entrySet()) {
            Bootstrap.Mod mod = entry.getValue();
            try {
                if (present.contains(mod.clazz)) {
                    skipped++;
                    continue;
                }
                Class<?> type = Bootstrap.loadModuleClass(mod.clazz, loader);
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object module = constructor.newInstance();
                if (!(module instanceof Module)) {
                    failed++;
                    System.err.println("[Prestige] module class is not a Module: " + mod.clazz);
                    continue;
                }
                Bootstrap.post(module);
                //noinspection unchecked
                modules.add(module);
                present.add(mod.clazz);
                registered++;
            } catch (Throwable error) {
                failed++;
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null
                        ? error.getCause() : error;
                System.err.println("[Prestige] module registration failed for " + mod.clazz + " (" + mod.name + "): " + cause);
                if (failed <= 5) cause.printStackTrace(System.err);
            }
        }
        System.out.println("[Prestige] modules registered=" + registered + " skipped=" + skipped + " failed=" + failed
                + " total=" + modules.size() + " manifest=" + Bootstrap.mods().size());
    }

    /** Native menu hook: toggles the ClickGUI screen on the client thread. */
    @NativeId("70d16b96-40d4-4e8b-8d11-111b87ee88c2")
    public static void toggleMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || Prestige.clickGuiScreen == null) return;
        client.execute(() -> {
            if (client.currentScreen instanceof ClickGuiScreen) {
                client.setScreen(null);
            } else {
                Prestige.clickGuiScreen.resetClosedState();
                client.setScreen(Prestige.clickGuiScreen);
            }
        });
    }

    /** True while the ClickGUI screen is open. */
    @NativeId("0dfaa780-8d92-4e8f-aa2e-9dae747c4dd6")
    public static boolean isMenuOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ClickGuiScreen;
    }

    /**
     * Per-tick native callback. The native module used it to pump its own UI; the native screen is driven by Minecraft
     * itself now, so this is intentionally empty (and is still invoked every tick by ModuleStateSync).
     */
    @NativeId("018b2f19-e79e-7d6a-a56d-29feb6211b04")
    public static void onClientTick() {
    }

    // ------------------------------------------------------------------ unused legacy native surface
    private static void legacy(String method) {
        if (!modeWarningLogged) {
            modeWarningLogged = true;
            System.out.println("[Prestige] legacy native hook " + method + "() invoked; running standalone without prestige_native.dll (no-op)");
        }
    }

    @NativeId("6937e6e7-60ef-45de-98e1-f177ec613e99")
    public static void fetchString(int id, StringBuilder out) {
        legacy("fetchString");
    }

    @NativeId("c4f1db16-7850-44eb-8f8a-863dfe33ab26")
    public static void fetchStatus(StringBuilder out) {
        legacy("fetchStatus");
    }

    @NativeId("774de945-aba3-4038-b5ae-d8ff48635feb")
    public static void request(String key, String value, boolean flag, String extra, StringBuilder out, String context) {
        legacy("request");
    }

    @NativeId("b6356619-bb5a-4616-a076-93f620a7d9de")
    public static void fetchText(int id, StringBuilder out) {
        legacy("fetchText");
    }

    @NativeId("9ae73026-310d-4cbc-b283-12f3f530fe67")
    public static void fetchField(int id, String key, StringBuilder out) {
        legacy("fetchField");
    }

    @NativeId("08dcf01c-9943-4f66-8dfd-120714d7abdd")
    public static void submit(String message) {
        legacy("submit");
    }
}

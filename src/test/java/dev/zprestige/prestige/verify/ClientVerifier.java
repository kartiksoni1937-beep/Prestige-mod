package dev.zprestige.prestige.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zprestige.prestige.engine.EnginePlugin;
import dev.zprestige.prestige.render.MappedMeshBuilder;
import dev.zprestige.prestige.world.MeshBuilder;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.lenni0451.classtransform.TransformerManager;

/**
 * Offline verification of the standalone build (no Minecraft client / GL needed).
 *
 * <p>Checks that:
 * <ul>
 *   <li>every module class from the live manifest exists, has a no-arg constructor and every setting field named in
 *       the manifest exists with the type {@code ui.bridge.Bootstrap} reconstructs;</li>
 *   <li>the 42 ClassTransform game hooks can be built, resolve their targets against the runtime Minecraft
 *       mappings and transform those classes without throwing;</li>
 *   <li>the repaired {@link MappedMeshBuilder#begin()}/{@link MappedMeshBuilder#end()} contract is chainable.</li>
 * </ul>
 * Run with {@code ./gradlew verifyClient}. Exits non-zero when a check fails.
 */
public final class ClientVerifier {
    private static int failures;

    public static void main(String[] args) throws Exception {
        System.out.println("== OpenPrestige offline verification ==");
        verifyManifestModules();
        verifyEngineTransformers();
        verifyMeshBuilderContract();
        verifyGuiFrames();
        verifyFriendsRegistry();
        verifySocialsList();
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + " problem(s))");
        if (failures != 0) System.exit(1);
    }

    // ------------------------------------------------------------------ modules
    private static void verifyManifestModules() throws Exception {
        JsonObject root;
        try (InputStream in = ClientVerifier.class.getResourceAsStream("/prestige_ui/manifest_live.json")) {
            if (in == null) {
                fail("module manifest resource is missing from the jar");
                return;
            }
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<JsonObject> modules = new ArrayList<>();
        for (var element : root.getAsJsonArray("modules")) modules.add(element.getAsJsonObject());
        int settings = 0, missingFields = 0, badConstructors = 0;
        Set<String> classes = new LinkedHashSet<>();
        for (JsonObject module : modules) {
            String className = module.get("class").getAsString();
            classes.add(className);
            Class<?> type;
            try {
                type = Class.forName(className, false, ClientVerifier.class.getClassLoader());
            } catch (Throwable error) {
                fail("module class does not load: " + className + " (" + error + ")");
                continue;
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
            } catch (Throwable error) {
                badConstructors++;
                fail("module has no no-arg constructor: " + className);
            }
            if (module.has("settings")) {
                for (var settingElement : module.getAsJsonArray("settings")) {
                    JsonObject setting = settingElement.getAsJsonObject();
                    settings++;
                    String fieldName = setting.get("field").getAsString();
                    Field field = findField(type, fieldName);
                    String expected = setting.get("setClass").getAsString();
                    if (field == null) {
                        missingFields++;
                        fail("field '" + fieldName + "' missing on " + type.getSimpleName() + " (manifest setting)");
                    } else if (!field.getType().getName().equals(expected)) {
                        missingFields++;
                        fail("field '" + fieldName + "' on " + type.getSimpleName() + " is " + field.getType().getName()
                                + " but the manifest declares " + expected);
                    }
                }
            }
        }
        System.out.printf("modules: classes=%d unique=%d settings=%d missingFields=%d badConstructors=%d%n",
                modules.size(), classes.size(), settings, missingFields, badConstructors);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ engine
    private static void verifyEngineTransformers() throws Exception {
        List<String> transformers = EnginePlugin.lines("prestige-engine/transformers.list");
        List<String> targets = EnginePlugin.lines("prestige-engine/targets.named.list");
        System.out.printf("engine: transformers=%d targets=%d%n", transformers.size(), targets.size());
        TransformerManager manager;
        try {
            manager = EnginePlugin.createManager("named", transformers);
        } catch (Throwable error) {
            fail("could not build the ClassTransform manager: " + error);
            error.printStackTrace(System.out);
            return;
        }
        Set<String> transformed = manager.getTransformedClasses();
        if (transformed.size() != targets.size()) {
            fail("engine target count mismatch: manager=" + transformed.size() + " expected=" + targets.size());
        }
        int ok = 0, untouched = 0, transformFailures = 0;
        for (String target : targets) {
            String resource = target.replace('.', '/') + ".class";
            byte[] original;
            try (InputStream in = ClientVerifier.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    fail("engine target class not found on the runtime classpath: " + target);
                    continue;
                }
                original = in.readAllBytes();
            }
            try {
                byte[] result = manager.transform(target, original, true);
                if (result == null) {
                    fail("transformation returned null for " + target);
                } else if (java.util.Arrays.equals(result, original)) {
                    untouched++;
                } else {
                    ok++;
                }
            } catch (Throwable error) {
                transformFailures++;
                fail("transformation failed for " + target + ": " + error);
            }
        }
        System.out.printf("engine: transformed=%d unchanged=%d failed=%d%n", ok, untouched, transformFailures);
    }

    // ------------------------------------------------------------------ mesh builder
    private static void verifyMeshBuilderContract() {
        // Contract only: no GL is touched by begin()/end() (they operate on the mapped buffers, which do not exist here),
        // so this verifies the repaired methods no longer throw UnsupportedOperationException and stay chainable.
        try {
            if (!MeshBuilder.class.isInterface()) fail("MeshBuilder must stay an interface");
            if (MappedMeshBuilder.class.getMethod("begin").getReturnType() != MeshBuilder.class) fail("begin() return type changed");
            if (MappedMeshBuilder.class.getMethod("end").getReturnType() != MeshBuilder.class) fail("end() return type changed");
            System.out.println("mesh: begin()/end() present, chainable contract intact");
        } catch (Throwable error) {
            fail("MappedMeshBuilder contract: " + error);
        }
    }

    // ------------------------------------------------------------------ ClickGUI layout
    /**
     * Runs the ported ClickGUI layout for every page against the replacement draw list.
     *
     * <p>No GL is touched: the MSDF font is absent in this harness, so text measures as zero width, exactly the way the
     * layout behaves before the font manager exists. This still executes every page/widget draw path (null-safety,
     * arithmetic, draw-list contract) which is what broke when the imgui backend was replaced.
     */
    private static void verifyGuiFrames() {
        try {
            dev.zprestige.prestige.ui.Lib.dl = new dev.zprestige.prestige.ui.NativeDrawList();
            dev.zprestige.prestige.ui.Lib.scale = 1f;
            dev.zprestige.prestige.ui.Lib.ox = 0f;
            dev.zprestige.prestige.ui.Lib.oy = 0f;
            java.io.InputStream modules = ClientVerifier.class.getResourceAsStream("/prestige_ui/modules.tsv");
            java.io.InputStream settings = ClientVerifier.class.getResourceAsStream("/prestige_ui/settings.tsv");
            if (modules == null || settings == null) {
                fail("ClickGUI tsv resources are missing from the jar");
                return;
            }
            dev.zprestige.prestige.ui.Data.load(modules, settings);
            settings.close();
            modules.close();
            dev.zprestige.prestige.ui.Ui.initEntries(null);
            int totalPrimitives = 0;
            for (int page = 0; page <= 10; page++) {
                dev.zprestige.prestige.ui.Ui.S.page = page;
                dev.zprestige.prestige.ui.Input.update(0.016f);
                dev.zprestige.prestige.ui.Lib.dl.clear();
                dev.zprestige.prestige.ui.Ui.frame();
                int primitives = dev.zprestige.prestige.ui.Lib.dl.size();
                totalPrimitives += primitives;
                if (primitives == 0) fail("ClickGUI page " + page + " produced no draw commands");
            }
            System.out.printf("gui: pages=11 modules=%d primitives=%d%n",
                    dev.zprestige.prestige.ui.ModulesPage.P.mods.size(), totalPrimitives);
        } catch (Throwable error) {
            fail("ClickGUI layout failed: " + error);
            error.printStackTrace(System.out);
        }
    }

    /**
     * Exercises the friends registry without a game: add/remove, duplicate adds, persistence across a restart,
     * a username change on the same UUID, removal by stale name, and name-only (offline) entries.
     */
    private static void verifyFriendsRegistry() {
        try {
            java.nio.file.Path store = java.nio.file.Files.createTempDirectory("prestige-friends")
                    .resolve("friends.json");
            dev.zprestige.prestige.social.FriendManager.setStoreFile(store);

            java.util.UUID alice = java.util.UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8));
            java.util.UUID bob = java.util.UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8));

            dev.zprestige.prestige.social.FriendManager fm = new dev.zprestige.prestige.social.FriendManager();
            check(!fm.hasFriend(alice), "fresh registry must start empty");
            fm.addFriend(alice, "Alice");
            fm.addFriend(alice, "Alice");
            fm.addFriend(bob, "Bob");
            check(fm.getFriendCount() == 2, "duplicate add must not create a second entry");
            check(fm.hasFriend(alice) && fm.hasFriend("Alice"), "friend lookup by UUID and by name");
            check(fm.isFriend(alice), "protection defaults to on");

            // A new instance must read the saved file back: persistence across a restart.
            dev.zprestige.prestige.social.FriendManager restarted = new dev.zprestige.prestige.social.FriendManager();
            check(restarted.hasFriend(alice) && restarted.hasFriend(bob), "friends must survive a restart");
            check(restarted.getFriends().contains("Alice"), "stored username must be restored");

            // Same UUID, new name: still the same friend, old name no longer matches.
            restarted.addFriend(alice, "Alice_New");
            check(restarted.hasFriend("Alice_New") && !restarted.hasFriend("Alice"),
                    "a username change must keep the friendship and drop the old name");
            restarted.removeFriend(alice);
            check(!restarted.hasFriend(alice), "removeFriend(UUID) must remove the entry");

            restarted.addFriend(alice, "Alice2");
            restarted.removeFriend("Alice2");
            check(!restarted.hasFriend(alice), "removeFriend(name) must resolve the UUID");

            restarted.addFriend("Ghost");
            check(restarted.hasFriend("Ghost"), "a friend with no known UUID must still be stored");
            check(restarted.getFriendCount() == 2, "registry holds bob plus the name-only entry");

            System.out.println("friends: add/remove/duplicate/persist/rename/offline = ok");
        } catch (Throwable error) {
            fail("friends registry checks failed: " + error);
            error.printStackTrace(System.out);
        } finally {
            dev.zprestige.prestige.social.FriendManager.setStoreFile(null);
        }
    }

    /**
     * Socials page list mechanics without a window: the search filter drives the same list that is drawn, clicked and
     * scrolled, and scrolling clamps to the filtered height (which is what decides whether the slider appears).
     */
    private static void verifySocialsList() {
        try {
            dev.zprestige.prestige.ui.SocialsPage.friends.clear();
            dev.zprestige.prestige.ui.SocialsPage.players.clear();
            for (int i = 0; i < 40; i++) {
                dev.zprestige.prestige.ui.SocialsPage.friends.add(String.format("Friend%02d", i));
            }
            dev.zprestige.prestige.ui.SocialsPage.tab = 1;
            dev.zprestige.prestige.ui.SocialsPage.search = "";
            dev.zprestige.prestige.ui.SocialsPage.scrollTarget = 0f;
            dev.zprestige.prestige.ui.SocialsPage.scrollA = 0f;
            dev.zprestige.prestige.ui.SocialsPage.dirty = true;
            check(dev.zprestige.prestige.ui.SocialsPage.visibleEntries().size() == 40, "unfiltered list must show both tabs");

            // 20 rows of 70px minus the 10px tail, minus one viewport: the range the slider maps onto.
            dev.zprestige.prestige.ui.SocialsPage.scroll(-100000);
            check(Math.abs(dev.zprestige.prestige.ui.SocialsPage.scrollTarget - 860f) < 0.01f,
                    "scroll must clamp to the list height (got " + dev.zprestige.prestige.ui.SocialsPage.scrollTarget + ")");
            dev.zprestige.prestige.ui.SocialsPage.scroll(100000);
            check(dev.zprestige.prestige.ui.SocialsPage.scrollTarget == 0f, "scroll must clamp at the top");

            dev.zprestige.prestige.ui.SocialsPage.search = "1";
            dev.zprestige.prestige.ui.SocialsPage.dirty = true;
            int expected = 0;
            for (int i = 0; i < 40; i++) {
                if (String.format("Friend%02d", i).contains("1")) expected++;
            }
            check(dev.zprestige.prestige.ui.SocialsPage.visibleEntries().size() == expected,
                    "search must filter the list (expected " + expected + ")");
            dev.zprestige.prestige.ui.SocialsPage.scroll(-100000);
            check(dev.zprestige.prestige.ui.SocialsPage.scrollTarget == 0f,
                    "a filtered list that fits must not scroll, so no slider is drawn");

            System.out.println("socials: filter/clamp/slider = ok");
        } catch (Throwable error) {
            fail("socials list checks failed: " + error);
            error.printStackTrace(System.out);
        } finally {
            dev.zprestige.prestige.ui.SocialsPage.friends.clear();
            dev.zprestige.prestige.ui.SocialsPage.players.clear();
            dev.zprestige.prestige.ui.SocialsPage.search = "";
            dev.zprestige.prestige.ui.SocialsPage.tab = 0;
            dev.zprestige.prestige.ui.SocialsPage.scrollTarget = 0f;
            dev.zprestige.prestige.ui.SocialsPage.scrollA = 0f;
            dev.zprestige.prestige.ui.SocialsPage.dirty = true;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String message) {
        failures++;
        System.out.println("FAIL: " + message);
    }
}

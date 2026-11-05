package dev.zprestige.prestige.inject;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.engine.EnginePlugin;
import dev.zprestige.prestige.engine.EngineState;
import dev.zprestige.prestige.hooks.Definer;
import dev.zprestige.prestige.ui.mc.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.lenni0451.classtransform.TransformerManager;
import net.minecraft.client.MinecraftClient;

/**
 * Entry point of the injected payload. prestige_native.dll appends this jar to the Fabric (Knot) class path, registers the
 * {@link NativeBridge} natives and calls {@link #start()}. Everything Fabric would normally do at launch for the Prestige mod
 * (Mixin anchors, entrypoints) is done here instead: the ClassTransform hooks are applied to the loaded classes with
 * {@link InPlaceTransformer} + JVMTI redefinition, then the mod entrypoints run on the client thread.
 */
public final class Bootstrap {
    private static boolean started;
    /** Class bytes as they were before the hooks, kept so self destruct can put the game classes back. */
    private static final Map<Class<?>, byte[]> ORIGINALS = new ConcurrentHashMap<>();

    private Bootstrap() {}

    /** Called by the native loader on its worker thread once Minecraft is up. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("prestige")) {
            // the jar is also installed in mods/: Mixin already applied the hooks and Fabric ran the entrypoints
            log("Prestige is already loaded as a Fabric mod, skipping injection");
            return;
        }
        try {
            log("waiting for the Minecraft client");
            waitForClient();
            Report report = install(NativeBridge::classBytes, NativeBridge::redefine, nativeBatch());
            log("hooks installed: " + report.redefined + " classes, " + report.moved + " handlers moved" + (report.failed.isEmpty() ? "" : ", FAILED " + report.failed));
            // Some clients (Lunar) name a few classes and methods differently. Keep going with the hooks that did apply: a missing
            // hook only disables the features behind it, while stopping here would leave the client without Prestige at all.
            if (!report.failed.isEmpty()) {
                if (report.redefined == 0) throw new IllegalStateException("could not hook " + report.failed);
                log("continuing with " + report.redefined + " hooked classes; skipped " + report.failed);
            }
            log("initialising Prestige on the client thread");
            MinecraftClient.getInstance().execute(Bootstrap::initClient);
        } catch (Throwable t) {
            log("start failed: " + t);
            t.printStackTrace(System.out);
        }
    }

    /** The loader may inject while the game is still starting: wait until the client and its window exist. */
    private static void waitForClient() throws InterruptedException {
        for (int i = 0; i < 6000; i++) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) return;
            Thread.sleep(50);
        }
        throw new IllegalStateException("Minecraft client did not start");
    }

    public static final class Report {
        public int redefined, moved;
        public final List<String> failed = new ArrayList<>();
    }

    /**
     * Applies all injection-mode hooks. The byte source and the redefiner are parameters so the transformation can be exercised
     * without the native library.
     */
    public static Report install(Function<Class<?>, byte[]> classBytes, ToIntBiFunction<Class<?>, byte[]> redefine) throws Exception {
        return install(classBytes, redefine, null);
    }

    /** Many classes per native call: one RetransformClasses / RedefineClasses (one safepoint each) instead of one per class. */
    public interface Batch {
        byte[][] classBytes(Class<?>[] types);

        int redefine(Class<?>[] types, byte[][] bytes);
    }

    /** The batch natives of {@link NativeBridge}, or null when the loaded prestige_native.dll predates them. */
    private static Batch nativeBatch() {
        try {
            NativeBridge.classBytesBatch(new Class<?>[0]);   // probes the registration; a missing native throws UnsatisfiedLinkError
        } catch (UnsatisfiedLinkError e) {
            return null;
        } catch (Throwable ignored) {
            // anything else means the native exists
        }
        return new Batch() {
            @Override public byte[][] classBytes(Class<?>[] types) { return NativeBridge.classBytesBatch(types); }
            @Override public int redefine(Class<?>[] types, byte[][] bytes) { return NativeBridge.redefineBatch(types, bytes); }
        };
    }

    /**
     * Same as {@link #install(Function, ToIntBiFunction)} with an optional batch path: all original bytes are captured with one
     * call, the ClassTransform step reuses cached results where it can, and all hooked classes are redefined with one call. A
     * failing batch step falls back to per-class calls, so one bad class only fails itself.
     */
    public static Report install(Function<Class<?>, byte[]> classBytes, ToIntBiFunction<Class<?>, byte[]> redefine, Batch batch) throws Exception {
        String namespace = EnginePlugin.runtimeNamespace();
        log("runtime namespace " + namespace + " (the loader reports " + FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace() + ")");
        List<String> transformers = new ArrayList<>(EnginePlugin.lines("prestige-engine/transformers.list"));
        transformers.addAll(EnginePlugin.lines("prestige-engine/inject-transformers.list"));
        TransformerManager manager = EnginePlugin.createManager(namespace, transformers);
        ClassLoader loader = Bootstrap.class.getClassLoader();
        InPlaceTransformer transformer = new InPlaceTransformer(manager, loader);
        EngineState.installed = true;

        Report report = new Report();
        List<String> targets = new ArrayList<>(manager.getTransformedClasses());
        int total = targets.size();
        log("transforming " + total + " hook targets");
        long start = System.nanoTime();

        // 1. resolve the target classes
        Class<?>[] types = new Class<?>[total];
        for (int i = 0; i < total; i++) {
            try {
                types[i] = Class.forName(targets.get(i), false, loader);
            } catch (Throwable t) {
                fail(report, targets.get(i), t);
            }
        }

        // 2. the bytes each class currently has: one native call for all of them, per-class for any that did not come back
        byte[][] originals = new byte[total][];
        if (batch != null) {
            Class<?>[] resolved = java.util.Arrays.stream(types).filter(java.util.Objects::nonNull).toArray(Class<?>[]::new);
            byte[][] captured = resolved.length == 0 ? null : batch.classBytes(resolved);
            if (captured != null && captured.length == resolved.length) {
                int next = 0;
                for (int i = 0; i < total; i++) if (types[i] != null) originals[i] = captured[next++];
            }
        }
        for (int i = 0; i < total; i++) {
            if (types[i] != null && originals[i] == null) originals[i] = classBytes.apply(types[i]);
        }
        long captured = System.nanoTime();

        // 3. ClassTransform, one class at a time (its mapper is not thread-safe: running it on several threads corrupts its state).
        // A result that is already in the disk cache skips the transformation, which is where nearly all of the time goes.
        InPlaceTransformer.Result[] results = new InPlaceTransformer.Result[total];
        TransformCache cache = TransformCache.open();
        int cacheHits = 0;
        for (int i = 0; i < total; i++) {
            if (types[i] == null) continue;
            String target = targets.get(i);
            if (originals[i] == null) {
                fail(report, target, new IllegalStateException("no class bytes"));
                continue;
            }
            try {
                byte[] hit = cache == null ? null : cache.get(target, originals[i]);
                if (hit != null) {
                    results[i] = new InPlaceTransformer.Result(hit, Map.of(), 0);
                    cacheHits++;
                    continue;
                }
                results[i] = transformer.transform(target, originals[i]);
                if (cache != null && results[i].helpers().isEmpty()) cache.put(target, originals[i], results[i].target());
            } catch (Throwable t) {
                fail(report, target, t);
            }
        }
        long transformed = System.nanoTime();

        // 4. helper classes first, then redefine everything in one call (all or nothing); on an error redefine one by one
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (results[i] == null) continue;
            try {
                for (byte[] helper : results[i].helpers().values()) Definer.define(helper);
                ready.add(i);
            } catch (Throwable t) {
                fail(report, targets.get(i), t);
            }
        }
        boolean redefinedAll = false;
        if (batch != null && !ready.isEmpty()) {
            Class<?>[] batchTypes = ready.stream().map(i -> types[i]).toArray(Class<?>[]::new);
            byte[][] batchBytes = ready.stream().map(i -> results[i].target()).toArray(byte[][]::new);
            redefinedAll = batch.redefine(batchTypes, batchBytes) == 0;
        }
        for (int i : ready) {
            try {
                if (!redefinedAll) {
                    int code = redefine.applyAsInt(types[i], results[i].target());
                    if (code != 0) throw new IllegalStateException("redefine returned JVMTI error " + code);
                }
                ORIGINALS.put(types[i], originals[i]);
                report.redefined++;
                report.moved += results[i].movedMethods();
            } catch (Throwable t) {
                fail(report, targets.get(i), t);
            }
        }
        long end = System.nanoTime();
        log(String.format("timing: bytes %d ms, transform %d ms (%d of %d from cache), redefine %d ms%s",
                (captured - start) / 1_000_000, (transformed - captured) / 1_000_000, cacheHits, total, (end - transformed) / 1_000_000,
                batch == null ? " [per-class natives]" : redefinedAll ? "" : " [batch redefine refused, per-class]"));
        return report;
    }

    private static void fail(Report report, String target, Throwable t) {
        report.failed.add(target);
        log("hook failed for " + target + ": " + t);
        t.printStackTrace(System.out);
    }

    /** True when this client was injected, i.e. hooks were applied through JVMTI and can be taken out again. */
    public static boolean isInjected() {
        return !ORIGINALS.isEmpty();
    }

    /**
     * Self destruct, part 1: redefines every hooked class back to its original bytes so no game code references the client any
     * more. Returns the number of classes that could not be restored.
     */
    public static int uninstall() {
        int failed = 0;
        for (Map.Entry<Class<?>, byte[]> entry : ORIGINALS.entrySet()) {
            try {
                int code = NativeBridge.redefine(entry.getKey(), entry.getValue());
                if (code != 0) throw new IllegalStateException("JVMTI error " + code);
                ORIGINALS.remove(entry.getKey());
            } catch (Throwable t) {
                failed++;
                log("could not restore " + entry.getKey().getName() + ": " + t);
            }
        }
        log("hooks removed" + (failed == 0 ? "" : ", " + failed + " classes could not be restored"));
        return failed;
    }

    private static void initClient() {
        try {
            new Prestige().onInitialize();
            new Entry().onInitializeClient();
            log("client initialised");
        } catch (Throwable t) {
            log("client init failed: " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void log(String message) {
        System.out.println("[Prestige-Inject] " + message);
        try {
            NativeBridge.log(message);
        } catch (UnsatisfiedLinkError ignored) {
            // not injected (dev run): the console line above is enough
        }
    }
}

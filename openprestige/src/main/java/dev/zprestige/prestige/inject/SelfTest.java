package dev.zprestige.prestige.inject;

import dev.zprestige.prestige.engine.EnginePlugin;
import dev.zprestige.prestige.hooks.Definer;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.lenni0451.classtransform.TransformerManager;

/**
 * Dev check of the in-place transformer without the native library: transforms the pristine class files of every hook target,
 * defines the generated helper classes (verifies them and resolves their accessors) and links the transformed target in an
 * isolated class loader (verifies it). Run through the engine dev command "injectselftest".
 */
public final class SelfTest {
    private SelfTest() {}

    public static String run() throws Exception {
        String namespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
        List<String> transformers = new ArrayList<>(EnginePlugin.lines("prestige-engine/transformers.list"));
        transformers.addAll(EnginePlugin.lines("prestige-engine/inject-transformers.list"));
        TransformerManager manager = EnginePlugin.createManager(namespace, transformers);
        ClassLoader loader = SelfTest.class.getClassLoader();
        InPlaceTransformer transformer = new InPlaceTransformer(manager, loader);

        StringBuilder out = new StringBuilder("injectselftest namespace=" + namespace + " targets=" + manager.getTransformedClasses().size());
        int ok = 0, moved = 0;
        for (String target : manager.getTransformedClasses()) {
            try {
                byte[] original;
                try (InputStream in = loader.getResourceAsStream(target.replace('.', '/') + ".class")) {
                    if (in == null) throw new IllegalStateException("no class resource");
                    original = in.readAllBytes();
                }
                InPlaceTransformer.Result result = transformer.transform(target, original);
                byte[] raw = manager.transform(target, original, true);
                out.append("\n  ").append(target).append(" original=").append(original.length).append(" ct=").append(raw == null ? -1 : raw.length)
                    .append(" result=").append(result.target().length).append(" moved=").append(result.movedMethods());
                for (var helper : result.helpers().entrySet()) {
                    Definer.define(helper.getValue());
                    Class.forName(helper.getKey(), true, loader);
                }
                Class<?> linked = new Isolated(loader, target, result.target()).loadClass(target);
                for (Method ignored : linked.getDeclaredMethods()) { /* forces linking, i.e. bytecode verification */ }
                ok++;
                moved += result.movedMethods();
            } catch (Throwable t) {
                out.append("\n  FAIL ").append(target).append(": ").append(t);
                t.printStackTrace(System.out);
            }
        }
        return out.append("\n  ok=").append(ok).append(" movedHandlers=").append(moved).toString();
    }

    /** Defines the transformed target itself; everything else comes from the parent. */
    private static final class Isolated extends ClassLoader {
        private final String target;
        private final byte[] bytes;

        Isolated(ClassLoader parent, String target, byte[] bytes) {
            super(parent);
            this.target = target;
            this.bytes = bytes;
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(target)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                return c != null ? c : defineClass(name, bytes, 0, bytes.length);
            }
        }
    }
}

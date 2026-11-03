package dev.zprestige.prestige.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.FailStrategy;
import net.lenni0451.classtransform.utils.tree.IClassProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the ClassTransform transformers of the Prestige client (the original native loader installed them through an agent)
 * to the Minecraft classes, from inside Sponge Mixin: for every target class one anchor mixin (dev.zprestige.prestige.engine.mixin.N../I..)
 * makes Mixin call {@link #preApply}, which runs the ClassTransform transformation on the live Mixin ClassNode.
 *
 * Namespaces: the transformer classes reference Minecraft by Yarn names in strings (annotation values) and by the names of the
 * jar variant in bytecode. In the Gradle dev client the runtime names are Yarn ("named": the mapper is void), in a production
 * install they are Intermediary (the bundled Yarn tiny maps the Yarn strings to Intermediary).
 */
public final class EnginePlugin implements IMixinConfigPlugin {
    private static final String HOOKS = "Lnet/lenni0451/classtransform/annotations/injection/";
    private TransformerManager manager;
    private String namespace = "named";
    private int expectedTargets;

    /**
     * The namespace the running classes are named in, which selects the target list and the mapping used by the transformers.
     * Fabric reports it directly. Lunar's Genesis loader reports another namespace, but its classes carry intermediary names
     * (its own stack traces show net.minecraft.class_310.method_55608), so intermediary is used there. Without this, the mapper
     * resolved some names through the official namespace and others through the named one, which broke the hooks.
     */
    public static String runtimeNamespace() {
        String forced = System.getProperty("prestige.engine.namespace");   // test override ("named" / "intermediary")
        if (forced != null) return forced;
        String reported;
        try {
            reported = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
        } catch (Throwable t) {
            return "named";
        }
        if (reported.equals("named") || reported.equals("intermediary")) return reported;
        System.out.println("[Engine] runtime reports namespace '" + reported + "'; the classes use intermediary names, so using intermediary");
        return "intermediary";
    }

    @Override public void onLoad(String mixinPackage) {
        try {
            namespace = runtimeNamespace();
            List<String> transformers = lines("prestige-engine/transformers.list");
            expectedTargets = lines(namespace.equals("named") ? "prestige-engine/targets.named.list" : "prestige-engine/targets.intermediary.list").size();
            manager = createManager(namespace, transformers);
            if (manager.getTransformedClasses().size() != expectedTargets)
                throw new IllegalStateException("Expected " + expectedTargets + " Minecraft targets, got " + manager.getTransformedClasses().size());
            EngineState.installed = true;
            System.out.println("[Engine] namespace=" + namespace + " REGISTERED=" + transformers.size() + " TARGETS=" + expectedTargets);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare the Prestige game hooks", e);
        }
    }

    /** Builds the ClassTransform manager for the given runtime namespace ("named" or "intermediary") and transformer class names. */
    public static TransformerManager createManager(String namespace, List<String> transformers) throws IOException {
        ClassLoader loader = EnginePlugin.class.getClassLoader();
        byte[] tiny = namespace.equals("named") ? null : resource("prestige-engine/mappings.tiny");
        TransformerManager manager = new TransformerManager(new ResourceClassProvider(loader, new HashSet<>(transformers), tiny), tiny == null ? null : new ByteArrayInputStream(tiny));
        // A transformer whose target method does not exist in this runtime (Lunar's Genesis loader names some methods differently
        // from the bundled mappings) is skipped on its own. CANCEL would drop every hook of the whole class and abort the injection.
        manager.setFailStrategy(FailStrategy.CONTINUE);
        for (String name : transformers) manager.addTransformer(name);
        return manager;
    }

    /** Class bytes from the mod class path; the callbacks (void CInject/CModifyArgs handlers) get the readiness gate on the way. */
    private static final class ResourceClassProvider implements IClassProvider {
        private final ClassLoader loader;
        private final Set<String> transformers;
        /** Named (Yarn) class name -> intermediary name, from the bundled mapping. Lunar's jar only has the intermediary files. */
        private final Map<String, String> namedToIntermediary;
        ResourceClassProvider(ClassLoader loader, Set<String> transformers, byte[] tiny) {
            this.loader = loader;
            this.transformers = transformers;
            this.namedToIntermediary = classNames(tiny);
        }

        private static Map<String, String> classNames(byte[] tiny) {
            Map<String, String> out = new HashMap<>();
            if (tiny == null) return out;
            for (String line : new String(tiny, StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\t");
                // header "tiny 2 0 intermediary named"; class lines "c <intermediary> <named>"
                if (f.length >= 3 && f[0].equals("c")) out.put(f[2].trim(), f[1].trim());
            }
            return out;
        }

        @Override public byte[] getClass(String name) throws ClassNotFoundException {
            String slashed = name.replace('.', '/');
            InputStream in = loader.getResourceAsStream(slashed + ".class");
            if (in == null) {
                // a named name the runtime does not have: load the intermediary class it maps to
                String intermediary = namedToIntermediary.get(slashed);
                if (intermediary != null) {
                    slashed = intermediary;
                    in = loader.getResourceAsStream(slashed + ".class");
                }
            }
            if (in == null) throw new ClassNotFoundException(name + " (missing resource " + slashed + ".class)");
            try (InputStream stream = in) {
                byte[] bytes = stream.readAllBytes();
                return transformers.contains(slashed.replace('/', '.')) ? gate(bytes) : bytes;
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override public Map<String, Supplier<byte[]>> getAllClasses() {
            throw new UnsupportedOperationException("Wildcard class enumeration is not used by the engine");
        }

        /** Prepends "if (!EngineState.ready) return; EngineState.onCallback(id);" to every void callback. */
        private static byte[] gate(byte[] bytes) {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int count = 0;
            for (MethodNode method : node.methods) {
                if (!hasHook(method)) continue;
                if (Type.getReturnType(method.desc).getSort() != Type.VOID) {
                    System.out.println("[Engine] note: non-void callback not gated: " + node.name + "." + method.name);
                    continue;
                }
                InsnList gate = new InsnList();
                LabelNode run = new LabelNode();
                gate.add(new FieldInsnNode(Opcodes.GETSTATIC, "dev/zprestige/prestige/engine/EngineState", "ready", "Z"));
                gate.add(new JumpInsnNode(Opcodes.IFNE, run));
                gate.add(new InsnNode(Opcodes.RETURN));
                gate.add(run);
                gate.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                gate.add(new LdcInsnNode(node.name.substring(node.name.lastIndexOf('/') + 1) + "." + method.name));
                gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/zprestige/prestige/engine/EngineState", "onCallback", "(Ljava/lang/String;)V", false));
                method.instructions.insert(gate);
                count++;
            }
            if (count == 0) return bytes;
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        }

        private static boolean hasHook(MethodNode m) {
            return hasHook(m.visibleAnnotations) || hasHook(m.invisibleAnnotations);
        }

        private static boolean hasHook(List<AnnotationNode> list) {
            if (list == null) return false;
            for (AnnotationNode a : list)
                if (a.desc.equals(HOOKS + "CInject;") || a.desc.equals(HOOKS + "CModifyArgs;")) return true;
            return false;
        }
    }

    public static byte[] resource(String name) throws IOException {
        try (InputStream in = EnginePlugin.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("Missing engine resource: " + name);
            return in.readAllBytes();
        }
    }

    public static List<String> lines(String name) throws IOException {
        return new String(resource(name), StandardCharsets.UTF_8).lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override public void preApply(String targetName, ClassNode target, String mixin, IMixinInfo info) {
        if (!manager.getTransformedClasses().contains(targetName)) throw new IllegalStateException("Unexpected target: " + targetName);
        if (!EngineState.transformed.add(targetName)) throw new IllegalStateException("Duplicate transformation: " + targetName);

        // ClassTransform parses the bytes it is given; hand it a copy but let its output land in the LIVE Mixin node, so the method
        // nodes Mixin (and MixinExtras) already reference stay valid.
        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        ClassNode[] liveTarget = {target};
        manager.addRawTransformer(targetName, (engine, parsed) -> {
            if (liveTarget[0] == null) return parsed;
            if (!parsed.name.equals(liveTarget[0].name)) throw new IllegalStateException("Unexpected target for " + targetName);
            ClassNode live = liveTarget[0];
            liveTarget[0] = null;
            return live;
        });
        try {
            byte[] verified = manager.transform(targetName, writer.toByteArray(), true);
            if (verified == null) throw new IllegalStateException("Transformation failed for " + targetName + "; see ClassTransform diagnostics");
            // the transformed live node needs new max stack/locals: take them from the re-serialised class
            ClassNode calculated = new ClassNode();
            new ClassReader(verified).accept(calculated, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : target.methods) {
                MethodNode sizing = calculated.methods.stream().filter(m -> m.name.equals(method.name) && m.desc.equals(method.desc)).findFirst().orElseThrow();
                method.maxLocals = sizing.maxLocals;
                method.maxStack = sizing.maxStack;
            }
        } finally {
            liveTarget[0] = null;
        }
        System.out.println("[Engine] TRANSFORMED=" + targetName);
    }

    /** The anchor mixins for the runtime namespace (Yarn "N" set in the dev client, Intermediary "I" set in production). */
    @Override public List<String> getMixins() {
        String prefix = namespace.equals("named") ? "N" : "I";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < expectedTargets; i++) out.add(String.format("%s%02d", prefix, i));
        return out;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

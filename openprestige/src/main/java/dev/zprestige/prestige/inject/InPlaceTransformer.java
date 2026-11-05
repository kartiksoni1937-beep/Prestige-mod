package dev.zprestige.prestige.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.lenni0451.classtransform.TransformerManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Runs the ClassTransform hooks on already loaded classes without changing their shape.
 *
 * ClassTransform copies every hook handler into the target class. A running JVM refuses to redefine a class with added methods,
 * so the handlers are moved into a generated helper class and the call sites in the target become invokestatic calls to it
 * (an instance handler receives the target instance as its first argument, which keeps the local variable layout identical).
 * Accesses from a moved handler to members it can no longer reach directly (private fields and methods of the target) go through
 * generated accessors backed by {@link Access}.
 */
public final class InPlaceTransformer {
    public static final String HOOK_PACKAGE = "dev/zprestige/prestige/hooks/";
    private static final String MH = "java/lang/invoke/MethodHandle";

    /** The class to redefine plus the helper classes that must be defined (by name, in order) before it runs. */
    public record Result(byte[] target, Map<String, byte[]> helpers, int movedMethods) {}

    private final TransformerManager manager;
    private final ClassLoader loader;
    private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();

    public InPlaceTransformer(TransformerManager manager, ClassLoader loader) {
        this.manager = manager;
        this.loader = loader;
    }

    /** @param dottedName runtime name of the target class, @param current the bytes of the class as currently loaded */
    public Result transform(String dottedName, byte[] current) throws Exception {
        byte[] out = manager.transform(dottedName, current, true);
        if (out == null) throw new IllegalStateException("ClassTransform failed for " + dottedName);
        ClassNode before = read(current);
        ClassNode after = read(out);

        Set<String> beforeFields = new HashSet<>();
        before.fields.forEach(f -> beforeFields.add(f.name + ":" + f.desc));
        for (var f : after.fields)
            if (!beforeFields.contains(f.name + ":" + f.desc))
                throw new IllegalStateException(dottedName + ": transformer added field " + f.name + " (cannot be redefined in place)");

        Map<String, MethodNode> existing = new HashMap<>();
        before.methods.forEach(m -> existing.put(m.name + m.desc, m));
        Map<String, MethodNode> moved = new LinkedHashMap<>();
        for (MethodNode m : after.methods) if (!existing.containsKey(m.name + m.desc)) moved.put(m.name + m.desc, m);
        if (moved.isEmpty()) return new Result(out, Map.of(), 0);
        if (after.interfaces.size() != before.interfaces.size())
            throw new IllegalStateException(dottedName + ": transformer changed the interface list");

        String helperName = HOOK_PACKAGE + "H" + (sequence.getAndIncrement()) + "_" + after.name.substring(after.name.lastIndexOf('/') + 1).replace('$', '_');
        ClassNode helper = new ClassNode();
        helper.version = after.version;
        helper.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        helper.name = helperName;
        helper.superName = "java/lang/Object";

        Map<String, Boolean> movedInstance = new HashMap<>();
        for (MethodNode m : moved.values()) movedInstance.put(m.name + m.desc, (m.access & Opcodes.ACC_STATIC) == 0);

        for (MethodNode m : moved.values()) {
            boolean instance = movedInstance.get(m.name + m.desc);
            String desc = instance ? "(L" + after.name + ";" + m.desc.substring(1) : m.desc;
            MethodNode copy = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, m.name, desc, null, m.exceptions.toArray(new String[0]));
            m.accept(copy);
            copy.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            copy.desc = desc;
            copy.visibleAnnotations = null;
            copy.invisibleAnnotations = null;
            copy.visibleParameterAnnotations = null;
            copy.invisibleParameterAnnotations = null;
            copy.visibleTypeAnnotations = null;
            copy.invisibleTypeAnnotations = null;
            helper.methods.add(copy);
        }

        Accessors accessors = new Accessors(helper);
        for (MethodNode m : helper.methods) rewrite(m, after.name, helperName, movedInstance, accessors, true, dottedName);
        for (MethodNode m : after.methods)
            if (!moved.containsKey(m.name + m.desc)) rewrite(m, after.name, helperName, movedInstance, accessors, false, dottedName);
        accessors.finish();
        after.methods.removeIf(m -> moved.containsKey(m.name + m.desc));

        ClassWriter targetWriter = new ClassWriter(0);
        after.accept(targetWriter);
        ClassWriter helperWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        helper.accept(helperWriter);
        Map<String, byte[]> helpers = new LinkedHashMap<>();
        helpers.put(helperName.replace('/', '.'), helperWriter.toByteArray());
        return new Result(targetWriter.toByteArray(), helpers, moved.size());
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private void rewrite(MethodNode method, String targetName, String helperName, Map<String, Boolean> moved, Accessors accessors,
                         boolean inHelper, String context) throws ReflectiveOperationException {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof MethodInsnNode call) {
                Boolean instance = call.owner.equals(targetName) ? moved.get(call.name + call.desc) : null;
                if (instance != null) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = helperName;
                    call.itf = false;
                    if (instance) call.desc = "(L" + targetName + ";" + call.desc.substring(1);
                } else if (inHelper && !call.name.equals("<init>")) {
                    if (call.getOpcode() == Opcodes.INVOKESPECIAL && !call.owner.equals(targetName))
                        throw new IllegalStateException(context + ": handler " + method.name + " calls super method " + call.owner + "." + call.name);
                    if (!accessible(call.owner, call.name, call.desc, true))
                        accessors.route(method, call, call.getOpcode() == Opcodes.INVOKESTATIC ? Access.STATIC : Access.VIRTUAL, helperName);
                }
            } else if (inHelper && insn instanceof FieldInsnNode field) {
                if (!accessible(field.owner, field.name, field.desc, false)) {
                    int kind = switch (field.getOpcode()) {
                        case Opcodes.GETFIELD -> Access.GET_FIELD;
                        case Opcodes.PUTFIELD -> Access.PUT_FIELD;
                        case Opcodes.GETSTATIC -> Access.GET_STATIC;
                        default -> Access.PUT_STATIC;
                    };
                    accessors.route(method, field, kind, helperName);
                }
            }
            insn = next;
        }
    }

    /** True when code in another package can name the member directly: public member, public declaring class and public named owner. */
    private boolean accessible(String ownerInternal, String name, String desc, boolean method) throws ReflectiveOperationException {
        if (ownerInternal.startsWith("[")) return true;
        Class<?> owner;
        try {
            owner = Class.forName(ownerInternal.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException e) {
            return true;   // not a game class we can inspect; leave it for the verifier
        }
        if (!Modifier.isPublic(owner.getModifiers())) return false;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            if (method) {
                for (Method m : c.getDeclaredMethods())
                    if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc))
                        return Modifier.isPublic(m.getModifiers()) && Modifier.isPublic(c.getModifiers());
            } else {
                for (Field f : c.getDeclaredFields())
                    if (f.getName().equals(name))
                        return Modifier.isPublic(f.getModifiers()) && Modifier.isPublic(c.getModifiers());
            }
        }
        if (method) {
            for (Class<?> i : owner.getInterfaces()) if (accessible(i.getName().replace('.', '/'), name, desc, true)) return true;
        }
        return true;
    }

    /** Collects the accessor methods and the MethodHandle fields they use, and builds the helper's static initialiser. */
    private static final class Accessors {
        private final ClassNode helper;
        private final Map<String, String> byKey = new HashMap<>();
        private final List<Object[]> entries = new ArrayList<>();

        Accessors(ClassNode helper) { this.helper = helper; }

        /** Replaces the member instruction by an invokestatic of a (new or reused) accessor. */
        void route(MethodNode in, AbstractInsnNode insn, int kind, String helperName) {
            String owner, name, desc;
            if (insn instanceof FieldInsnNode f) { owner = f.owner; name = f.name; desc = f.desc; }
            else { MethodInsnNode m = (MethodInsnNode) insn; owner = m.owner; name = m.name; desc = m.desc; }
            String key = kind + ":" + owner + "." + name + desc;
            String accessor = byKey.get(key);
            if (accessor == null) {
                accessor = "acc$" + byKey.size();
                byKey.put(key, accessor);
                String accessorDesc = accessorDescriptor(kind, desc);
                MethodNode node = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, accessor, accessorDesc, null, null);
                node.visitFieldInsn(Opcodes.GETSTATIC, helperName, "mh$" + (byKey.size() - 1), "L" + MH + ";");
                int slot = 0;
                for (Type arg : Type.getArgumentTypes(accessorDesc)) {
                    node.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
                    slot += arg.getSize();
                }
                node.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MH, "invokeExact", accessorDesc, false);
                node.visitInsn(Type.getReturnType(accessorDesc).getOpcode(Opcodes.IRETURN));
                helper.methods.add(node);
                entries.add(new Object[]{byKey.size() - 1, owner.replace('/', '.'), name, desc, kind});
            }
            in.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, helperName, accessor, accessorDescriptor(kind, desc), false));
        }

        /** Descriptor of the accessor method as seen by callers (receiver stays typed Object). */
        private static String accessorDescriptor(int kind, String desc) {
            return switch (kind) {
                case Access.GET_FIELD -> "(Ljava/lang/Object;)" + desc;
                case Access.PUT_FIELD -> "(Ljava/lang/Object;" + desc + ")V";
                case Access.GET_STATIC -> "()" + desc;
                case Access.PUT_STATIC -> "(" + desc + ")V";
                case Access.VIRTUAL -> "(Ljava/lang/Object;" + desc.substring(1);
                default -> desc;
            };
        }

        void finish() {
            if (entries.isEmpty()) return;
            MethodNode clinit = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            for (Object[] e : entries) {
                String field = "mh$" + e[0];
                helper.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field, "L" + MH + ";", null, null));
                clinit.visitLdcInsn(e[1]);
                clinit.visitLdcInsn(e[2]);
                clinit.visitLdcInsn(e[3]);
                clinit.visitIntInsn(Opcodes.BIPUSH, (Integer) e[4]);
                clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/zprestige/prestige/inject/Access", "resolve",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)L" + MH + ";", false);
                clinit.visitFieldInsn(Opcodes.PUTSTATIC, helper.name, field, "L" + MH + ";");
            }
            clinit.visitInsn(Opcodes.RETURN);
            helper.methods.add(clinit);
        }
    }
}

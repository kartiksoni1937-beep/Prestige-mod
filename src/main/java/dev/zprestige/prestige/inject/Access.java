package dev.zprestige.prestige.inject;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.objectweb.asm.Type;

/**
 * Resolves the MethodHandles behind generated accessors. A hook handler that was moved out of its target class into a helper class
 * can no longer touch the target's private members directly; the transformer routes those accesses through handles made here.
 * All handles take the receiver as Object so the generated call sites can use one erased signature.
 */
public final class Access {
    public static final int GET_FIELD = 0, PUT_FIELD = 1, GET_STATIC = 2, PUT_STATIC = 3, VIRTUAL = 4, STATIC = 5;

    private Access() {}

    public static MethodHandle resolve(String owner, String name, String desc, int kind) throws ReflectiveOperationException {
        Class<?> ref = Class.forName(owner, false, Access.class.getClassLoader());
        Class<?> decl = declaring(ref, name, desc, kind);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(decl, MethodHandles.lookup());
        switch (kind) {
            case GET_FIELD: {
                Class<?> t = fieldType(decl, name);
                return lookup.findGetter(decl, name, t).asType(MethodType.methodType(t, Object.class));
            }
            case PUT_FIELD: {
                Class<?> t = fieldType(decl, name);
                return lookup.findSetter(decl, name, t).asType(MethodType.methodType(void.class, Object.class, t));
            }
            case GET_STATIC:
                return lookup.findStaticGetter(decl, name, fieldType(decl, name));
            case PUT_STATIC:
                return lookup.findStaticSetter(decl, name, fieldType(decl, name));
            case VIRTUAL: {
                MethodType type = MethodType.fromMethodDescriptorString(desc, Access.class.getClassLoader());
                return lookup.findVirtual(decl, name, type).asType(type.insertParameterTypes(0, Object.class));
            }
            default:
                return lookup.findStatic(decl, name, MethodType.fromMethodDescriptorString(desc, Access.class.getClassLoader()));
        }
    }

    private static Class<?> fieldType(Class<?> decl, String name) throws NoSuchFieldException {
        return decl.getDeclaredField(name).getType();
    }

    /** The class in the hierarchy of ref that actually declares the member (JVM resolution walks the superclasses). */
    private static Class<?> declaring(Class<?> ref, String name, String desc, int kind) throws ReflectiveOperationException {
        boolean field = kind <= PUT_STATIC;
        for (Class<?> c = ref; c != null; c = c.getSuperclass()) {
            if (field) {
                for (var f : c.getDeclaredFields()) if (f.getName().equals(name)) return c;
            } else {
                for (var m : c.getDeclaredMethods()) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return c;
            }
        }
        throw field ? new NoSuchFieldException(ref.getName() + "." + name) : new NoSuchMethodException(ref.getName() + "." + name + desc);
    }
}

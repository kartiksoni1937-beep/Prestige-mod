package dev.zprestige.prestige.reflect;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;

public abstract class ReflectionBase {
    public static final Unsafe UNSAFE;
    private static final Map RESOLVED_FIELDS = new ConcurrentHashMap();
    private static final Map RESOLVED_METHODS = new ConcurrentHashMap();

    public static AccessibleObject forceAccessible(AccessibleObject member) {
        try {
            member.setAccessible(true);
            return member;
        } catch (Throwable accessError) {
            if (UNSAFE == null) {
                throw accessError;
            }

            try {
                UNSAFE.putBoolean(member, 12L, true);
            } catch (Throwable ignored) {
            }

            return member;
        }
    }

    public static void removeFinalModifier(Field field) {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            int modifiers = field.getModifiers();
            if ((modifiers & 16) != 0) {
                modifiersField.setInt(field, modifiers & -17);
            }
        } catch (Throwable ignored) {
        }
    }

    public static ResolvedField resolveDeclaredField(Class owner, String name) {
        String cacheKey = owner.getName() + "#" + name;
        ResolvedField resolved = (ResolvedField)RESOLVED_FIELDS.get(cacheKey);
        if (resolved != null) {
            return resolved;
        } else {
            try {
                Field field = owner.getDeclaredField(name);
                forceAccessible(field);
                removeFinalModifier(field);
                boolean isStatic = Modifier.isStatic(field.getModifiers());
                VarHandle varHandle = null;

                try {
                    Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                    if (isStatic) {
                        varHandle = lookup.findStaticVarHandle(owner, name, field.getType());
                    } else {
                        varHandle = lookup.findVarHandle(owner, name, field.getType());
                    }
                } catch (Throwable ignored) {
                }

                if (UNSAFE == null) {
                    resolved = new ResolvedField(field, isStatic, null, -1L, varHandle);
                } else if (isStatic) {
                    resolved = new ResolvedField(field, true, UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field), varHandle);
                } else {
                    resolved = new ResolvedField(field, false, null, UNSAFE.objectFieldOffset(field), varHandle);
                }

                RESOLVED_FIELDS.put(cacheKey, resolved);
                return resolved;
            } catch (NoSuchFieldException notFound) {
                throw new RuntimeException("Field not found: " + owner + "." + name, notFound);
            }
        }
    }

    public static ResolvedField resolveFieldByDescriptor(Class owner, String name, String descriptor) {
        String cacheKey = owner.getName() + "#" + name + ":" + descriptor;
        ResolvedField resolved = (ResolvedField)RESOLVED_FIELDS.get(cacheKey);
        if (resolved != null) {
            return resolved;
        } else {
            Class currentClass = owner;

            Class expectedType;
            try {
                expectedType = parseFieldDescriptor(descriptor, owner.getClassLoader());
            } catch (ClassNotFoundException parseError) {
                throw new RuntimeException("Failed to parse field descriptor: " + descriptor, parseError);
            }

            while (currentClass != null) {
                try {
                    Field field = currentClass.getDeclaredField(name);
                    if (!field.getType().equals(expectedType)) {
                        throw new RuntimeException("Field type mismatch for " + String.valueOf(currentClass) + "." + name + ": expected " + String.valueOf(expectedType) + " but found " + String.valueOf(field.getType()));
                    }

                    forceAccessible(field);
                    removeFinalModifier(field);
                    boolean isStatic = Modifier.isStatic(field.getModifiers());
                    VarHandle varHandle = null;

                    try {
                        Lookup lookup = MethodHandles.privateLookupIn(currentClass, MethodHandles.lookup());
                        if (isStatic) {
                            varHandle = lookup.findStaticVarHandle(currentClass, name, field.getType());
                        } else {
                            varHandle = lookup.findVarHandle(currentClass, name, field.getType());
                        }
                    } catch (Throwable ignored) {
                    }

                    if (UNSAFE == null) {
                        resolved = new ResolvedField(field, isStatic, null, -1L, varHandle);
                    } else if (isStatic) {
                        resolved = new ResolvedField(field, true, UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field), varHandle);
                    } else {
                        resolved = new ResolvedField(field, false, null, UNSAFE.objectFieldOffset(field), varHandle);
                    }

                    RESOLVED_FIELDS.put(cacheKey, resolved);
                    return resolved;
                } catch (NoSuchFieldException notFound) {
                    currentClass = currentClass.getSuperclass();
                }
            }

            throw new RuntimeException("Field not found: " + owner + "." + name);
        }
    }

    public static ResolvedMethod resolveMethod(Class owner, String name, Class[] parameterTypes) {
        String cacheKey = owner.getName() + "#" + name + ":" + buildParameterKey(parameterTypes);
        ResolvedMethod resolved = (ResolvedMethod)RESOLVED_METHODS.get(cacheKey);
        if (resolved != null) {
            return resolved;
        } else {
            for (Class currentClass = owner; currentClass != null; currentClass = currentClass.getSuperclass()) {
                try {
                    Method method = currentClass.getDeclaredMethod(name, parameterTypes);
                    forceAccessible(method);
                    resolved = new ResolvedMethod(method, MethodHandles.privateLookupIn(currentClass, MethodHandles.lookup()).unreflect(method), Modifier.isStatic(method.getModifiers()));
                    RESOLVED_METHODS.put(cacheKey, resolved);
                    return resolved;
                } catch (IllegalAccessException | NoSuchMethodException exactLookupFailure) {
                    for (Method candidate : currentClass.getDeclaredMethods()) {
                        if (candidate.getName().equals(name) && candidate.getParameterCount() == parameterTypes.length) {
                            boolean compatible = true;
                            Class[] candidateParameterTypes = candidate.getParameterTypes();

                            for (int i = 0; i < candidateParameterTypes.length; i++) {
                                if (!candidateParameterTypes[i].isAssignableFrom(parameterTypes[i])) {
                                    compatible = false;
                                    break;
                                }
                            }

                            if (compatible) {
                                try {
                                    forceAccessible(candidate);
                                    resolved = new ResolvedMethod(candidate, MethodHandles.privateLookupIn(currentClass, MethodHandles.lookup()).unreflect(candidate), Modifier.isStatic(candidate.getModifiers()));
                                    RESOLVED_METHODS.put(cacheKey, resolved);
                                    return resolved;
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }
            }

            throw new RuntimeException("Method not found: " + String.valueOf(owner) + "." + name + Arrays.toString((Object[])parameterTypes));
        }
    }

    private static String buildParameterKey(Class[] types) {
        if (types != null && types.length != 0) {
            StringBuilder builder = new StringBuilder();

            for (Class type : types) {
                builder.append(type == null ? "null" : type.getName()).append(',');
            }

            return builder.toString();
        } else {
            return "()";
        }
    }

    public static Class parseFieldDescriptor(String descriptor, ClassLoader classLoader) throws ClassNotFoundException {
        if (descriptor == null) {
            throw new IllegalArgumentException("Descriptor required");
        } else {
            int index = 0;

            int arrayDepth;
            for (arrayDepth = 0; index < descriptor.length() && descriptor.charAt(index) == '['; index++) {
                arrayDepth++;
            }

            char typeChar = descriptor.charAt(index);
            if (typeChar == 'L') {
                Class elementClass = Class.forName(descriptor.substring(index + 1, descriptor.indexOf(59, index)).replace('/', '.'), false, classLoader);
                return toArrayType(elementClass, arrayDepth);
            } else {
                return toArrayType(primitiveForDescriptor(Integer.valueOf(typeChar)), arrayDepth);
            }
        }
    }

    private static Class toArrayType(Class componentType, int dimensions) {
        if (dimensions == 0) {
            return componentType;
        } else {
            Class arrayType = componentType;

            for (int i = 0; i < dimensions; i++) {
                arrayType = Array.newInstance(arrayType, 0).getClass();
            }

            return arrayType;
        }
    }

    private static Class primitiveForDescriptor(int descriptorChar) {
        switch (descriptorChar) {
            case 66:
                return byte.class;
            case 67:
                return char.class;
            case 68:
                return double.class;
            case 69:
            case 71:
            case 72:
            case 75:
            case 76:
            case 77:
            case 78:
            case 79:
            case 80:
            case 81:
            case 82:
            case 84:
            case 85:
            case 87:
            case 88:
            case 89:
            default:
                throw new IllegalArgumentException("Unknown primitive descriptor: " + descriptorChar);
            case 70:
                return float.class;
            case 73:
                return int.class;
            case 74:
                return long.class;
            case 83:
                return short.class;
            case 86:
                return void.class;
            case 90:
                return boolean.class;
        }
    }

    static {
        Unsafe unsafeInstance = null;

        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafeInstance = (Unsafe)theUnsafeField.get(null);
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }

        UNSAFE = unsafeInstance;
    }
}

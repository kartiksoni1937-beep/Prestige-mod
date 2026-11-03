package dev.zprestige.prestige.reflect;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.ASMUtils;

public class FieldAccessor extends ReflectionBase {
    private final Field field;
    private final boolean isStaticField;
    private final Object staticBase;
    private final long fieldOffset;
    private final VarHandle varHandle;
    private final Object boundInstance;
    private static final ConcurrentHashMap MAPPED_FIELD_ACCESSORS = new ConcurrentHashMap();

    private FieldAccessor(Field reflectField, boolean staticField, Object base, long offset, VarHandle handle, Object instance) {
        this.field = reflectField;
        this.isStaticField = staticField;
        this.staticBase = base;
        this.fieldOffset = offset;
        this.varHandle = handle;
        this.boundInstance = instance;
    }

    private FieldAccessor(ResolvedField resolved, Object instance) {
        this(resolved.field, resolved.isStatic, resolved.staticBase, resolved.offset, resolved.varHandle, instance);
    }

    public static FieldAccessor of(Class owner, String name) {
        return new FieldAccessor(ReflectionBase.resolveDeclaredField(owner, name), null);
    }

    public static BoundField bindMapped(Object instance, String name, Class owner, Class type) {
        Class runtimeClass = instance.getClass();
        FieldCacheKey cacheKey = new FieldCacheKey(owner, name, type, runtimeClass);
        FieldAccessor accessor = (FieldAccessor)MAPPED_FIELD_ACCESSORS.get(cacheKey);
        if (accessor == null) {
            String runtimeDescriptor = ASMUtils.toFieldDescriptor(type);
            String namedOwner = TransformerManager.getRemapper().getRemapper().reverse().map(owner.getName().replace('.', '/'));
            String namedDescriptor = TransformerManager.getRemapper().getRemapper().reverse().mapDesc(runtimeDescriptor);
            accessor = new FieldAccessor(ReflectionBase.resolveFieldByDescriptor(runtimeClass, TransformerManager.getRemapper().getRemapper().mapFieldName(namedOwner, name, namedDescriptor), runtimeDescriptor), null);
            FieldAccessor existing = (FieldAccessor)MAPPED_FIELD_ACCESSORS.putIfAbsent(cacheKey, accessor);
            if (existing != null) {
                accessor = existing;
            }
        }

        return accessor.bind(instance);
    }

    public Field getField() {
        return this.field;
    }

    public Object get(Object target) {
        if (!this.isStaticField && target == null) {
            throw new NullPointerException("target cannot be null for instance field " + this.field);
        }

        try {
            if (UNSAFE != null) {
                return this.unsafeGet(target);
            } else if (this.varHandle != null) {
                return this.isStaticField ? this.varHandle.get() : this.varHandle.get(target);
            } else {
                return this.isStaticField ? this.field.get(null) : this.field.get(target);
            }
        } catch (IllegalAccessException accessError) {
            throw new RuntimeException(accessError);
        }
    }

    public void set(Object target, Object value) {
        if (!this.isStaticField && target == null) {
            throw new NullPointerException("target cannot be null for instance field " + this.field);
        }

        if (this.isStaticField) {
            if (UNSAFE != null) {
                this.unsafeSet(null, value);
            } else if (this.varHandle != null) {
                this.varHandle.set(value);
            } else {
                try {
                    this.field.set(null, value);
                } catch (IllegalAccessException staticAccessError) {
                    throw new RuntimeException(staticAccessError);
                }
            }
        } else if (UNSAFE != null) {
            this.unsafeSet(target, value);
        } else if (this.varHandle != null) {
            this.varHandle.set(target, value);
        } else {
            try {
                this.field.set(target, value);
            } catch (IllegalAccessException instanceAccessError) {
                throw new RuntimeException(instanceAccessError);
            }
        }
    }

    public void set(Object value) {
        if (!this.isStaticField) {
            if (this.boundInstance == null) {
                throw new IllegalStateException("set(value) without target only valid for static fields or when bound to an instance");
            } else {
                this.set(this.boundInstance, value);
            }
        } else {
            this.set(null, value);
        }
    }

    private Object unsafeGet(Object target) {
        Class type = this.field.getType();
        boolean primitive = type.isPrimitive();
        Object base = this.isStaticField ? this.staticBase : target;
        if (!primitive) {
            return UNSAFE.getObject(base, this.fieldOffset);
        } else if (type == int.class) {
            return UNSAFE.getInt(base, this.fieldOffset);
        } else if (type == long.class) {
            return UNSAFE.getLong(base, this.fieldOffset);
        } else if (type == boolean.class) {
            return UNSAFE.getBoolean(base, this.fieldOffset);
        } else if (type == byte.class) {
            return UNSAFE.getByte(base, this.fieldOffset);
        } else if (type == char.class) {
            return UNSAFE.getChar(base, this.fieldOffset);
        } else if (type == short.class) {
            return UNSAFE.getShort(base, this.fieldOffset);
        } else if (type == float.class) {
            return UNSAFE.getFloat(base, this.fieldOffset);
        } else {
            return type == double.class ? UNSAFE.getDouble(base, this.fieldOffset) : UNSAFE.getObject(base, this.fieldOffset);
        }
    }

    private void unsafeSet(Object target, Object value) {
        Class type = this.field.getType();
        Object base = this.isStaticField ? this.staticBase : target;
        if (!type.isPrimitive()) {
            UNSAFE.putObject(base, this.fieldOffset, value);
        } else {
            if (type == int.class) {
                UNSAFE.putInt(base, this.fieldOffset, ((Number)value).intValue());
            } else if (type == long.class) {
                UNSAFE.putLong(base, this.fieldOffset, ((Number)value).longValue());
            } else if (type == boolean.class) {
                UNSAFE.putBoolean(base, this.fieldOffset, (Boolean)value);
            } else if (type == byte.class) {
                UNSAFE.putByte(base, this.fieldOffset, ((Number)value).byteValue());
            } else if (type == char.class) {
                UNSAFE.putChar(base, this.fieldOffset, (Character)value);
            } else if (type == short.class) {
                UNSAFE.putShort(base, this.fieldOffset, ((Number)value).shortValue());
            } else if (type == float.class) {
                UNSAFE.putFloat(base, this.fieldOffset, ((Number)value).floatValue());
            } else if (type == double.class) {
                UNSAFE.putDouble(base, this.fieldOffset, ((Number)value).doubleValue());
            }
        }
    }

    public BoundField bind(Object target) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null for instance bind");
        } else if (this.isStaticField) {
            throw new IllegalStateException("Cannot bind static field to instance; access static field via get()/set() or FieldAccessor.of(Class,..)");
        } else {
            return new BoundField(this, target);
        }
    }

    public static MappedField mappedField(Class owner, String name, Class type) {
        try {
            String runtimeDescriptor = ASMUtils.toFieldDescriptor(type);
            String namedOwner = TransformerManager.getRemapper().getRemapper().reverse().map(owner.getName().replace('.', '/'));
            String namedDescriptor = TransformerManager.getRemapper().getRemapper().reverse().mapDesc(runtimeDescriptor);
            return new MappedField(ReflectionBase.resolveFieldByDescriptor(owner, TransformerManager.getRemapper().getRemapper().mapFieldName(namedOwner, name, namedDescriptor), runtimeDescriptor));
        } catch (Throwable resolveError) {
            return new MappedField(owner, name, type);
        }
    }
}

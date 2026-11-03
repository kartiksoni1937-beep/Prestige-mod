package dev.zprestige.prestige.reflect;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public final class MappedField {
    private final boolean resolved;
    private final long offset;
    private final Object staticBase;
    private final boolean isStatic;
    private final boolean useUnsafe;
    private final VarHandle varHandle;
    private final Field field;
    private final Class owner;
    private final String name;
    private final Class type;

    public MappedField(ResolvedField resolvedField) {
        this.resolved = true;
        this.offset = resolvedField.offset;
        this.staticBase = resolvedField.staticBase;
        this.isStatic = resolvedField.isStatic;
        this.varHandle = resolvedField.varHandle;
        this.field = resolvedField.field;
        this.useUnsafe = ReflectionBase.UNSAFE != null && resolvedField.offset != -1L;
        this.owner = null;
        this.name = null;
        this.type = null;
    }

    public MappedField(Class fieldOwner, String fieldName, Class fieldType) {
        this.resolved = false;
        this.offset = -1L;
        this.staticBase = null;
        this.isStatic = false;
        this.useUnsafe = false;
        this.varHandle = null;
        this.field = null;
        this.owner = fieldOwner;
        this.name = fieldName;
        this.type = fieldType;
    }

    private Object resolveTarget(Object target) {
        if (!this.isStatic && target == null) {
            throw new NullPointerException("target cannot be null for instance field " + this.field);
        }

        return this.isStatic ? this.staticBase : target;
    }

    public void setDouble(Object target, double value) {
        if (!this.resolved) {
            this.setViaLookup(target, value);
        } else {
            if (this.useUnsafe) {
                ReflectionBase.UNSAFE.putDouble(this.resolveTarget(target), this.offset, value);
            } else if (this.varHandle != null) {
                if (this.isStatic) {
                    this.varHandle.set(value);
                } else {
                    this.varHandle.set(target, value);
                }
            } else {
                this.setReflective(target, value);
            }
        }
    }

    public void setFloat(Object target, float value) {
        if (!this.resolved) {
            this.setViaLookup(target, value);
        } else {
            if (this.useUnsafe) {
                ReflectionBase.UNSAFE.putFloat(this.resolveTarget(target), this.offset, value);
            } else if (this.varHandle != null) {
                if (this.isStatic) {
                    this.varHandle.set(value);
                } else {
                    this.varHandle.set(target, value);
                }
            } else {
                this.setReflective(target, value);
            }
        }
    }

    public void set(Object target, Object value) {
        if (!this.resolved) {
            this.setViaLookup(target, value);
        } else {
            if (this.useUnsafe) {
                ReflectionBase.UNSAFE.putObject(this.resolveTarget(target), this.offset, value);
            } else if (this.varHandle != null) {
                if (this.isStatic) {
                    this.varHandle.set(value);
                } else {
                    this.varHandle.set(target, value);
                }
            } else {
                this.setReflective(target, value);
            }
        }
    }

    public Object get(Object target) {
        if (!this.resolved) {
            return this.getViaLookup(target);
        } else if (this.useUnsafe) {
            return ReflectionBase.UNSAFE.getObject(this.resolveTarget(target), this.offset);
        } else if (this.varHandle != null) {
            return this.isStatic ? this.varHandle.get() : this.varHandle.get(target);
        } else {
            return this.getReflective(target);
        }
    }

    private void setViaLookup(Object target, Object value) {
        FieldAccessor.bindMapped(target, this.name, this.owner, this.type).set(value);
    }

    private Object getViaLookup(Object target) {
        return FieldAccessor.bindMapped(target, this.name, this.owner, this.type).get();
    }

    private void setReflective(Object target, Object value) {
        try {
            this.field.set(this.resolveTarget(target), value);
        } catch (IllegalAccessException accessError) {
            throw new RuntimeException(accessError);
        }
    }

    private Object getReflective(Object target) {
        try {
            return this.field.get(this.resolveTarget(target));
        } catch (IllegalAccessException accessError) {
            throw new RuntimeException(accessError);
        }
    }
}

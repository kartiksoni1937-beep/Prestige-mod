package dev.zprestige.prestige.reflect;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class ResolvedField {
    public final Field field;
    public final boolean isStatic;
    public final Object staticBase;
    public final long offset;
    public final VarHandle varHandle;

    public ResolvedField(Field reflectField, boolean staticField, Object base, long fieldOffset, VarHandle handle) {
        this.field = reflectField;
        this.isStatic = staticField;
        this.staticBase = base;
        this.offset = fieldOffset;
        this.varHandle = handle;
    }
}

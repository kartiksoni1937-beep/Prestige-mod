package dev.zprestige.prestige.reflect;

public class BoundField {
    private final Object target;
    public final FieldAccessor accessor;

    public BoundField(FieldAccessor fieldAccessor, Object instance) {
        this.accessor = fieldAccessor;
        this.target = instance;
    }

    public Object get() {
        return this.accessor.get(this.target);
    }

    public void set(Object value) {
        this.accessor.set(this.target, value);
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.MethodAccessor;

public class BoundMethod {
    private final Object target;
    public final MethodAccessor accessor;

    public BoundMethod(MethodAccessor methodAccessor, Object instance) {
        this.accessor = methodAccessor;
        this.target = instance;
    }

    public Object invoke(Object[] args) {
        try {
            return this.accessor.call(this.target, args);
        } catch (Throwable error) {
            throw new RuntimeException(error);
        }
    }
}

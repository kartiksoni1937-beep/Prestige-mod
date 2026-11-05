package dev.zprestige.prestige.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

public class ResolvedMethod {
    public final Method method;
    public final MethodHandle handle;
    public final boolean isStatic;

    public ResolvedMethod(Method reflectMethod, MethodHandle methodHandle, boolean staticMethod) {
        this.method = reflectMethod;
        this.handle = methodHandle;
        this.isStatic = staticMethod;
    }
}

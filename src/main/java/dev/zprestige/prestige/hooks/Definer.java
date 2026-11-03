package dev.zprestige.prestige.hooks;

import java.lang.invoke.MethodHandles;

/** Defines the generated hook helper classes of this package in the ClassLoader that also holds the game classes. */
public final class Definer {
    private Definer() {}

    public static Class<?> define(byte[] bytes) throws IllegalAccessException {
        return MethodHandles.lookup().defineClass(bytes);
    }
}

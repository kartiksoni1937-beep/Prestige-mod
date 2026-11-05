package dev.zprestige.prestige.reflect;

public record FieldCacheKey(Class<?> owner, String name, Object type, Class<?> targetClass) {
}

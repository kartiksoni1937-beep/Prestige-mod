package dev.zprestige.prestige.inject;

/** Natives registered by the injected prestige_native.dll (JVMTI): current class bytes and class redefinition. */
public final class NativeBridge {
    private NativeBridge() {}

    /** Bytes of the loaded class as the JVM currently has it (including changes made by earlier transformers). */
    public static native byte[] classBytes(Class<?> type);

    /** classBytes for many classes with one RetransformClasses call; entries are null where nothing was captured, null on failure. */
    public static native byte[][] classBytesBatch(Class<?>[] types);

    /** RedefineClasses for many classes in one call, all or nothing; returns the JVMTI error code, 0 on success. */
    public static native int redefineBatch(Class<?>[] types, byte[][] bytes);

    /** Appends a line to prestige-native.log, which the injector tails to show progress. */
    public static native void log(String message);

    /** Disables the class file load hook and unloads prestige_native.dll shortly afterwards; nothing may call the natives after this. */
    public static native void release();

    /** JVMTI RedefineClasses for one class; returns the JVMTI error code, 0 on success. */
    public static native int redefine(Class<?> type, byte[] bytes);
}

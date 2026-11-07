package dev.zprestige.prestige.util;

/**
 * The only sink for client diagnostics.
 *
 * <p>The client must not write anything into the game log, so every method here is a no-op.
 * Flip {@link #ENABLED} to {@code true} for a debug build and the messages are printed again.
 */
public final class Log {
    /** {@code false}: the client stays completely silent in the game log. */
    public static final boolean ENABLED = false;

    private Log() {
    }

    public static void info(String message) {
        if (ENABLED) {
            System.out.println("[Prestige] " + message);
        }
    }

    public static void warn(String message) {
        if (ENABLED) {
            System.out.println("[Prestige] " + message);
        }
    }

    public static void error(String message) {
        if (ENABLED) {
            System.out.println("[Prestige] " + message);
        }
    }

    public static void error(String message, Throwable error) {
        if (ENABLED) {
            System.out.println("[Prestige] " + message);
            if (error != null) {
                error.printStackTrace(System.out);
            }
        }
    }

    public static void exception(Throwable error) {
        if (ENABLED && error != null) {
            error.printStackTrace(System.out);
        }
    }
}

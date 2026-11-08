package dev.zprestige.prestige.util;

/**
 * One funnel for every diagnostic the mod can emit.
 *
 * The client has to be invisible in the game log, so {@link #OUT} is the single master switch and it
 * defaults to off: while it is off nothing in the mod writes to stdout, stderr or the game log.
 * Nothing else may print directly - route new messages through here.
 *
 * Diagnostics can be turned back on with {@code -Dprestige.debug=true}.
 */
public final class Log {
    /** Master switch. Off unless the JVM is started with -Dprestige.debug=true. */
    public static final boolean OUT = Boolean.getBoolean("prestige.debug");

    private Log() {
    }

    public static void info(String message) {
        if (OUT) {
            System.out.println(message);
        }
    }

    public static void warn(String message) {
        if (OUT) {
            System.err.println(message);
        }
    }

    public static void error(String message) {
        if (OUT) {
            System.err.println(message);
        }
    }

    public static void error(String message, Throwable error) {
        if (!OUT) {
            return;
        }
        System.err.println(message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }

    public static void throwable(Throwable error) {
        if (OUT && error != null) {
            error.printStackTrace(System.err);
        }
    }
}

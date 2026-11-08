package dev.zprestige.prestige.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zprestige.prestige.ui.bridge.Bootstrap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproduces the in-game module registration diagnostics without a Minecraft client.
 *
 * <p>Every module constructor calls {@code ui.bridge.Bootstrap.pre(this)}, which prints a
 * {@code [Prestige] <Module>: ...} line when a manifest setting cannot be rebuilt. This probe runs that same code
 * path for every module in the live manifest and groups the output per module, so a module that "flags" at startup
 * can be identified offline.
 *
 * <p>Run with {@code ./gradlew probeFlags}.
 */
public final class FlagProbe {
    public static void main(String[] args) throws Exception {
        JsonObject root;
        try (InputStream in = FlagProbe.class.getResourceAsStream("/prestige_ui/manifest_live.json")) {
            if (in == null) {
                System.out.println("manifest resource missing");
                System.exit(1);
                return;
            }
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }

        PrintStream realErr = System.err;
        Map<String, List<String>> flags = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int built = 0;

        for (var element : root.getAsJsonArray("modules")) {
            JsonObject module = element.getAsJsonObject();
            String className = module.get("class").getAsString();
            String simple = className.substring(className.lastIndexOf('.') + 1);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
            System.setErr(capture);
            try {
                Class<?> type = Class.forName(className, true, FlagProbe.class.getClassLoader());
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance();
                Bootstrap.post(instance);
                built++;
            } catch (Throwable error) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                if (needsGameRegistries(cause)) skipped.add(simple);
                else failures.add(simple + ": " + cause);
            } finally {
                System.setErr(realErr);
                capture.flush();
            }

            List<String> lines = new ArrayList<>();
            for (String line : buffer.toString(StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
            if (!lines.isEmpty()) flags.put(simple, lines);
        }

        System.out.println("built=" + built + " flagged=" + flags.size() + " failed=" + failures.size()
                + " skippedOffline=" + skipped.size());
        if (!skipped.isEmpty()) {
            System.out.println("  (needs game registries, not a startup flag: " + String.join(", ", skipped) + ")");
        }
        if (!failures.isEmpty()) {
            System.out.println("\n== construction failures ==");
            for (String failure : failures) System.out.println("  " + failure);
        }
        if (!flags.isEmpty()) {
            System.out.println("\n== flagged modules ==");
            flags.forEach((module, lines) -> {
                System.out.println("  " + module);
                for (String line : lines) System.out.println("      " + line);
            });
        }
        if (flags.isEmpty() && failures.isEmpty()) System.out.println("no module reported a problem");
        if (!failures.isEmpty() || !flags.isEmpty()) System.exit(1);
    }

    /** A module whose static initialiser touches a Minecraft registry cannot be built outside the game. */
    private static boolean needsGameRegistries(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("Not bootstrapped")) return true;
            if (t instanceof ExceptionInInitializerError) return true;
        }
        return false;
    }
}

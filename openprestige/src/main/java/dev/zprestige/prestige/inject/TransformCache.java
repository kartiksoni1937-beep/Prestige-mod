package dev.zprestige.prestige.inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Disk cache of finished ClassTransform results. The ClassTransform step is by far the slowest part of an injection, and its
 * output only depends on the payload jar and on the class bytes it is given, so a repeat injection into the same game setup can
 * skip it. Entries are keyed by SHA-256(payload jar, target name, original class bytes); a rebuilt payload or a changed modpack
 * simply misses. Only results that need no helper classes are stored.
 */
final class TransformCache {
    private static final long MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000;

    private final Path dir;
    private final byte[] payloadId;

    private TransformCache(Path dir, byte[] payloadId) {
        this.dir = dir;
        this.payloadId = payloadId;
    }

    /** The cache for the payload jar this class was loaded from, or null when it cannot be used. */
    static TransformCache open() {
        try {
            Path jar = Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar)) return null;
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "PrestigeLoader", "cache");
            Files.createDirectories(dir);
            prune(dir);
            return new TransformCache(dir, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Cached transformed bytes of {@code target}, or null. */
    byte[] get(String target, byte[] original) {
        try {
            Path file = file(target, original);
            if (!Files.isRegularFile(file)) return null;
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xCA || (bytes[1] & 0xFF) != 0xFE || (bytes[2] & 0xFF) != 0xBA || (bytes[3] & 0xFF) != 0xBE) return null;
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
            return bytes;
        } catch (Throwable t) {
            return null;
        }
    }

    void put(String target, byte[] original, byte[] transformed) {
        try {
            Path file = file(target, original);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, transformed);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable ignored) {
            // the cache is only an optimisation
        }
    }

    private Path file(String target, byte[] original) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(payloadId);
        digest.update(target.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(original);
        StringBuilder name = new StringBuilder();
        for (byte b : digest.digest()) name.append(String.format("%02x", b));
        return dir.resolve(name + ".class");
    }

    /** Drops entries that have not been used for two weeks (every rebuild of the payload starts a fresh set). */
    private static void prune(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            long limit = System.currentTimeMillis() - MAX_AGE_MILLIS;
            files.forEach(file -> {
                try {
                    if (Files.getLastModifiedTime(file).toMillis() < limit) Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // leave it
                }
            });
        } catch (Throwable ignored) {
            // leave it
        }
    }
}

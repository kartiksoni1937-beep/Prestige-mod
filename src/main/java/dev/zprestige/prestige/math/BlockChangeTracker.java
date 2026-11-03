package dev.zprestige.prestige.math;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.network.BlockChangeListener;
import dev.zprestige.prestige.render.MinecraftAccess;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.math.BlockPos;

public final class BlockChangeTracker implements MinecraftAccess {
    public static final Map CLEARED_BLOCKS = new ConcurrentHashMap();
    public static final Map EXPLOSIONS = new ConcurrentHashMap();
    public static final Map PLACED_BLOCKS = new ConcurrentHashMap();
    private static boolean listenerRegistered;

    private BlockChangeTracker() {
    }

    private static void ensureListenerRegistered() {
        if (!listenerRegistered && Prestige.eventBus != null) {
            listenerRegistered = true;
            Prestige.eventBus.register(new BlockChangeListener());
        }
    }

    public static void markExplosion(BlockPos pos) {
        ensureListenerRegistered();
        EXPLOSIONS.put(pos.toImmutable(), System.currentTimeMillis());
    }

    public static boolean isRecentlyExploded(BlockPos pos) {
        ensureListenerRegistered();
        long now = System.currentTimeMillis();
        Long clearedAt = (Long)CLEARED_BLOCKS.get(pos);
        if (clearedAt != null && now - clearedAt < 300L) {
            return true;
        } else {
            for (Entry explosion : (Iterable<Entry>)(EXPLOSIONS.entrySet())) {
                if (now - (Long)explosion.getValue() <= 100L && ((BlockPos)explosion.getKey()).getSquaredDistance(pos) <= 42.25) {
                    return true;
                }
            }

            return false;
        }
    }

    public static boolean isRecentlyPlaced(BlockPos pos) {
        ensureListenerRegistered();
        Long placedAt = (Long)PLACED_BLOCKS.get(pos);
        return placedAt != null && System.currentTimeMillis() - placedAt < 120L;
    }

    public static void markCleared(BlockPos pos, long time) {
        CLEARED_BLOCKS.put(pos.toImmutable(), time);
    }
}

package dev.zprestige.prestige.math;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.network.BlockChangeListener;
import dev.zprestige.prestige.render.MinecraftAccess;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public final class BlockChangeTracker implements MinecraftAccess {
    public static final Map CLEARED_BLOCKS = new ConcurrentHashMap();
    public static final Map EXPLOSIONS = new ConcurrentHashMap();
    public static final Map PLACED_BLOCKS = new ConcurrentHashMap();
    /** Respawn anchors this client placed, kept whether or not any anchor module is currently enabled. */
    private static final Map RESPAWN_ANCHORS = new ConcurrentHashMap();
    private static boolean listenerRegistered;

    private BlockChangeTracker() {
    }

    /** Starts the packet listener early, so placements are recorded even before a module that needs them is on. */
    public static void init() {
        ensureListenerRegistered();
    }

    public static void markOwnAnchor(BlockPos pos) {
        ensureListenerRegistered();
        if (pos != null) {
            RESPAWN_ANCHORS.put(pos.toImmutable(), System.currentTimeMillis());
        }
    }

    public static void clearOwnAnchor(BlockPos pos) {
        if (pos != null) {
            RESPAWN_ANCHORS.remove(pos);
        }
    }

    /** True while the anchor this client placed is still an anchor in the world; removes it once it is gone. */
    public static boolean isOwnAnchor(BlockPos pos) {
        ensureListenerRegistered();
        if (pos == null || !RESPAWN_ANCHORS.containsKey(pos)) {
            return false;
        }
        if (mc.world == null || !mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
            RESPAWN_ANCHORS.remove(pos);
            return false;
        }
        return true;
    }

    /** Snapshot of the anchors still standing that this client placed. */
    public static Set getOwnAnchors() {
        ensureListenerRegistered();
        Set result = new HashSet();
        for (Object entry : RESPAWN_ANCHORS.keySet()) {
            BlockPos pos = (BlockPos)entry;
            if (mc.world != null && mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                result.add(pos);
            } else {
                RESPAWN_ANCHORS.remove(pos);
            }
        }
        return result;
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

package dev.zprestige.prestige.network;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.math.BlockChangeTracker;
import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BlockChangeListener implements MinecraftAccess {
    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        // Packets arrive on the netty thread; BlockChangeTracker maps are plain HashMaps read on the client thread.
        long now = System.currentTimeMillis();
        if (mc.isOnThread()) {
            handleReceive(event, now);
        } else {
            mc.execute(() -> handleReceive(event, now));
        }
    }

    private static void handleReceive(PacketReceiveEvent event, long now) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState() != null && (blockUpdate.getState().isAir() || blockUpdate.getState().getBlock() == Blocks.FIRE)) {
                BlockChangeTracker.markCleared(blockUpdate.getPos(), now);
            }
            // The anchor is gone once the server replaces it with anything that is not an anchor.
            if (blockUpdate.getState() != null && !blockUpdate.getState().isOf(Blocks.RESPAWN_ANCHOR)) {
                BlockChangeTracker.clearOwnAnchor(blockUpdate.getPos());
            }
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket deltaUpdate) {
            deltaUpdate.visitUpdates((pos, state) -> {
                if (state.isAir() || state.getBlock() == Blocks.FIRE) {
                    BlockChangeTracker.markCleared(pos, now);
                }
                if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
                    BlockChangeTracker.clearOwnAnchor(pos);
                }
            });
        } else if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            Vec3d center = explosion.center();
            BlockChangeTracker.EXPLOSIONS.put(BlockPos.ofFloored(center.x, center.y, center.z), now);
        }

        pruneOldEntries(now);
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            if (mc.player != null && mc.world != null) {
                ItemStack used = mc.player.getStackInHand(interactPacket.getHand());
                if (used.getItem() == Items.RESPAWN_ANCHOR) {
                    // Remember anchors this client places, whether or not an anchor module is enabled right now.
                    BlockHitResult anchorHit = interactPacket.getBlockHitResult();
                    BlockPos anchorPos = mc.world.getBlockState(anchorHit.getBlockPos()).isReplaceable()
                        ? anchorHit.getBlockPos()
                        : anchorHit.getBlockPos().offset(anchorHit.getSide());
                    BlockChangeTracker.markOwnAnchor(anchorPos);
                } else if (used.getItem() instanceof BlockItem) {
                    BlockHitResult hitResult = interactPacket.getBlockHitResult();
                    BlockPos clickedPos = hitResult.getBlockPos();
                    if (mc.world.getBlockState(clickedPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
                        long now = System.currentTimeMillis();
                        BlockChangeTracker.PLACED_BLOCKS.put(clickedPos.offset(hitResult.getSide()).toImmutable(), now);
                        if (mc.world.getBlockState(clickedPos).isReplaceable()) {
                            BlockChangeTracker.PLACED_BLOCKS.put(clickedPos.toImmutable(), now);
                        }

                        pruneOldEntries(now);
                    }
                }
            }
        }
    }

    private static void pruneOldEntries(long now) {
        if (BlockChangeTracker.CLEARED_BLOCKS.size() > 256) {
            BlockChangeTracker.CLEARED_BLOCKS.entrySet().removeIf(clearedEntry -> now - (Long)((java.util.Map.Entry)clearedEntry).getValue() > 2000L);
        }

        if (BlockChangeTracker.EXPLOSIONS.size() > 64) {
            BlockChangeTracker.EXPLOSIONS.entrySet().removeIf(explosionEntry -> now - (Long)((java.util.Map.Entry)explosionEntry).getValue() > 2000L);
        }

        if (BlockChangeTracker.PLACED_BLOCKS.size() > 256) {
            BlockChangeTracker.PLACED_BLOCKS.entrySet().removeIf(placedEntry -> now - (Long)((java.util.Map.Entry)placedEntry).getValue() > 2000L);
        }
    }
}

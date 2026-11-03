package dev.zprestige.prestige.network;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class TotemPopManager implements MinecraftAccess {
    private final Object2IntMap popCounts;
    private final HashSet presentPlayers;
    private static final long TOTEM_POP_STATUS = 5903295852427870243L;

    public TotemPopManager() {
        super();
        this.popCounts = new Object2IntOpenHashMap();
        this.presentPlayers = new HashSet();
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket && statusPacket.getStatus() == (int)TOTEM_POP_STATUS) {
            // Packets arrive on the netty thread; touch the world and pop map on the client thread only.
            mc.execute(() -> {
                if (mc.world != null && statusPacket.getEntity(mc.world) instanceof PlayerEntity player) {
                    int pops = this.popCounts.getOrDefault(player.getUuid(), 0);
                    this.popCounts.put(player.getUuid(), ++pops);
                }
            });
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world == null) {
            this.popCounts.clear();
        } else if (!this.popCounts.isEmpty()) {
            this.presentPlayers.clear();

            for (PlayerEntity player : mc.world.getPlayers()) {
                UUID uuid = player.getUuid();
                this.presentPlayers.add(uuid);
                if (player.deathTime > 0 || player.getHealth() <= 0.0F || player.isDead()) {
                    this.popCounts.removeInt(uuid);
                }
            }

            if (!this.popCounts.isEmpty()) {
                this.popCounts.keySet().retainAll(this.presentPlayers);
            }
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.popCounts.clear();
    }

    public int getPopCount(PlayerEntity player) {
        return this.popCounts.getOrDefault(player.getUuid(), 0);
    }
}

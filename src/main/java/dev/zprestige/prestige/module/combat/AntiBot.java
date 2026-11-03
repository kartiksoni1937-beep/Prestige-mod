package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.BotCheckEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.AntiBotQueryEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.world.GameMode;

public class AntiBot extends Module {
    private MultiSetting mode;
    private IntSetting validationThreshold;
    private IntSetting tabListGrace;
    private IntSetting pingGrace;
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private Map positionPacketCounts = new IdentityHashMap();

    public AntiBot() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.pingGrace.visibleWhen((Predicate<Integer>)ignored -> this.mode.isSelected("Ping"));
        this.tabListGrace
            .visibleWhen((Predicate<Integer>)ignored -> this.mode.isSelected("TabList"));
        this.validationThreshold
            .visibleWhen((Predicate<Integer>)ignored -> this.mode.isSelected("Packet"));
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (this.mode.isSelected("Packet") && event.getPacket() instanceof EntityPositionS2CPacket) {
            int entityId = ((EntityPositionS2CPacket)event.getPacket()).entityId();
            mc.execute(() -> {
                if (mc.world == null) {
                    return;
                }

                Entity entity = mc.world.getEntityById(entityId);
                if (entity == null) {
                    return;
                }

                if (entity instanceof PlayerEntity player && !Prestige.botFilter.getValidatedPlayers().contains(entity)) {
                    int threshold = this.validationThreshold.getValue();
                    int packetCount = (Integer)this.positionPacketCounts.getOrDefault(player, 0) + 1;
                    if (packetCount >= threshold) {
                        Prestige.botFilter.markValidated(player);
                        this.positionPacketCounts.remove(player);
                    } else {
                        this.positionPacketCounts.put(player, packetCount);
                    }
                }
            });
        }
    }

    @Override
    public void onDisable() {
        this.positionPacketCounts.clear();
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.positionPacketCounts.clear();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (!this.positionPacketCounts.isEmpty()) {
            this.positionPacketCounts.keySet().removeIf(player -> !((net.minecraft.entity.player.PlayerEntity)player).isAlive());
        }
    }

    @Subscribe
    public void onAntiBotQuery(AntiBotQueryEvent event) {
        event.cancel();
    }

    @Subscribe
    public void onBotCheck(BotCheckEvent event) {
        PlayerEntity player = event.getPlayer();
        UUID uuid = player.getUuid();
        if (!this.mode.isSelected("UUID") || !uuid.equals(NIL_UUID) && uuid.version() == 4) {
            PlayerListEntry listEntry = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(uuid) : null;
            if (this.mode.isSelected("TabList") && player.age * 50L >= this.tabListGrace.getValue().intValue() && listEntry == null) {
                event.cancel();
            } else {
                if (this.mode.isSelected("Ping") && listEntry != null) {
                    GameMode gameMode = listEntry.getGameMode();
                    if ((gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) && listEntry.getLatency() == 0 && player.age * 50L >= this.pingGrace.getValue().intValue()) {
                        event.cancel();
                    }
                }
            }
        } else {
            event.cancel();
        }
    }
}

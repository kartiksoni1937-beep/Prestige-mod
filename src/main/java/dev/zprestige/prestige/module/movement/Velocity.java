package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.VelocityUpdateEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

public class Velocity extends Module {
    public Velocity() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting chance;
    private BoolSetting ignoreExplosions;
    private RangeSetting vertical;
    private RangeSetting horizontal;
    private CopyOnWriteArrayList recentExplosions = new CopyOnWriteArrayList();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.player.isOnGround()) {
            this.recentExplosions.clear();
        }
    }

    @Subscribe
    public void onVelocityUpdate(VelocityUpdateEvent event) {
        if ((this.recentExplosions.isEmpty() || !this.ignoreExplosions.getValue()) && RandomUtil.RANDOM.nextFloat() * 100.0F <= this.chance.getValue()) {
            float verticalFactor = this.vertical.randomInRange() / 100.0F;
            float horizontalFactor = this.horizontal.randomInRange() / 100.0F;
            event.setVelocityX(event.getVelocityX() * horizontalFactor);
            event.setVelocityY(event.getVelocityY() * verticalFactor);
            event.setVelocityZ(event.getVelocityZ() * horizontalFactor);
            event.cancel();
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof ExplosionS2CPacket explosionPacket
            && explosionPacket.playerKnockback().isPresent()
            && explosionPacket.playerKnockback().get().lengthSquared() > 0.0) {
            if (this.recentExplosions.size() >= 16) {
                this.recentExplosions.remove(0);
            }

            this.recentExplosions.add(explosionPacket);
        }
    }

    @Override
    public void onDisable() {
        this.recentExplosions.clear();
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.recentExplosions.clear();
    }
}

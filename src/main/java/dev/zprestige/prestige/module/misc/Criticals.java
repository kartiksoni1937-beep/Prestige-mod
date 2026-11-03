package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.network.PacketSender;
import dev.zprestige.prestige.render.JumpKeyPresser;
import dev.zprestige.prestige.type.EventPhase;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;

public class Criticals extends Module {
    public Criticals() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private Entity pendingTarget = null;
    private int ticksUntilAttack = 0;
    private boolean replayingAttack = false;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (this.canCrit() && !this.replayingAttack) {
            Entity target = event.getTarget();
            if (target != null) {
                if (this.mode.getValue().equals("Silent")) {
                    this.sendOffsetPosition(0.0625F);
                    this.sendOffsetPosition(0.0F);
                } else {
                    this.pendingTarget = target;
                    if (this.mode.getValue().equals("Legit")) {
                        JumpKeyPresser.pressJump();
                    } else {
                        mc.player.setVelocity(new Vec3d(mc.player.getVelocity().x, mc.player.getVelocity().y + 0.15, mc.player.getVelocity().z));
                    }

                    this.ticksUntilAttack = this.mode.getValue().equals("Legit") ? 6 : 1;
                    event.cancel();
                }
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (this.canCrit() && !this.replayingAttack) {
            if (event.getPacket() instanceof HandSwingC2SPacket && !this.mode.getValue().equals("Silent") && this.pendingTarget != null) {
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.pendingTarget != null) {
            if (this.ticksUntilAttack <= 0) {
                this.replayingAttack = true;
                try {
                    if (mc.player != null && mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.ENTITY) {
                        PlayerInteractions.attackEntity(this.pendingTarget);
                    }
                } finally {
                    this.replayingAttack = false;
                    this.pendingTarget = null;
                }
            } else {
                this.ticksUntilAttack--;
            }
        }
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (event.getPhase() == EventPhase.PRE) {
            if (this.pendingTarget != null && this.ticksUntilAttack > 0) {
                event.cancel();
            }
        }
    }

    @Override
    public void onDisable() {
        this.pendingTarget = null;
        this.ticksUntilAttack = 0;
        this.replayingAttack = false;
    }

    private void sendOffsetPosition(float yOffset) {
        PacketSender.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + yOffset, mc.player.getZ(), false, mc.player.horizontalCollision));
    }

    private boolean canCrit() {
        return mc.player != null && mc.player.isOnGround() && !mc.player.isSubmergedInWater() && !mc.player.isInLava() && !mc.player.isClimbing();
    }
}

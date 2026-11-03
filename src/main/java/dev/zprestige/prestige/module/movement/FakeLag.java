package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.module.combat.AutoElytra;
import dev.zprestige.prestige.network.PacketSender;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

public class FakeLag extends Module {
    private EnumSetting mode;
    private RangeSetting lagRange;
    private FloatSetting targetFov;
    private RangeSetting choke;
    private RangeSetting rangeChoke;
    private MultiSetting sendPackets;
    private BoolSetting render;
    private Queue heldPackets = new ConcurrentLinkedQueue();
    private StopWatch chokeTimer = new StopWatch();
    private StopWatch knockbackTimer = new StopWatch();
    private Vec3d lastFlushPos = Vec3d.ZERO;
    private boolean flushing;
    private boolean rangeFlushed;
    private boolean knockbackActive;
    private float lastTargetDistance = 0.0F;

    public FakeLag() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.choke
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Timed")
                        || this.mode.getValue().equals("Dynamic")
                        || this.mode.getValue().equals("Knockback")
            );
        this.sendPackets.visibleWhen((Predicate<String>)ignored -> !this.mode.getValue().equals("Manual"));
        this.lagRange
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Ranged") || this.mode.getValue().equals("Dynamic")
            );
        this.targetFov
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Ranged") || this.mode.getValue().equals("Dynamic")
            );
        this.rangeChoke
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Ranged") || this.mode.getValue().equals("Dynamic")
            );
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.world != null && mc.player != null) {
            if (!this.mode.getValue().equals("Manual")
                && event.getPacket() instanceof ExplosionS2CPacket
                && this.sendPackets.isSelected("On Explosion")) {
                if (!(
                    mc.player
                            .squaredDistanceTo(
                                ((ExplosionS2CPacket)event.getPacket()).center().getX(),
                                ((ExplosionS2CPacket)event.getPacket()).center().getY(),
                                ((ExplosionS2CPacket)event.getPacket()).center().getZ()
                            )
                        > 100.0
                )) {
                    this.flushPackets();
                }
            } else {
                if (this.mode.getValue().equals("Knockback")
                    && event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket
                    && velocityPacket.getEntityId() == mc.player.getId()) {
                    this.knockbackActive = true;
                    this.knockbackTimer.reset();
                    this.choke.reroll();
                }
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.mode.getValue().equals("Manual")
                || (
                        !(event.getPacket() instanceof PlayerInteractEntityC2SPacket)
                            || !this.sendPackets.isSelected("On Interact Entity")
                    )
                    && (!(event.getPacket() instanceof HandSwingC2SPacket) || !this.sendPackets.isSelected("On Hand Swing"))
                    && (
                        !(event.getPacket() instanceof PlayerInteractBlockC2SPacket)
                            || !this.sendPackets.isSelected("On Interact Block")
                    )
                    && (
                        !(event.getPacket() instanceof PlayerInteractItemC2SPacket)
                            || !this.sendPackets.isSelected("On Interact Item")
                    )
                    && (!(event.getPacket() instanceof ClickSlotC2SPacket) || !this.sendPackets.isSelected("On Click Slot"))) {
                if (!this.flushing && (!this.mode.getValue().equals("Knockback") || !this.knockbackTimer.hasElapsedRandom(this.choke))) {
                    if (AutoElytra.active) {
                        this.flushPackets();
                        return;
                    }

                    this.heldPackets.add(event.getPacket());
                    event.cancel();
                }
            } else {
                this.flushPackets();
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        this.rangeFlushed = false;
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (this.mode.getValue().equals("Knockback") && this.knockbackTimer.hasElapsedRandom(this.choke)) {
            this.knockbackActive = false;
            this.flushPackets();
        }

        if (this.mode.getValue().equals("Ranged") || this.mode.getValue().equals("Dynamic")) {
            PlayerEntity enemy = TargetSelector.getClosestEnemyInFov(this.targetFov.getValue());
            if (enemy != null) {
                double distance = (float)Math.sqrt(mc.player.squaredDistanceTo(enemy));
                if (distance < this.lagRange.getHigh() && distance > this.lagRange.getLow() && distance <= this.lastTargetDistance) {
                    if (this.chokeTimer.hasElapsedRandom(this.rangeChoke)) {
                        this.chokeTimer.reset();
                        this.rangeChoke.reroll();
                        this.flushPackets();
                        this.rangeFlushed = true;
                    }
                } else if (this.mode.getValue().equals("Ranged") || this.chokeTimer.hasElapsedRandom(this.choke) || mc.player.isUsingItem()) {
                    this.flushPackets();
                }

                this.lastTargetDistance = (float)distance;
            } else if (this.mode.getValue().equals("Ranged") || this.chokeTimer.hasElapsedRandom(this.choke) || mc.player.isUsingItem()) {
                this.flushPackets();
            }
        }

        if (this.mode.getValue().equals("Timed") && (this.chokeTimer.hasElapsedRandom(this.choke) || mc.player.isUsingItem())) {
            this.flushPackets();
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.render.getValue()) {
            StopWatch activeTimer = this.knockbackActive ? this.knockbackTimer : this.chokeTimer;
            float chokeDuration = this.mode.getValue().equals("Manual") ? 1000.0F : (this.rangeFlushed ? this.rangeChoke.getRolled() : this.choke.getRolled());
            if (chokeDuration > 0.0F && !(activeTimer.getElapsed() / chokeDuration / 2.0F < 0.1F)) {
                ShapeRenderer.drawGlowRing(event.context, event.renderer, (float)this.lastFlushPos.x, (float)this.lastFlushPos.y, (float)this.lastFlushPos.z, Math.min(0.5F, activeTimer.getElapsed() / chokeDuration / 2.0F), 0.005F, 0.1F, UiMath.withAlpha(UiMath.getAccentColor().brighter(), Math.min(1.0F, activeTimer.getElapsed() / chokeDuration)), UiMath.getAccentColor());
            }
        }
    }

    @Override
    public void onEnable() {
        this.chokeTimer.reset();
        this.lastFlushPos = PositionUtil.getPlayerPos();
    }

    @Override
    public void onDisable() {
        this.flushPackets();
        // packets of an old world must never be replayed into a new one
        this.heldPackets.clear();
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.heldPackets.clear();
        this.knockbackActive = false;
    }

    private void flushPackets() {
        if (mc.player != null && mc.world != null) {
            this.flushing = true;

            try {
                while (mc.getNetworkHandler() != null && !this.heldPackets.isEmpty()) {
                    PacketSender.sendPacket((Packet)this.heldPackets.poll());
                }
            } finally {
                this.flushing = false;
            }

            this.chokeTimer.reset();
            this.choke.reroll();
            this.lastFlushPos = PositionUtil.getPlayerPos();
        }
    }
}

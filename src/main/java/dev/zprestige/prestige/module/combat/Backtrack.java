package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.data.DelayedPacket;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Backtrack extends Module {
    public Backtrack() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting range;
    private RangeSetting latency;
    private BoolSetting weaponOnly;
    private Map trackedPositions = new ConcurrentHashMap();
    private ConcurrentLinkedQueue delayedPackets = new ConcurrentLinkedQueue();
    private volatile long lastReleaseTime;
    private volatile int targetId = -1;
    private volatile int pendingTargetId = -1;
    private Vec3d lastTargetPos;
    private boolean holdingPackets;
    private volatile Vec3d prevRenderPos = Vec3d.ZERO;
    private volatile Vec3d renderPos = Vec3d.ZERO;
    private volatile Vec3d renderTargetPos = Vec3d.ZERO;
    private int renderLerpSteps;

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        this.releasePackets(false);
        this.applyPendingTarget();
        if (mc.world == null || mc.player == null) {
            this.releasePackets(true);
            this.targetId = -1;
        } else if (mc.player.age < 10) {
            this.resetState(true);
        } else {
            Packet packet = event.getPacket();
            if (!(packet instanceof PlayerRespawnS2CPacket) && !(packet instanceof GameStateChangeS2CPacket) && !(packet instanceof DisconnectS2CPacket)) {
                ArrayList packets = new ArrayList();
                if (packet instanceof BundleS2CPacket bundle) {
                    for (Packet bundledPacket : bundle.getPackets()) {
                        packets.add(bundledPacket);
                    }
                } else {
                    packets.add(packet);
                }

                Vec3d newTargetPos = null;

                for (Packet subPacket : (Iterable<Packet>)(packets)) {
                    Vec3d updatedPos = this.processPositionPacket(subPacket);
                    if (updatedPos != null) {
                        newTargetPos = updatedPos;
                    }

                    if (this.isSelfMotionPacket(subPacket)) {
                        this.releasePackets(true);
                        return;
                    }
                }

                if (this.targetId == -1) {
                    this.releasePackets(true);
                } else if (!this.passesWeaponCheck()) {
                    this.releasePackets(true);
                } else {
                    if (newTargetPos != null) {
                        this.holdingPackets = this.isMovingAway(newTargetPos);
                        this.lastTargetPos = newTargetPos;
                    }

                    if (this.holdingPackets) {
                        event.cancel();
                        this.queuePacket(packet);
                    } else {
                        this.releasePackets(true);
                    }
                }
            } else {
                this.resetState(true);
            }
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (target != null) {
            if (target instanceof PlayerEntity && this.isInRange(target)) {
                if (this.passesWeaponCheck()) {
                    int entityId = target.getId();
                    if (entityId != this.targetId && entityId != this.pendingTargetId) {
                        this.pendingTargetId = entityId;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.prevRenderPos = this.renderPos;
        if (this.targetId != -1 && this.renderLerpSteps > 0) {
            this.renderPos = this.renderPos.add(this.renderTargetPos.subtract(this.renderPos).multiply(1.0 / this.renderLerpSteps));
            this.renderLerpSteps--;
        }

        if (mc.world != null && mc.player != null) {
            if (!mc.player.isAlive()) {
                this.resetState(true);
            }
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState(false);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.targetId != -1 && mc.world != null) {
            if (this.renderPos.getY() != 0.0) {
                if (mc.world.getEntityById(this.targetId) != null) {
                    float tickDelta = Render2D.getTickDelta();
                    Vec3d interpolatedPos = this.prevRenderPos.add(this.renderPos.subtract(this.prevRenderPos).multiply(tickDelta));
                    ShapeRenderer.drawFilledBox(event.context, event.renderer, (float)(interpolatedPos.getX() - 0.25), (float)interpolatedPos.getY(), (float)(interpolatedPos.getZ() - 0.25), (float)(interpolatedPos.getX() + 0.25), (float)(interpolatedPos.getY() + 2.0), (float)(interpolatedPos.getZ() + 0.25), UiMath.withAlpha(UiMath.getAccentColor(), 0.3F));
                    ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)(interpolatedPos.getX() - 0.25), (float)interpolatedPos.getY(), (float)(interpolatedPos.getZ() - 0.25), (float)(interpolatedPos.getX() + 0.25), (float)(interpolatedPos.getY() + 2.0), (float)(interpolatedPos.getZ() + 0.25), UiMath.withAlpha(UiMath.getAccentColor(), 1.0F));
                }
            }
        }
    }

    @Override
    public void onEnable() {
        this.resetState(false);
    }

    @Override
    public void onDisable() {
        this.resetState(true);
    }

    private Vec3d processPositionPacket(Packet packet) {
        Integer entityId = null;
        if (packet instanceof EntityS2CPacket movePacket) {
            Entity entity = movePacket.getEntity(mc.world);
            if (entity != null && movePacket.isPositionChanged()) {
                entityId = entity.getId();
                TrackedPosition trackedPos = (TrackedPosition)this.trackedPositions
                    .computeIfAbsent(entityId, key -> this.createTrackedPosition(PositionUtil.getEntityPos(entity)));
                trackedPos.setPos(trackedPos.withDelta(movePacket.getDeltaX(), movePacket.getDeltaY(), movePacket.getDeltaZ()));
            }
        } else if (packet instanceof EntityPositionS2CPacket positionPacket) {
            entityId = positionPacket.entityId();
            this.setTrackedPosition(entityId, positionPacket.change().position());
        } else if (packet instanceof EntitiesDestroyS2CPacket destroyPacket) {
            for (int destroyedId : destroyPacket.getEntityIds()) {
                this.trackedPositions.remove(destroyedId);
                if (destroyedId == this.targetId) {
                    this.targetId = -1;
                    this.lastTargetPos = null;
                    this.releasePackets(true);
                }
            }

            return null;
        }

        if (packet instanceof EntityPositionSyncS2CPacket syncPacket) {
            entityId = syncPacket.id();
            this.setTrackedPosition(entityId, syncPacket.values().position());
        }

        if (entityId != null && entityId == this.targetId) {
            TrackedPosition targetTrackedPos = (TrackedPosition)this.trackedPositions.get(entityId);
            if (targetTrackedPos == null) {
                return null;
            } else {
                Vec3d serverPos = targetTrackedPos.withDelta(0L, 0L, 0L);
                this.setRenderTarget(serverPos);
                return serverPos;
            }
        } else {
            return null;
        }
    }

    private boolean isSelfMotionPacket(Packet packet) {
        if (packet instanceof PlayerPositionLookS2CPacket) {
            return true;
        } else {
            return packet instanceof EntityVelocityUpdateS2CPacket velocityPacket && mc.player != null ? velocityPacket.getEntityId() == mc.player.getId() : false;
        }
    }

    private boolean isMovingAway(Vec3d newPos) {
        if (this.lastTargetPos == null) {
            return false;
        } else {
            double lastDistance = this.getHitDistance(this.lastTargetPos);
            return this.getHitDistance(newPos) > lastDistance + 0.001;
        }
    }

    private double getHitDistance(Vec3d pos) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d rayEnd = eyePos.add(pos.add(0.0, 0.9, 0.0).subtract(eyePos).normalize().multiply(256.0));
        return new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3).expand(0.1).raycast(eyePos, rayEnd).map(eyePos::distanceTo).orElse(0.0);
    }

    private void queuePacket(Packet packet) {
        long releaseTime = System.currentTimeMillis() + (long)this.latency.randomInRange();
        if (releaseTime <= this.lastReleaseTime) {
            releaseTime = this.lastReleaseTime + 1L;
        }

        this.lastReleaseTime = releaseTime;
        this.delayedPackets.add(new DelayedPacket(packet, releaseTime));
    }

    private void releasePackets(boolean releaseAll) {
        if (!this.delayedPackets.isEmpty()) {
            ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
            if (networkHandler == null) {
                this.delayedPackets.clear();
                this.lastReleaseTime = 0L;
            } else {
                long now = System.currentTimeMillis();

                DelayedPacket delayedPacket;
                while ((delayedPacket = (DelayedPacket)this.delayedPackets.peek()) != null && (releaseAll || now >= delayedPacket.releaseTime())) {
                    this.delayedPackets.poll();

                    try {
                        ((net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener>)delayedPacket.packet()).apply(networkHandler);
                    } catch (Exception ignored) {
                    }
                }

                if (this.delayedPackets.isEmpty()) {
                    this.lastReleaseTime = 0L;
                }
            }
        }
    }

    private void applyPendingTarget() {
        if (this.pendingTargetId != -1) {
            this.targetId = this.pendingTargetId;
            this.pendingTargetId = -1;
            this.lastTargetPos = null;
            Vec3d startPos = null;
            TrackedPosition trackedPos = (TrackedPosition)this.trackedPositions.get(this.targetId);
            if (trackedPos != null) {
                startPos = trackedPos.withDelta(0L, 0L, 0L);
            } else if (mc.world != null) {
                Entity entity = mc.world.getEntityById(this.targetId);
                if (entity != null) {
                    startPos = PositionUtil.getEntityPos(entity);
                }
            }

            this.snapRenderPos((Vec3d)(startPos == null ? Vec3d.ZERO : startPos));
            this.releasePackets(true);
        }
    }

    private void setTrackedPosition(int entityId, Vec3d pos) {
        ((TrackedPosition)this.trackedPositions.computeIfAbsent(entityId, key -> new TrackedPosition())).setPos(pos);
    }

    private TrackedPosition createTrackedPosition(Vec3d pos) {
        TrackedPosition trackedPos = new TrackedPosition();
        trackedPos.setPos(pos);
        return trackedPos;
    }

    private void resetState(boolean flushQueued) {
        if (flushQueued) {
            this.releasePackets(true);
        } else {
            this.delayedPackets.clear();
            this.lastReleaseTime = 0L;
        }

        this.trackedPositions.clear();
        this.targetId = -1;
        this.pendingTargetId = -1;
        this.lastTargetPos = null;
        this.holdingPackets = false;
        this.snapRenderPos(Vec3d.ZERO);
    }

    private void setRenderTarget(Vec3d pos) {
        this.renderTargetPos = pos;
        this.renderLerpSteps = 3;
    }

    private void snapRenderPos(Vec3d pos) {
        this.prevRenderPos = pos;
        this.renderPos = pos;
        this.renderTargetPos = pos;
        this.renderLerpSteps = 0;
    }

    private boolean passesWeaponCheck() {
        if (!this.weaponOnly.getValue()) {
            return true;
        } else {
            ItemStack mainHand = mc.player.getMainHandStack();
            return InventoryUtil.isSword(mainHand) || InventoryUtil.isAxe(mainHand) || InventoryUtil.isMace(mainHand);
        }
    }

    private boolean isInRange(Entity entity) {
        double distance = mc.player.distanceTo(entity);
        return distance >= this.range.getMin() && distance <= this.range.getMax();
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.MouseButtonEvent;
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.AutoAttackEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SilentAim extends Module implements RotationProvider {
    private BoolSetting playerOnly;
    private BoolSetting weaponOnly;
    private BoolSetting continuousTracking;
    private FloatSetting maxYaw;
    private FloatSetting maxPitch;
    private MultiSetting aimPoints;
    private BoolSetting renderDot;
    private FloatSetting dotSize;
    private ColorSetting dotColor;
    private boolean hitPending = false;
    private boolean sendingAttack = false;
    private Vec3d lastAimPoint = null;
    private Entity pendingTarget = null;
    private static final String[] AIM_POINT_NAMES = new String[]{"Head", "Neck", "Body", "Arms", "Thighs", "Legs", "Feet"};
    private static final double[] AIM_POINT_HEIGHTS = new double[]{0.95, 0.85, 0.7, 0.65, 0.35, 0.2, 0.05};

    public SilentAim() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.dotColor.visibleWhen((Predicate<Color>)ignored -> this.renderDot.getValue());
        this.dotSize.visibleWhen((Predicate<Float>)ignored -> this.renderDot.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal, Gamemode.Mace);
    }

    private boolean isKillAuraActive() {
        return KillAura.instance != null && KillAura.instance.isEnabled();
    }

    private boolean exceedsAngleLimits(Rotation rotation) {
        return Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()
            || Math.abs(rotation.getPitch() - mc.player.getPitch()) > this.maxPitch.getValue();
    }

    private Entity findTarget() {
        Entity candidate = this.playerOnly.getValue() ? TargetSelector.getClosestEnemy() : TargetSelector.getClosestEntity();
        return (Entity)(candidate != null
                && candidate.isAlive()
                && !(mc.player.distanceTo(candidate) > 3.0F)
                && mc.player.canSee(candidate)
                && !BlockUtil.isCobwebInLineOfSight(candidate)
            ? candidate
            : null);
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (event.getPhase() == EventPhase.PRE) {
                if (this.sendingAttack) {
                    event.setSkipPost(true);
                } else if (!this.isKillAuraActive()) {
                    if (InventoryUtil.isSword(mc.player.getMainHandStack())
                        || mc.player.getMainHandStack().getItem() instanceof AxeItem
                        || InventoryUtil.isMace(mc.player.getMainHandStack())
                        || !this.weaponOnly.getValue()) {
                        HitResult crosshairHit = mc.crosshairTarget;
                        if (crosshairHit != null
                            && (
                                crosshairHit.getType() == Type.MISS
                                    || crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())
                            )) {
                            Entity target = this.findTarget();
                            if (target != null) {
                                if (!this.exceedsAngleLimits(RotationUtil.getRotationTo(
                                    this.getAimPoint(target)
                                ))) {
                                    this.pendingTarget = target;
                                    this.hitPending = true;
                                    event.cancel();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        Entity target = this.pendingTarget;
        this.pendingTarget = null;
        if (target != null && !target.isAlive()) {
            target = null;
        }

        if (this.isKillAuraActive()) {
            this.hitPending = false;
            this.lastAimPoint = null;
            return null;
        } else {
            if (this.continuousTracking.getValue()) {
                if (target == null) {
                    target = this.findTarget();
                }

                Rotation trackingRotation = target != null ? this.computeAimRotation(target) : null;
                if (trackingRotation != null) {
                    this.lastAimPoint = this.getAimPoint(target);
                    if (this.hitPending) {
                        this.hitPending = false;
                        this.attackTarget(target);
                    }

                    return trackingRotation;
                }

                this.lastAimPoint = null;
            }

            if (!this.hitPending) {
                return null;
            } else {
                this.hitPending = false;
                if (target == null) {
                    return null;
                } else {
                    Rotation hitRotation = this.computeAimRotation(target);
                    if (hitRotation == null) {
                        return null;
                    } else {
                        this.lastAimPoint = this.getAimPoint(target);
                        this.attackTarget(target);
                        return hitRotation;
                    }
                }
            }
        }
    }

    private Rotation computeAimRotation(Entity target) {
        if (!InventoryUtil.isSword(mc.player.getMainHandStack())
            && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)
            && !InventoryUtil.isMace(mc.player.getMainHandStack())
            && this.weaponOnly.getValue()) {
            return null;
        } else {
            HitResult crosshairHit = mc.crosshairTarget;
            if (this.continuousTracking.getValue()
                || crosshairHit != null
                    && (crosshairHit.getType() == Type.MISS || crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack()))) {
                if (target != null && target.isAlive() && !(mc.player.distanceTo(target) > 3.0F) && mc.player.canSee(target) && !BlockUtil.isCobwebInLineOfSight(target)) {
                    Rotation rotation = RotationUtil.getRotationTo(this.getAimPoint(target));
                    return this.exceedsAngleLimits(rotation) ? null : rotation;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private Vec3d getAimPoint(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double boxHeight = box.maxY - box.minY;
        if (boxHeight <= 0.0) {
            return entity.getEyePos();
        } else {
            double nearestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
            double nearestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
            double horizontalDistSq = (eyePos.x - nearestX) * (eyePos.x - nearestX) + (eyePos.z - nearestZ) * (eyePos.z - nearestZ);
            double bestDistSq = Double.POSITIVE_INFINITY;
            Vec3d bestPoint = null;

            for (int index = 0; index < AIM_POINT_NAMES.length; index++) {
                if (this.aimPoints.isSelected(AIM_POINT_NAMES[index])) {
                    double pointY = box.minY + AIM_POINT_HEIGHTS[index] * boxHeight;
                    double dy = eyePos.y - pointY;
                    double distSq = horizontalDistSq + dy * dy;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestPoint = new Vec3d(nearestX, pointY, nearestZ);
                    }
                }
            }

            return bestPoint != null ? bestPoint : entity.getEyePos();
        }
    }

    private void attackTarget(Entity target) {
        if (target != null) {
            new AutoAttackEvent().post();
            this.sendingAttack = true;
            PlayerInteractions.attackEntity(target);
            this.sendingAttack = false;
        }
    }

    @Subscribe
    public void onMouseButton(MouseButtonEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!this.isKillAuraActive()) {
                if (event.getButton() == 0 && event.getAction() == 1) {
                    if (!Prestige.simulatedClickManager.isSimulated(event.getButton())) {
                        HitResult crosshairHit = mc.crosshairTarget;
                        if (crosshairHit != null && crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                            Entity target = this.findTarget();
                            if (target == null) {
                                return;
                            }

                            if (this.exceedsAngleLimits(RotationUtil.getRotationTo(
                                this.getAimPoint(target)
                            ))) {
                                return;
                            }

                            this.pendingTarget = target;
                            this.hitPending = true;
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!this.isKillAuraActive()) {
                HitResult crosshairHit = mc.crosshairTarget;
                if (crosshairHit != null && crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    Entity target = this.findTarget();
                    if (target == null) {
                        return;
                    }

                    if (this.exceedsAngleLimits(RotationUtil.getRotationTo(
                        this.getAimPoint(target)
                    ))) {
                        return;
                    }

                    event.cancel();
                }
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.renderDot.getValue()) {
            Vec3d aimPoint = this.lastAimPoint;
            if (aimPoint != null) {
                float radius = this.dotSize.getValue();
                float minX = (float)(aimPoint.x - radius);
                float minY = (float)(aimPoint.y - radius);
                float minZ = (float)(aimPoint.z - radius);
                float maxX = (float)(aimPoint.x + radius);
                float maxY = (float)(aimPoint.y + radius);
                float maxZ = (float)(aimPoint.z + radius);
                Color fillColor = this.dotColor.getValue();
                Color outlineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), Math.min(255, fillColor.getAlpha() + 55));
                ShapeRenderer.drawFilledBox(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, fillColor);
                ShapeRenderer.drawBoxOutline(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, outlineColor);
            }
        }
    }

    @Override
    public void onEnable() {
        this.lastAimPoint = null;
        this.pendingTarget = null;
        this.hitPending = false;
        this.sendingAttack = false;
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.lastAimPoint = null;
        this.pendingTarget = null;
        this.hitPending = false;
        this.sendingAttack = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }
}

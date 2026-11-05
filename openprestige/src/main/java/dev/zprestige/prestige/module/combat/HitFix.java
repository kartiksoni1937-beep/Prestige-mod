package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.AutoAttackEvent;
import dev.zprestige.prestige.event.ResetAttackCooldownEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;

public class HitFix extends Module implements RotationProvider {
    public HitFix() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting weaponOnly;
    private BoolSetting onlyOnGround;
    private BoolSetting damageTick;
    private StopWatch pendingHitTimer = new StopWatch();
    private boolean hitPending = false;
    private boolean sendingAttack = false;
    private PlayerEntity pendingTarget = null;

    private boolean shouldDelayHit(PlayerEntity target) {
        if (target == null) {
            return false;
        } else if (this.weaponOnly.getValue() && !InventoryUtil.isSword(mc.player.getMainHandStack())) {
            return false;
        } else {
            return this.damageTick.getValue() && target.hurtTime != 0
                ? true
                : this.onlyOnGround.getValue() && PositionUtil.getHeightAboveGround(target) > 0.3F;
        }
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (event.getPhase() == EventPhase.PRE) {
                if (this.sendingAttack) {
                    event.setSkipPost(true);
                } else if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.ENTITY) {
                    if (mc.crosshairTarget instanceof EntityHitResult) {
                        if (((EntityHitResult)mc.crosshairTarget).getEntity() instanceof PlayerEntity target) {
                            if (this.shouldDelayHit(target)) {
                                this.hitPending = true;
                                this.pendingTarget = target;
                                this.pendingHitTimer.reset();
                                event.cancel();
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.ENTITY) {
                if (mc.crosshairTarget instanceof EntityHitResult) {
                    if (((EntityHitResult)mc.crosshairTarget).getEntity() instanceof PlayerEntity target) {
                        if (this.hitPending || this.shouldDelayHit(target)) {
                            this.hitPending = true;
                            this.pendingTarget = target;
                            this.pendingHitTimer.reset();
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onResetAttackCooldown(ResetAttackCooldownEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.ENTITY) {
                if (mc.crosshairTarget instanceof EntityHitResult) {
                    if (((EntityHitResult)mc.crosshairTarget).getEntity() instanceof PlayerEntity target) {
                        if (this.hitPending || this.shouldDelayHit(target)) {
                            this.hitPending = true;
                            this.pendingTarget = target;
                            this.pendingHitTimer.reset();
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.hitPending) {
            return null;
        } else if (mc.player.isUsingItem()) {
            return null;
        } else {
            PlayerEntity target = this.pendingTarget != null && this.pendingTarget.isAlive() ? this.pendingTarget : TargetSelector.getClosestEnemy();
            if (target != null && !(mc.player.distanceTo(target) > 3.0F) && mc.player.canSee(target)) {
                if (this.shouldDelayHit(target)) {
                    return null;
                } else {
                    if (this.pendingHitTimer.hasElapsed(1000.0F)) {
                        this.hitPending = false;
                        this.pendingTarget = null;
                        return null;
                    } else {
                        Rotation rotation = RotationUtil.getRotationTo(target.getEyePos());
                        this.hitPending = false;
                        this.pendingTarget = null;
                        new AutoAttackEvent().post();
                        this.sendingAttack = true;
                        PlayerInteractions.attackEntity(target);
                        this.sendingAttack = false;
                        return rotation;
                    }
                }
            } else {
                this.hitPending = false;
                this.pendingTarget = null;
                return null;
            }
        }
    }

    @Override
    public void onEnable() {
        this.hitPending = false;
        this.pendingTarget = null;
        this.sendingAttack = false;
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.hitPending = false;
        this.pendingTarget = null;
        this.sendingAttack = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }
}

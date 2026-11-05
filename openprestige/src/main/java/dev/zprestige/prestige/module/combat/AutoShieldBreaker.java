package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

public class AutoShieldBreaker extends Module implements RotationProvider {
    private EnumSetting mode;
    private BoolSetting stun;
    private FloatSetting chance;
    private BoolSetting fromInventory;
    private RangeSetting hitDelay;
    private RangeSetting breakCooldown;
    private BoolSetting swapBack;
    private BoolSetting silentAim;
    private FloatSetting maxYaw;
    private int previousSlot = -1;
    private int axeInventorySlot = -1;
    private boolean swappedToAxe = false;
    private StopWatch breakCooldownTimer = new StopWatch();
    private StopWatch hitDelayTimer = new StopWatch();
    private StopWatch axeSwapTimer = new StopWatch();
    private static final long NOTIFICATION_DURATION_MS = 3000L;

    public AutoShieldBreaker() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.stun.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
        this.breakCooldown.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Automatic"));
        this.swapBack.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
        this.silentAim.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
        this.maxYaw
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Automatic") && this.silentAim.getValue()
            );
        this.chance.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Automatic"));
        this.hitDelay.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Automatic"));
        this.fromInventory.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword);
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (!mc.player.isAlive()) {
            this.previousSlot = -1;
            this.axeInventorySlot = -1;
            this.swappedToAxe = false;
        } else if (!(mc.player.fallDistance > 2.0)) {
            if (this.swappedToAxe) {
                if (this.axeSwapTimer.hasElapsed(this.hitDelay.getRolled() + 400.0F)) {
                    this.swappedToAxe = false;
                }
            }

            if (this.previousSlot != -1 && !this.swappedToAxe && mc.currentScreen == null && !PlayerInteractions.isHoldingUsableItem()) {
                if (this.swapBack.getValue()) {
                    if (this.axeInventorySlot != -1) {
                        InventoryUtil.swapSlot(this.axeInventorySlot, this.previousSlot);
                    } else {
                        InventoryUtil.selectSlot(this.previousSlot);
                    }
                }

                this.previousSlot = -1;
                this.axeInventorySlot = -1;
            }

            if (mc.targetedEntity != null) {
                if (mc.targetedEntity instanceof PlayerEntity) {
                    this.tryBreakShield((PlayerEntity)mc.targetedEntity);
                }
            }
        }
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (this.mode.getValue().equals("On Hit")) {
            if (mc.targetedEntity != null && mc.currentScreen == null && mc.isWindowFocused()) {
                if (mc.targetedEntity instanceof PlayerEntity target) {
                    if (!Prestige.friendManager.isFriend(target.getName().getString())) {
                        if (Prestige.botFilter.isValidTarget(target)) {
                            if (this.isBlockingWithShield(target)) {
                                int axeSlot = this.findHotbarAxe();
                                if (axeSlot != -1) {
                                    if (this.previousSlot == -1) {
                                        this.previousSlot = InventoryUtil.getSelectedSlot();
                                        this.axeInventorySlot = -1;
                                    }

                                    InventoryUtil.selectSlot(axeSlot);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private int findHotbarAxe() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }

        return -1;
    }

    private int findInventoryAxe() {
        for (int slot = 9; slot <= 35; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isBlockingWithShield(PlayerEntity target) {
        return target.isUsingItem() && target.getActiveItem().getItem() == Items.SHIELD;
    }

    @Override
    public Rotation provideRotation() {
        if (!this.silentAim.getValue()) {
            return null;
        } else if (mc.player.fallDistance > 2.0) {
            return null;
        } else {
            PlayerEntity target = TargetSelector.getClosestEnemyInFov(this.maxYaw.getValue());
            if (target != null) {
                if (mc.player.distanceTo(target) > 3.0F || !mc.player.canSee(target)) {
                    return null;
                }

                if (!Prestige.botFilter.isValidTarget(target)) {
                    return null;
                }

                Rotation rotation = RotationUtil.getRotationTo(target.getEyePos());
                if (Math.abs(MathHelper.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()) {
                    return null;
                }

                if (this.tryBreakShield(target)) {
                    return rotation;
                }
            }

            return null;
        }
    }

    public boolean tryBreakShield(PlayerEntity target) {
        if (mc.player.fallDistance > 2.0) {
            return false;
        } else if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (PlayerInteractions.isHoldingUsableItem()) {
                return false;
            } else {
                if (!mc.player.isUsingItem()) {
                    if (RandomUtil.randomInt(0, 100) <= this.chance.getValue() && this.breakCooldownTimer.hasElapsedRandom(this.breakCooldown)) {
                        if (!this.mode.getValue().equals("Automatic")) {
                            return false;
                        }

                        if (Prestige.friendManager.isFriend(target.getName().getString())) {
                            return false;
                        }

                        if (!Prestige.botFilter.isValidTarget(target)) {
                            return false;
                        }

                        if (this.isBlockingWithShield(target)) {
                            if (!(mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
                                if (this.swappedToAxe) {
                                    return false;
                                }

                                int hotbarAxeSlot = this.findHotbarAxe();
                                if (hotbarAxeSlot != -1) {
                                    this.previousSlot = InventoryUtil.getSelectedSlot();
                                    this.axeInventorySlot = -1;
                                    InventoryUtil.selectSlot(hotbarAxeSlot);
                                    this.swappedToAxe = true;
                                    this.axeSwapTimer.reset();
                                } else if (this.fromInventory.getValue()) {
                                    int inventoryAxeSlot = this.findInventoryAxe();
                                    if (inventoryAxeSlot != -1) {
                                        this.previousSlot = InventoryUtil.getSelectedSlot();
                                        this.axeInventorySlot = inventoryAxeSlot;
                                        InventoryUtil.swapSlot(inventoryAxeSlot, this.previousSlot);
                                        this.swappedToAxe = true;
                                        this.axeSwapTimer.reset();
                                    }
                                }

                                this.hitDelayTimer.reset();
                                this.hitDelay.reroll();
                                return false;
                            }

                            if (!this.hitDelayTimer.hasElapsedRandom(this.hitDelay)) {
                                return false;
                            }

                            PlayerInteractions.attackEntity(target);
                            if (this.stun.getValue()) {
                                PlayerInteractions.attackEntity(target);
                            }

                            if (ModuleNotificationsElement.instance.isEnabled()) {
                                Prestige.notificationManager.addNotification("AutoShieldBreaker", "Broke target's shield!", NOTIFICATION_DURATION_MS, NotificationType.INFO);
                            }

                            this.swappedToAxe = false;
                            this.breakCooldownTimer.reset();
                            this.breakCooldown.reroll();
                            return true;
                        }

                        return false;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.previousSlot = -1;
        this.axeInventorySlot = -1;
        this.swappedToAxe = false;
    }
}

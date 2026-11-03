package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.BoundingBoxEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public class AutoMace extends Module implements RotationProvider {
    private FloatSetting horizontalTargetDist;
    private FloatSetting minFallDistance;
    private BoolSetting stunSlam;
    private EnumSetting stunSlamMode;
    private KeybindSetting stunSlamBind;
    private FloatSetting chance;
    private BoolSetting scaleWithFall;
    private FloatSetting stunMinFall;
    private IntSetting axeHoldDelay;
    private BoolSetting breachCheck;
    private BoolSetting trigger;
    private BoolSetting silentAim;
    private FloatSetting maxYaw;
    private BoolSetting takeoffElytra;
    private BoolSetting swapBack;
    private IntSetting swapBackDelay;
    private BoolSetting expandHitbox;
    private FloatSetting hitboxExpansion;
    private StopWatch attackTimer = new StopWatch();
    private StopWatch stunSlamTimer = new StopWatch();
    private StopWatch swapBackTimer = new StopWatch();
    private StopWatch savedSlotTimer = new StopWatch();
    private boolean stunSlamQueued;
    private boolean attackingInternally;
    private int previousSlot = -1;
    private boolean swapBackPending;
    private boolean equippedChestplateThisTick;
    private PlayerEntity pendingAxeTarget;
    private Rotation pendingAxeRotation;
    private long axeHoldStartTime;
    private int pendingMaceSlot = -1;

    public AutoMace() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.chance
            .visibleWhen(
                (Predicate<Float>)ignoredChance -> this.stunSlam.getValue() && this.stunSlamMode.getValue().equals("Chance")
            );
        this.stunMinFall.visibleWhen((Predicate<Float>)ignoredStunMinFall -> this.stunSlam.getValue());
        this.stunSlamMode.visibleWhen((Predicate<String>)ignoredStunSlamMode -> this.stunSlam.getValue());
        this.axeHoldDelay.visibleWhen((Predicate<Integer>)ignoredAxeHoldDelay -> this.stunSlam.getValue());
        this.silentAim.visibleWhen((Predicate<Boolean>)ignoredSilentAim -> this.trigger.getValue());
        this.maxYaw
            .visibleWhen((Predicate<Float>)ignoredMaxYaw -> this.trigger.getValue() && this.silentAim.getValue());
        this.hitboxExpansion.visibleWhen((Predicate<Float>)ignoredHitboxExpansion -> this.expandHitbox.getValue());
        this.scaleWithFall
            .visibleWhen(
                (Predicate<Boolean>)ignoredScaleWithFall -> this.stunSlam.getValue() && this.stunSlamMode.getValue().equals("Chance")
            );
        this.stunSlamBind
            .visibleWhen(
                (Predicate<Integer>)ignoredStunSlamBind -> this.stunSlam.getValue() && this.stunSlamMode.getValue().equals("Hold Key")
            );
        this.swapBackDelay.visibleWhen((Predicate<Integer>)ignoredSwapBackDelay -> this.swapBack.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public Rotation provideRotation() {
        this.equippedChestplateThisTick = false;
        if (this.previousSlot != -1 && !this.swapBackPending) {
            if (this.savedSlotTimer.hasElapsed(2000.0F)) {
                this.clearSavedSlot();
            }
        }

        if (this.previousSlot != -1 && this.swapBackPending && !this.stunSlamQueued && this.pendingAxeTarget == null) {
            StopWatch swapBackStopWatch = this.swapBackTimer;
            float swapBackDelayMs = this.swapBackDelay.getValue().intValue();
            if (swapBackStopWatch.hasElapsed(swapBackDelayMs)) {
                this.swapBackToSavedSlot();
            }
        }

        if (this.pendingAxeTarget != null && !this.stunSlam.getValue()) {
            int returnSlot = this.swapBack.getValue() && this.previousSlot != -1 ? this.previousSlot : this.pendingMaceSlot;
            this.clearPendingStunSlam();
            this.clearSavedSlot();
            if (returnSlot >= 0 && InventoryUtil.getSelectedSlot() != returnSlot) {
                InventoryUtil.selectSlot(returnSlot);
            }
        }

        if (this.pendingAxeTarget != null) {
            return this.processPendingStunSlam();
        } else if (this.swapElytraForChestplate()) {
            return null;
        } else {
            int maceSlot = this.findBestMaceSlot();
            if (maceSlot == -1) {
                this.stunSlamQueued = false;
                return null;
            } else if (mc.player.fallDistance <= this.minFallDistance.getValue().floatValue()) {
                return null;
            } else if (this.stunSlam.getValue() && this.stunSlamQueued) {
                int axeSlot = this.findAxeSlot();
                if (axeSlot == -1) {
                    this.stunSlamQueued = false;
                    return null;
                } else {
                    Rotation stunRotation = null;
                    PlayerEntity stunTarget;
                    if (mc.crosshairTarget instanceof EntityHitResult stunCrosshairHit && stunCrosshairHit.getEntity() instanceof PlayerEntity) {
                        stunTarget = (PlayerEntity)stunCrosshairHit.getEntity();
                    } else {
                        stunTarget = TargetSelector.getClosestEnemyInFov(this.maxYaw.getValue());
                        if (stunTarget == null || mc.player.distanceTo(stunTarget) > 3.0F || !mc.player.canSee(stunTarget) || BlockUtil.isCobwebInLineOfSight(stunTarget)) {
                            this.stunSlamQueued = false;
                            return null;
                        }

                        stunRotation = RotationUtil.getRotationTo(stunTarget.getEyePos());
                        if (stunRotation.getYaw() - mc.player.getYaw() > this.maxYaw.getValue()
                            || stunRotation.getYaw() - mc.player.getYaw() < -this.maxYaw.getValue()) {
                            this.stunSlamQueued = false;
                            return null;
                        }
                    }

                    if (!this.hasFallenEnoughToStun()) {
                        this.stunSlamQueued = false;
                        return null;
                    } else {
                        this.stunSlamQueued = false;
                        return this.startStunSlam(stunTarget, stunRotation, axeSlot, maceSlot);
                    }
                }
            } else {
                if (mc.crosshairTarget instanceof EntityHitResult crosshairHit && crosshairHit.getEntity() instanceof PlayerEntity hitPlayer) {
                    if (this.trigger.getValue()) {
                        if (this.attackTimer.hasElapsed(800.0F)) {
                            if (TargetSelector.isBlockingWithShield(hitPlayer) && !ShieldDrain.isDraining() && this.stunSlam.getValue()) {
                                if (this.stunSlamTimer.hasElapsed(800.0F) && this.shouldStunSlam() && this.hasFallenEnoughToStun()) {
                                    int crosshairAxeSlot = this.findAxeSlot();
                                    if (crosshairAxeSlot != -1) {
                                        return this.startStunSlam(hitPlayer, null, crosshairAxeSlot, maceSlot);
                                    }
                                }
                            }

                            if (PositionUtil.isPlayerGliding()) {
                                return null;
                            }

                            this.attackingInternally = true;
                            if (InventoryUtil.getSelectedSlot() != maceSlot) {
                                this.savePreviousSlot();
                                InventoryUtil.selectSlot(maceSlot);
                            }

                            PlayerInteractions.attackEntity(hitPlayer);
                            this.attackingInternally = false;
                            this.attackTimer.reset();
                            if (this.swapBack.getValue() && this.previousSlot != -1) {
                                this.swapBackTimer.reset();
                                this.swapBackPending = true;
                            }
                        }
                    }
                } else {
                    if (this.trigger.getValue() && this.silentAim.getValue()) {
                        if (this.attackTimer.hasElapsed(800.0F)) {
                            PlayerEntity aimTarget = TargetSelector.getClosestEnemyInFov(this.maxYaw.getValue());
                            if (aimTarget != null && !(mc.player.distanceTo(aimTarget) > 3.0F) && mc.player.canSee(aimTarget) && !BlockUtil.isCobwebInLineOfSight(aimTarget)) {
                                Rotation aimRotation = RotationUtil.getRotationTo(aimTarget.getEyePos());
                                if (!(aimRotation.getYaw() - mc.player.getYaw() > this.maxYaw.getValue())
                                    && !(aimRotation.getYaw() - mc.player.getYaw() < -this.maxYaw.getValue())) {
                                    if (!this.stunSlamQueued
                                        && TargetSelector.isBlockingWithShield(aimTarget)
                                        && !ShieldDrain.isDraining()
                                        && this.stunSlam.getValue()) {
                                        if (this.stunSlamTimer.hasElapsed(800.0F) && this.shouldStunSlam() && this.hasFallenEnoughToStun()) {
                                            int aimAxeSlot = this.findAxeSlot();
                                            if (aimAxeSlot == -1) {
                                                this.stunSlamQueued = false;
                                                return null;
                                            }

                                            return this.startStunSlam(aimTarget, aimRotation, aimAxeSlot, maceSlot);
                                        }
                                    }

                                    if (PositionUtil.isPlayerGliding()) {
                                        return null;
                                    }

                                    if (InventoryUtil.getSelectedSlot() != maceSlot) {
                                        this.savePreviousSlot();
                                        InventoryUtil.selectSlot(maceSlot);
                                    }

                                    PlayerInteractions.attackEntity(aimTarget);
                                    this.attackTimer.reset();
                                    if (this.swapBack.getValue() && this.previousSlot != -1) {
                                        this.swapBackTimer.reset();
                                        this.swapBackPending = true;
                                    }

                                    return aimRotation;
                                }

                                return null;
                            }

                            return null;
                        }
                    }

                    this.stunSlamQueued = false;
                }

                return null;
            }
        }
    }

    private Rotation startStunSlam(PlayerEntity target, Rotation rotation, int axeSlot, int maceSlot) {
        if (InventoryUtil.getSelectedSlot() != axeSlot) {
            this.savePreviousSlot();
            InventoryUtil.selectSlot(axeSlot);
        }

        int holdTicks = this.axeHoldDelay.getValue();
        if (holdTicks <= 0) {
            this.attackingInternally = true;
            PlayerInteractions.attackEntity(target);
            this.attackingInternally = false;
            this.stunSlamTimer.reset();
            return rotation;
        } else {
            this.pendingAxeTarget = target;
            this.pendingAxeRotation = rotation;
            this.pendingMaceSlot = maceSlot;
            this.axeHoldStartTime = System.currentTimeMillis();
            return rotation;
        }
    }

    private Rotation processPendingStunSlam() {
        if (this.pendingAxeTarget != null && this.pendingAxeTarget.isAlive() && mc.player != null && mc.world != null && !(mc.player.distanceTo(this.pendingAxeTarget) > 6.0F)) {
            if ((System.currentTimeMillis() - this.axeHoldStartTime) < (this.axeHoldDelay.getValue().intValue() * 50L)) {
                return this.pendingAxeRotation;
            } else {
                PlayerEntity target = this.pendingAxeTarget;
                Rotation rotation = this.pendingAxeRotation;
                int maceSlot = this.pendingMaceSlot;
                this.attackingInternally = true;
                PlayerInteractions.attackEntity(target);
                this.attackingInternally = false;
                this.stunSlamTimer.reset();
                if (maceSlot >= 0 && InventoryUtil.getSelectedSlot() != maceSlot) {
                    InventoryUtil.selectSlot(maceSlot);
                }

                this.clearPendingStunSlam();
                return rotation;
            }
        } else {
            int returnSlot = this.swapBack.getValue() && this.previousSlot != -1 ? this.previousSlot : this.pendingMaceSlot;
            this.clearPendingStunSlam();
            this.clearSavedSlot();
            if (returnSlot >= 0 && InventoryUtil.getSelectedSlot() != returnSlot) {
                InventoryUtil.selectSlot(returnSlot);
            }

            return null;
        }
    }

    private void clearPendingStunSlam() {
        this.pendingAxeTarget = null;
        this.pendingAxeRotation = null;
        this.axeHoldStartTime = 0L;
        this.pendingMaceSlot = -1;
    }

    private void savePreviousSlot() {
        if (this.swapBack.getValue() && this.previousSlot == -1) {
            if (!this.equippedChestplateThisTick) {
                this.previousSlot = InventoryUtil.getSelectedSlot();
                this.savedSlotTimer.reset();
            }
        }
    }

    private void swapBackToSavedSlot() {
        int savedSlot = this.previousSlot;
        this.clearSavedSlot();
        if (savedSlot >= 0 && InventoryUtil.getSelectedSlot() != savedSlot) {
            InventoryUtil.selectSlot(savedSlot);
        }
    }

    private void clearSavedSlot() {
        this.previousSlot = -1;
        this.swapBackPending = false;
    }

    private boolean shouldStunSlam() {
        if (!this.stunSlam.getValue()) {
            return false;
        } else if (!this.stunSlamMode.getValue().equals("Hold Key")) {
            float rollChance = this.chance.getValue();
            if (this.scaleWithFall.getValue()) {
                float fallScale = Math.max(0.0F, Math.min(1.0F, (((float)mc.player.fallDistance) - Math.max(this.minFallDistance.getValue(), this.stunMinFall.getValue())) / 10.0F));
                rollChance *= fallScale;
            }

            return Math.random() * 100.0 < rollChance;
        } else {
            return this.stunSlamBind.getValue() != -1 && this.stunSlamBind.isPressed();
        }
    }

    private boolean hasFallenEnoughToStun() {
        float requiredFall = Math.max(this.minFallDistance.getValue(), this.stunMinFall.getValue());
        return mc.player.fallDistance >= requiredFall;
    }

    @Subscribe
    public void onBoundingBox(BoundingBoxEvent event) {
        if (this.expandHitbox.getValue() && !(mc.player.fallDistance <= this.minFallDistance.getValue().floatValue())) {
            PlayerEntity target = this.findClosestHorizontalTarget(this.horizontalTargetDist.getValue());
            if (target != null && target.equals(event.getEntity())) {
                event.setBox(event.getBox().expand(this.hitboxExpansion.getValue().floatValue() / 5.0));
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (!this.attackingInternally) {
            PlayerEntity target = this.findClosestHorizontalTarget(this.horizontalTargetDist.getValue());
            if (!this.stunSlamQueued
                && mc.player.fallDistance > this.minFallDistance.getValue().floatValue()
                && target != null
                && TargetSelector.isBlockingWithShield(target)
                && mc.player.getMainHandStack().getItem() instanceof AxeItem
                && this.stunSlam.getValue()) {
                if (this.stunSlamTimer.hasElapsed(800.0F) && this.shouldStunSlam() && this.hasFallenEnoughToStun()) {
                    if (this.findAxeSlot() != -1) {
                        this.stunSlamQueued = true;
                        return;
                    }
                }
            }

            int maceSlot = this.findBestMaceSlot();
            if (maceSlot != -1
                && InventoryUtil.getSelectedSlot() != maceSlot
                && mc.crosshairTarget instanceof EntityHitResult
                && !(mc.player.fallDistance <= this.minFallDistance.getValue().floatValue())) {
                this.savePreviousSlot();
                InventoryUtil.selectSlot(maceSlot);
                if (this.swapBack.getValue() && this.previousSlot != -1) {
                    this.swapBackTimer.reset();
                    this.swapBackPending = true;
                }
            }
        }
    }

    private PlayerEntity findClosestHorizontalTarget(float maxDistance) {
        float maxDistanceSq = maxDistance * maxDistance;
        PlayerEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive() && !player.equals(mc.player) && !Prestige.friendManager.isFriend(player.getName().getString())) {
                Vec3d levelPos = new Vec3d(player.getX(), mc.player.getY(), player.getZ());
                double distanceSq = mc.player.squaredDistanceTo(levelPos);
                if (!(distanceSq > maxDistanceSq) && distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = player;
                }
            }
        }

        return closest;
    }

    private boolean swapElytraForChestplate() {
        if (!this.takeoffElytra.getValue()) {
            return false;
        } else if (mc.player.fallDistance < this.minFallDistance.getValue().floatValue()) {
            return false;
        } else {
            PlayerEntity target = this.findClosestHorizontalTarget(this.horizontalTargetDist.getValue());
            if (target == null) {
                return false;
            } else if (!target.isAlive() || mc.player.distanceTo(target) > (TargetSelector.isBlockingWithShield(target) ? 8.0F : 6.0F)) {
                return false;
            } else if (!PositionUtil.isPlayerGliding()) {
                return false;
            } else if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                return false;
            } else {
                Integer chestplateSlot = InventoryUtil.findHotbarSlot(Items.NETHERITE_CHESTPLATE);
                if (chestplateSlot != null) {
                    InventoryUtil.selectSlot(chestplateSlot);
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    this.equippedChestplateThisTick = true;
                    return false;
                } else {
                    chestplateSlot = InventoryUtil.findHotbarSlot(Items.DIAMOND_CHESTPLATE);
                    if (chestplateSlot != null) {
                        InventoryUtil.selectSlot(chestplateSlot);
                        PlayerInteractions.interactItem(Hand.MAIN_HAND);
                        this.equippedChestplateThisTick = true;
                        return false;
                    } else {
                        List inventorySlots = InventoryUtil.findInventorySlots(Items.NETHERITE_CHESTPLATE, false);
                        if (!inventorySlots.isEmpty()) {
                            int netheriteSlot = (Integer)inventorySlots.getFirst();
                            InventoryUtil.clickSlot(netheriteSlot, 0, SlotActionType.PICKUP);
                            InventoryUtil.clickSlot(6, 0, SlotActionType.PICKUP);
                            InventoryUtil.clickSlot(netheriteSlot, 0, SlotActionType.PICKUP);
                            return true;
                        } else {
                            inventorySlots = InventoryUtil.findInventorySlots(Items.DIAMOND_CHESTPLATE, false);
                            if (!inventorySlots.isEmpty()) {
                                int diamondSlot = (Integer)inventorySlots.getFirst();
                                InventoryUtil.clickSlot(diamondSlot, 0, SlotActionType.PICKUP);
                                InventoryUtil.clickSlot(6, 0, SlotActionType.PICKUP);
                                InventoryUtil.clickSlot(diamondSlot, 0, SlotActionType.PICKUP);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.clearPendingStunSlam();
        this.clearSavedSlot();
        this.equippedChestplateThisTick = false;
    }

    public int findAxeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }

        return -1;
    }

    public int findBestMaceSlot() {
        ArrayList maceSlots = new ArrayList();

        for (int slot = 0; slot <= 8; slot++) {
            if (InventoryUtil.isMace(mc.player.getInventory().getStack(slot))) {
                maceSlots.add(slot);
            }
        }

        int bestLevel = -1;
        int bestSlot = -1;

        for (int maceSlot : (Iterable<Integer>)(maceSlots)) {
            int enchantLevel;
            if (this.breachCheck.getValue() && mc.player.fallDistance <= 7.0) {
                enchantLevel = this.getBreachLevel(maceSlot);
            } else {
                enchantLevel = this.getDensityLevel(maceSlot);
            }

            int level = enchantLevel;
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = maceSlot;
            }
        }

        return bestSlot;
    }

    public int getDensityLevel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack != null) {
            float level = EnchantmentHelper.getLevel(
                mc.world.getRegistryManager().getOptional(Enchantments.DENSITY.getRegistryRef()).get().getEntry(Enchantments.DENSITY.getValue()).get(), stack
            );
            return (int)level;
        } else {
            return 0;
        }
    }

    public int getBreachLevel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack != null) {
            float level = EnchantmentHelper.getLevel(
                mc.world.getRegistryManager().getOptional(Enchantments.BREACH.getRegistryRef()).get().getEntry(Enchantments.BREACH.getValue()).get(), stack
            );
            return (int)level;
        } else {
            return 0;
        }
    }
}

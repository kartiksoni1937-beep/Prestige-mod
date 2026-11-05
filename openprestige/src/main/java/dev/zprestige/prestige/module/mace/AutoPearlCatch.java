package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class AutoPearlCatch extends Module implements RotationProvider {
    private FloatSetting windChargeDelay;
    private FloatSetting movingDelay;
    private BoolSetting legitRotate;
    private FloatSetting smoothing;
    private boolean pearlThrown;
    private boolean windChargeThrown;
    private StopWatch pearlTimer = new StopWatch();
    private boolean pearlAimReady;
    private boolean windChargeReady;
    private float originalPitch;
    private Rotation lockedRotation;
    private boolean rotationSynced;
    private boolean rotationApplied;
    private boolean itemUsed;
    private boolean releasePending;
    private boolean disableAfterRelease;
    private Object lockWorld;
    private Object trackedWorld;
    private int lastUseTick = Integer.MIN_VALUE;

    public AutoPearlCatch() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.smoothing.visibleWhen((Predicate<Float>)ignored -> this.legitRotate.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (this.trackedWorld != mc.world) {
            this.resetState();
        }

        if (this.legitRotate.getValue()) {
            if (this.isUseKeyActive()) {
                this.pauseForItemUse();
            } else {
                if (!this.pearlThrown) {
                    if (RotationUtil.smoothRotatePitch(new Rotation(mc.player.getYaw(), -90.0F), this.smoothing.getValue() / 2.0F, 0.0F, 0.2F)) {
                        this.pearlAimReady = true;
                    }
                } else {
                    boolean moving = mc.player.getVelocity().x != 0.0 || mc.player.getVelocity().z != 0.0;
                    if (!this.pearlTimer.hasElapsed(moving ? this.movingDelay.getValue() : this.windChargeDelay.getValue())) {
                        return;
                    }

                    if (this.windChargeThrown) {
                        if (this.itemUsed) {
                            return;
                        }

                        if (RotationUtil.smoothRotatePitch(new Rotation(mc.player.getYaw(), this.originalPitch), this.smoothing.getValue() / 2.0F, 0.0F, 0.2F)) {
                            this.toggle();
                        }
                    } else {
                        this.windChargeReady = true;
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (this.trackedWorld != mc.world) {
            this.resetState();
        }

        boolean usingItem = this.isUseKeyActive();
        if (!Prestige.silentRotationManager.canRotate()) {
            this.resetState();
            return null;
        } else if (usingItem) {
            this.pauseForItemUse();
            return !this.itemUsed && !this.releasePending ? null : this.lockedRotation;
        } else if (this.lockedRotation == null || !this.itemUsed && !this.releasePending) {
            if (mc.isWindowFocused() && mc.currentScreen == null) {
                if (this.legitRotate.getValue()) {
                    int legitPearlSlot = this.pearlThrown ? -1 : this.findPearlSlot();
                    int legitWindChargeSlot = this.findWindChargeSlot();
                    if ((this.pearlThrown || legitPearlSlot != -1) && legitWindChargeSlot != -1) {
                        if (this.pearlAimReady) {
                            Rotation legitPearlRotation = this.lockRotation(new Rotation(mc.player.getYaw(), -90.0F));
                            if (this.throwFromSlot(legitPearlSlot)) {
                                this.pearlThrown = true;
                                this.pearlAimReady = false;
                                this.pearlTimer.reset();
                            }

                            return legitPearlRotation;
                        } else {
                            if (this.windChargeReady) {
                                EnderPearlEntity legitPearl = null;
                                int legitYoungestAge = Integer.MAX_VALUE;

                                for (Entity legitEntity : mc.world.getEntities()) {
                                    if (legitEntity instanceof EnderPearlEntity legitOwnPearl && legitOwnPearl.getOwner() == mc.player && legitOwnPearl.age < legitYoungestAge) {
                                        legitYoungestAge = legitOwnPearl.age;
                                        legitPearl = legitOwnPearl;
                                    }
                                }

                                if (legitPearl == null) {
                                    for (Entity legitFallbackEntity : mc.world.getEntities()) {
                                        if (legitFallbackEntity instanceof EnderPearlEntity legitFallbackPearl) {
                                            if (legitFallbackPearl.getOwner() == mc.player) {
                                                legitPearl = legitFallbackPearl;
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (legitPearl != null) {
                                    Vec3d legitVelocity = mc.player.getVelocity();
                                    double legitYawRadians = Math.toRadians(mc.player.getYaw());
                                    Vec3d legitForward = new Vec3d(-Math.sin(legitYawRadians), 0.0, Math.cos(legitYawRadians)).normalize();
                                    float legitForwardSpeed = (float)(legitVelocity.x * legitForward.x + legitVelocity.z * legitForward.z);
                                    Rotation legitWindRotation = this.lockRotation(
                                        new Rotation(mc.player.getYaw(), -90.0F + legitForwardSpeed * 1.6F)
                                    );
                                    if (this.throwFromSlot(legitWindChargeSlot)) {
                                        this.windChargeThrown = true;
                                        this.windChargeReady = false;
                                    }

                                    return new Rotation(legitWindRotation.getYaw(), legitWindRotation.getPitch());
                                }

                                if (this.hasPearlTimedOut()) {
                                    this.toggle();
                                }
                            }

                            return null;
                        }
                    } else {
                        this.toggle();
                        return null;
                    }
                } else {
                    boolean moving = mc.player.getVelocity().x != 0.0 || mc.player.getVelocity().z != 0.0;
                    int pearlSlot = this.pearlThrown ? -1 : this.findPearlSlot();
                    int windChargeSlot = this.findWindChargeSlot();
                    if ((this.pearlThrown || pearlSlot != -1) && windChargeSlot != -1) {
                        if (!this.pearlThrown) {
                            Rotation pearlRotation = this.lockRotation(new Rotation(mc.player.getYaw(), -90.0F));
                            if (this.throwFromSlot(pearlSlot)) {
                                this.pearlThrown = true;
                                this.pearlTimer.reset();
                            }

                            return pearlRotation;
                        } else {
                            if (!this.pearlTimer.hasElapsed(moving ? this.movingDelay.getValue() : this.windChargeDelay.getValue())) {
                                return new Rotation(mc.player.getYaw(), -90.0F);
                            } else {
                                EnderPearlEntity pearl = null;
                                int youngestAge = Integer.MAX_VALUE;

                                for (Entity entity : mc.world.getEntities()) {
                                    if (entity instanceof EnderPearlEntity ownPearl && ownPearl.getOwner() == mc.player && ownPearl.age < youngestAge) {
                                        youngestAge = ownPearl.age;
                                        pearl = ownPearl;
                                    }
                                }

                                if (pearl == null) {
                                    for (Entity fallbackEntity : mc.world.getEntities()) {
                                        if (fallbackEntity instanceof EnderPearlEntity fallbackPearl) {
                                            if (fallbackPearl.getOwner() == mc.player) {
                                                pearl = fallbackPearl;
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (pearl != null) {
                                    Vec3d velocity = mc.player.getVelocity();
                                    double yawRadians = Math.toRadians(mc.player.getYaw());
                                    Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize();
                                    float forwardSpeed = (float)(velocity.x * forward.x + velocity.z * forward.z);
                                    Rotation windRotation = this.lockRotation(
                                        new Rotation(mc.player.getYaw(), -90.0F + forwardSpeed * 1.6F)
                                    );
                                    if (this.throwFromSlot(windChargeSlot)) {
                                        this.disableAfterRelease = true;
                                    }

                                    return new Rotation(windRotation.getYaw(), windRotation.getPitch());
                                } else {
                                    if (this.hasPearlTimedOut()) {
                                        this.toggle();
                                    }

                                    return null;
                                }
                            }
                        }
                    } else {
                        this.toggle();
                        return null;
                    }
                }
            } else {
                if (!this.itemUsed && !this.releasePending) {
                    this.releaseRotationLock();
                }

                return null;
            }
        } else {
            return this.lockedRotation;
        }
    }

    private boolean hasPearlTimedOut() {
        float longestDelay = Math.max(this.windChargeDelay.getValue(), this.movingDelay.getValue());
        return this.pearlTimer.hasElapsed(longestDelay + 1500.0F);
    }

    private boolean isUseKeyActive() {
        int currentAge = mc.player.age;
        if (mc.player.isUsingItem() || mc.options.useKey.isPressed()) {
            this.lastUseTick = currentAge;
            return true;
        } else if (this.lastUseTick == Integer.MIN_VALUE) {
            return false;
        } else {
            int ticksSinceUse = currentAge - this.lastUseTick;
            if (ticksSinceUse >= 0 && ticksSinceUse <= 1) {
                return true;
            } else {
                this.lastUseTick = Integer.MIN_VALUE;
                return false;
            }
        }
    }

    private void pauseForItemUse() {
        if (!this.itemUsed && !this.releasePending) {
            this.releaseRotationLock();
        }

        this.pearlAimReady = false;
        this.windChargeReady = false;
    }

    private Rotation lockRotation(Rotation rotation) {
        if (this.lockedRotation == null) {
            this.lockedRotation = new Rotation(rotation.getYaw(), rotation.getPitch());
            this.rotationSynced = false;
            this.rotationApplied = false;
            this.itemUsed = false;
            this.lockWorld = mc.world;
        }

        Prestige.silentRotationManager.tryLockRotation(this, this.lockedRotation);
        return this.lockedRotation;
    }

    private boolean throwFromSlot(int slot) {
        if (this.lockedRotation != null && this.rotationSynced && !this.itemUsed && !Prestige.silentRotationManager.hasActedThisTick()) {
            InventoryUtil.selectSlot(slot);
            float previousYaw = mc.player.getYaw();
            float previousPitch = mc.player.getPitch();

            boolean accepted;
            try {
                mc.player.setYaw(this.lockedRotation.getYaw());
                mc.player.setPitch(this.lockedRotation.getPitch());
                accepted = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted();
                Prestige.silentRotationManager.setActedThisTick(true);
                this.itemUsed = true;
                if (accepted) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } finally {
                mc.player.setYaw(previousYaw);
                mc.player.setPitch(previousPitch);
            }

            return accepted;
        } else {
            return false;
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (this.lockedRotation != null) {
            if (event.getPhase() == EventPhase.PRE) {
                if (!Prestige.silentRotationManager.tryLockRotation(this, this.lockedRotation)) {
                    this.rotationApplied = false;
                } else {
                    event.setYaw(this.lockedRotation.getYaw());
                    event.setPitch(this.lockedRotation.getPitch());
                    this.rotationApplied = true;
                }
            } else if (event.getPhase() == EventPhase.POST && this.rotationApplied) {
                this.rotationApplied = false;
                if (Prestige.silentRotationManager.isLockHeldBy(this)) {
                    if (this.itemUsed) {
                        this.itemUsed = false;
                        this.releasePending = true;
                    } else if (this.releasePending) {
                        this.releasePending = false;
                        boolean shouldDisable = this.disableAfterRelease;
                        this.releaseRotationLock();
                        if (shouldDisable && this.isEnabled()) {
                            this.toggle();
                        }
                    } else {
                        this.rotationSynced = true;
                    }
                }
            }
        }
    }

    private void releaseRotationLock() {
        Prestige.silentRotationManager.releaseLock(this);
        this.lockedRotation = null;
        this.lockWorld = null;
        this.rotationSynced = false;
        this.rotationApplied = false;
        this.itemUsed = false;
        this.releasePending = false;
        this.disableAfterRelease = false;
    }

    private void resetState() {
        boolean worldChanged = this.trackedWorld != mc.world;
        this.releaseRotationLock();
        this.pearlThrown = false;
        this.windChargeThrown = false;
        this.pearlAimReady = false;
        this.windChargeReady = false;
        this.pearlTimer.reset();
        this.trackedWorld = mc.world;
        if (worldChanged) {
            this.lastUseTick = Integer.MIN_VALUE;
        }

        if (mc.player != null) {
            this.originalPitch = mc.player.getPitch();
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.lastUseTick = Integer.MIN_VALUE;
        this.resetState();
    }

    private int findPearlSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (!mc.player.getInventory().getStack(slot).isEmpty() && mc.player.getInventory().getStack(slot).getItem() == Items.ENDER_PEARL) {
                return slot;
            }
        }

        return -1;
    }

    private int findWindChargeSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (!mc.player.getInventory().getStack(slot).isEmpty() && isWindCharge(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    public static boolean isWindCharge(ItemStack stack) {
        return stack.getItem().equals(Items.WIND_CHARGE);
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.releaseRotationLock();
        this.trackedWorld = null;
        this.lastUseTick = Integer.MIN_VALUE;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }
}

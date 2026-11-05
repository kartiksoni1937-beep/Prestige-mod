package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.network.PacketSender;
import dev.zprestige.prestige.reflect.MethodAccessor;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.type.FastPearlCatchStage;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class FastPearlCatch extends Module implements RotationProvider {
    private EnumSetting mode;
    private FloatSetting pearlAngle;
    private FloatSetting offhandPearlAngle;
    private FloatSetting offhandWindChargeAngle;
    private FloatSetting windChargeAngle;
    private BoolSetting swapBack;
    private BoolSetting legitRotate;
    private FloatSetting smoothing;
    private boolean pearlThrown;
    private boolean windChargeThrown;
    private StopWatch pearlTimer = new StopWatch();
    private boolean pearlAimReady;
    private boolean windChargeReady;
    private float originalPitch;
    private float originalYaw;
    private Rotation lockedRotation;
    private boolean rotationSynced;
    private boolean rotationApplied;
    private boolean itemUsed;
    private boolean disableAfterRelease;
    private Object lockWorld;
    private Object trackedWorld;
    private int lastUseTick = Integer.MIN_VALUE;
    private FastPearlCatchStage offhandStage = FastPearlCatchStage.PREP;
    private int originalSlot;
    private int offhandSwapSlot = -1;

    public FastPearlCatch() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.pearlAngle.visibleWhen((Predicate<Float>)ignoredPearlAngle -> this.mode.getValue().equals("Swap"));
        this.offhandWindChargeAngle.visibleWhen((Predicate<Float>)ignoredOffhandWindChargeAngle -> this.mode.getValue().equals("Offhand"));
        this.offhandPearlAngle.visibleWhen((Predicate<Float>)ignoredOffhandPearlAngle -> this.mode.getValue().equals("Offhand"));
        this.legitRotate.visibleWhen((Predicate<Boolean>)ignoredLegitRotate -> this.mode.getValue().equals("Swap"));
        this.smoothing.visibleWhen((Predicate<Float>)ignoredSmoothing -> this.legitRotate.getValue());
        this.swapBack.visibleWhen((Predicate<Boolean>)ignoredSwapBack -> this.mode.getValue().equals("Swap"));
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

        if (this.legitRotate.getValue() && this.mode.getValue().equals("Swap")) {
            if (this.isUseKeyActive()) {
                this.pauseForItemUse();
            } else {
                if (!this.pearlThrown) {
                    if (RotationUtil.smoothRotate(new Rotation(this.originalYaw, -this.pearlAngle.getValue()), this.smoothing.getValue() / 2.0F, 0.0F, 0.2F)) {
                        this.pearlAimReady = true;
                    }
                } else if (this.windChargeThrown) {
                    if (this.itemUsed) {
                        return;
                    }

                    if (RotationUtil.smoothRotate(new Rotation(this.originalYaw, this.originalPitch), this.smoothing.getValue() / 2.0F, 0.0F, 0.2F)) {
                        this.toggle();
                    }
                } else {
                    this.windChargeReady = true;
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
            return this.itemUsed ? this.lockedRotation : null;
        } else if (this.lockedRotation != null && this.itemUsed) {
            return this.lockedRotation;
        } else if (mc.isWindowFocused() && mc.currentScreen == null) {
            if (this.legitRotate.getValue() && this.mode.getValue().equals("Swap")) {
                int legitPearlSlot = this.pearlThrown ? -1 : this.findPearlSlot();
                int legitWindChargeSlot = this.findWindChargeSlot();
                if ((this.pearlThrown || legitPearlSlot != -1) && legitWindChargeSlot != -1) {
                    if (this.pearlAimReady) {
                        Rotation legitPearlRotation = this.lockRotation(new Rotation(this.originalYaw, -this.pearlAngle.getValue()));
                        if (this.throwFromSlot(legitPearlSlot, Hand.MAIN_HAND)) {
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
                                double legitYawRadians = Math.toRadians(this.originalYaw);
                                Vec3d legitForward = new Vec3d(-Math.sin(legitYawRadians), 0.0, Math.cos(legitYawRadians)).normalize();
                                float legitForwardSpeed = (float)(legitVelocity.x * legitForward.x + legitVelocity.z * legitForward.z);
                                Rotation legitWindRotation = this.lockRotation(new Rotation(this.originalYaw, -this.windChargeAngle.getValue() + legitForwardSpeed * 1.6F));
                                if (this.throwFromSlot(legitWindChargeSlot, Hand.MAIN_HAND)) {
                                    this.windChargeThrown = true;
                                    this.windChargeReady = false;
                                    if (this.swapBack.getValue() && this.originalSlot != legitWindChargeSlot) {
                                        InventoryUtil.selectSlot(this.originalSlot);
                                    }
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
            } else if (this.mode.getValue().equals("Offhand")) {
                return this.runOffhandMode();
            } else {
                int pearlSlot = this.pearlThrown ? -1 : this.findPearlSlot();
                int windChargeSlot = this.findWindChargeSlot();
                if ((this.pearlThrown || pearlSlot != -1) && windChargeSlot != -1) {
                    if (!this.pearlThrown) {
                        Rotation pearlRotation = this.lockRotation(new Rotation(this.originalYaw, -this.pearlAngle.getValue()));
                        if (this.throwFromSlot(pearlSlot, Hand.MAIN_HAND)) {
                            this.pearlThrown = true;
                            this.pearlTimer.reset();
                        }

                        return pearlRotation;
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
                            double yawRadians = Math.toRadians(this.originalYaw);
                            Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize();
                            float forwardSpeed = (float)(velocity.x * forward.x + velocity.z * forward.z);
                            Rotation windRotation = this.lockRotation(new Rotation(this.originalYaw, -this.windChargeAngle.getValue() + forwardSpeed * 1.6F));
                            if (this.throwFromSlot(windChargeSlot, Hand.MAIN_HAND)) {
                                if (this.swapBack.getValue() && this.originalSlot != windChargeSlot) {
                                    InventoryUtil.selectSlot(this.originalSlot);
                                }

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
                } else {
                    this.toggle();
                    return null;
                }
            }
        } else {
            if (!this.itemUsed) {
                this.releaseRotationLock();
            }

            return null;
        }
    }

    private boolean hasPearlTimedOut() {
        return this.pearlTimer.hasElapsed(1500.0F);
    }

    private Rotation runOffhandMode() {
        switch (this.offhandStage) {
            case PREP:
                if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return null;
                } else {
                    int pearlSlot = this.findPearlSlot();
                    int windChargeSlot = this.findWindChargeSlot();
                    boolean windChargeInOffhand = InventoryUtil.isHoldingOffhand(Items.WIND_CHARGE);
                    if (pearlSlot != -1 && (windChargeInOffhand || windChargeSlot != -1)) {
                        this.originalSlot = InventoryUtil.getSelectedSlot();
                        this.offhandSwapSlot = -1;
                        if (!windChargeInOffhand) {
                            if (InventoryUtil.getSelectedSlot() != windChargeSlot) {
                                InventoryUtil.selectSlot(windChargeSlot);
                                this.syncSelectedSlot();
                            }

                            PacketSender.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                            this.offhandSwapSlot = windChargeSlot;
                        }

                        if (InventoryUtil.getSelectedSlot() != pearlSlot) {
                            InventoryUtil.selectSlot(pearlSlot);
                            this.syncSelectedSlot();
                        }

                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.offhandStage = FastPearlCatchStage.PEARL;
                        return this.lockRotation(new Rotation(this.originalYaw, -this.offhandPearlAngle.getValue()));
                    }

                    this.toggle();
                    return null;
                }
            case PEARL:
                int currentPearlSlot = this.findPearlSlot();
                if (currentPearlSlot == -1) {
                    this.toggle();
                    return null;
                }

                Rotation pearlRotation = this.lockRotation(new Rotation(this.originalYaw, -this.offhandPearlAngle.getValue()));
                if (this.throwFromSlot(currentPearlSlot, Hand.MAIN_HAND)) {
                    this.pearlTimer.reset();
                    this.offhandStage = FastPearlCatchStage.WIND;
                }

                return pearlRotation;
            case WIND:
                if (this.findThrownPearl() == null) {
                    if (this.hasPearlTimedOut()) {
                        this.toggle();
                    }

                    return null;
                } else {
                    Vec3d velocity = mc.player.getVelocity();
                    double yawRadians = Math.toRadians(this.originalYaw);
                    Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize();
                    float forwardSpeed = (float)(velocity.x * forward.x + velocity.z * forward.z);
                    Rotation windRotation = this.lockRotation(new Rotation(this.originalYaw, -this.offhandWindChargeAngle.getValue() + forwardSpeed * 1.6F));
                    if (!InventoryUtil.isHoldingOffhand(Items.WIND_CHARGE)) {
                        if (this.hasPearlTimedOut()) {
                            this.toggle();
                        }

                        return new Rotation(windRotation.getYaw(), windRotation.getPitch());
                    }

                    if (this.throwFromSlot(-1, Hand.OFF_HAND)) {
                        this.offhandStage = FastPearlCatchStage.RESTORE;
                    }

                    return new Rotation(windRotation.getYaw(), windRotation.getPitch());
                }
            case RESTORE:
                if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return null;
                }

                if (this.offhandSwapSlot != -1) {
                    if (InventoryUtil.getSelectedSlot() != this.offhandSwapSlot) {
                        InventoryUtil.selectSlot(this.offhandSwapSlot);
                        this.syncSelectedSlot();
                    }

                    PacketSender.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                }

                if (InventoryUtil.getSelectedSlot() != this.originalSlot) {
                    InventoryUtil.selectSlot(this.originalSlot);
                    this.syncSelectedSlot();
                }

                Prestige.silentRotationManager.setActedThisTick(true);
                this.toggle();
                return null;
            default:
                return null;
        }
    }

    private void syncSelectedSlot() {
        try {
            MethodAccessor.forInstance(mc.interactionManager, "syncSelectedSlot", new Class[0])
                .invoke(mc.interactionManager, new Object[0]);
        } catch (Throwable ignored) {
        }
    }

    private EnderPearlEntity findThrownPearl() {
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
                if (fallbackEntity instanceof EnderPearlEntity fallbackPearl && fallbackPearl.getOwner() == mc.player) {
                    pearl = fallbackPearl;
                    break;
                }
            }
        }

        return pearl;
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
        if (!this.itemUsed) {
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

    private boolean throwFromSlot(int slot, Hand hand) {
        if (this.lockedRotation != null && this.rotationSynced && !this.itemUsed && !Prestige.silentRotationManager.hasActedThisTick()) {
            if (slot >= 0) {
                InventoryUtil.selectSlot(slot);
            }

            float previousYaw = mc.player.getYaw();
            float previousPitch = mc.player.getPitch();

            boolean accepted;
            try {
                mc.player.setYaw(this.lockedRotation.getYaw());
                mc.player.setPitch(this.lockedRotation.getPitch());
                accepted = mc.interactionManager.interactItem(mc.player, hand).isAccepted();
                Prestige.silentRotationManager.setActedThisTick(true);
                this.itemUsed = true;
                if (accepted) {
                    mc.player.swingHand(hand);
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
        this.disableAfterRelease = false;
    }

    private void resetState() {
        boolean worldChanged = this.trackedWorld != mc.world;
        this.releaseRotationLock();
        this.pearlThrown = false;
        this.windChargeThrown = false;
        this.pearlAimReady = false;
        this.windChargeReady = false;
        this.offhandStage = FastPearlCatchStage.PREP;
        this.offhandSwapSlot = -1;
        this.pearlTimer.reset();
        this.trackedWorld = mc.world;
        if (worldChanged) {
            this.lastUseTick = Integer.MIN_VALUE;
        }

        if (mc.player != null) {
            this.originalPitch = mc.player.getPitch();
            this.originalYaw = mc.player.getYaw();
            this.originalSlot = InventoryUtil.getSelectedSlot();
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

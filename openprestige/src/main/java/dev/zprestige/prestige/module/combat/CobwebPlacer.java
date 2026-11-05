package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.SwapBackTracker;
import dev.zprestige.prestige.type.SwapQueueResult;
import dev.zprestige.prestige.type.CobwebPlaceState;
import dev.zprestige.prestige.world.CobwebSwitchBackTask;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CobwebPlacer extends Module implements RotationProvider {
    private EnumSetting mode;
    private FloatSetting targetRange;
    private RangeSetting delay;
    private IntSetting leadTicks;
    private BoolSetting onlyWhenFalling;
    private IntSetting maxWebsPerFall;
    private BoolSetting silent;
    private EnumSetting switchBack;
    private IntSetting switchSlot;
    private EnumSetting swapMode;
    private BoolSetting fromInventory;
    private BoolSetting headTrap;
    private EnumSetting autoSwitchBack;
    private IntSetting autoSwitchSlot;
    private BoolSetting render;
    private ColorSetting renderColor;
    private StopWatch delayTimer = new StopWatch();
    private int manualStage = 0;
    private Integer manualOriginalSlot = null;
    private Vec3d predictedPlacePos = null;
    private UUID fallTargetUuid = null;
    private int websThisFall = 0;
    private int originalSlot = -1;
    private int expectedSlot = -1;
    private int switchBackSlot = -1;
    public boolean switchBackPending = false;
    public boolean switchBackScheduled = false;
    private Object swapWorld = null;

    public CobwebPlacer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.silent.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Manual"));
        this.switchSlot
            .visibleWhen(
                (Predicate<Integer>)ignored -> this.mode.getValue().equals("Manual")
                        && !this.silent.getValue()
                        && this.switchBack.getValue().equals("Slot")
            );
        this.switchBack
            .visibleWhen(
                (Predicate<String>)ignored -> this.mode.getValue().equals("Manual") && !this.silent.getValue()
            );
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public boolean isPersistent() {
        return "Auto".equals(this.mode.getValue());
    }

    @Override
    public void onEnable() {
        this.manualStage = 0;
        this.manualOriginalSlot = null;
        this.predictedPlacePos = null;
        this.fallTargetUuid = null;
        this.websThisFall = 0;
        if (!this.switchBackPending) {
            this.releaseSwap();
        } else if (this.swapWorld != mc.world) {
            this.releaseSwap();
        }

        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.finishSwitchBack();
        this.manualStage = 0;
        this.manualOriginalSlot = null;
        this.predictedPlacePos = null;
        this.fallTargetUuid = null;
        this.websThisFall = 0;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (!this.mode.getValue().equals("Auto")) {
            if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
                if (!Prestige.silentRotationManager.hasActedThisTick()) {
                    if (this.delayTimer.hasElapsedRandom(this.delay)) {
                        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
                            if (mc.world.getBlockState(blockHit.getBlockPos()).isReplaceable()) {
                                this.toggle();
                            } else {
                                if (this.silent.getValue()) {
                                    if (InventoryUtil.runWithBlock(Blocks.COBWEB, () -> PlayerInteractions.interactBlock(blockHit), this.silent.getValue())) {
                                        this.toggle();
                                    }
                                } else {
                                    switch (this.manualStage) {
                                        case 0:
                                            this.manualOriginalSlot = InventoryUtil.getSelectedSlot();
                                            if (!InventoryUtil.switchToBlock(Blocks.COBWEB)) {
                                                this.toggle();
                                                return;
                                            }

                                            this.advanceManualStage();
                                            break;
                                        case 1:
                                            PlayerInteractions.interactBlock(blockHit);
                                            this.advanceManualStage();
                                            break;
                                        case 2:
                                            if (this.switchBack.getValue().equals("Slot")) {
                                                InventoryUtil.selectSlot(this.switchSlot.getValue() - 1);
                                            } else if (this.switchBack.getValue().equals("Original")) {
                                                InventoryUtil.selectSlot(this.manualOriginalSlot);
                                            }

                                            this.toggle();
                                    }
                                }
                            }
                        } else {
                            this.toggle();
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.mode.getValue().equals("Auto")) {
            this.predictedPlacePos = null;
            this.restoreOriginalSlot();
            this.processSwitchBack();
            return null;
        } else if (mc.player != null && mc.world != null) {
            if (this.originalSlot == -1
                || this.swapWorld == mc.world
                    && SwapBackTracker.isOwner(this)
                    && (this.expectedSlot == -1 || InventoryUtil.getSelectedSlot() == this.expectedSlot)) {
                if (this.processSwitchBack()) {
                    return null;
                } else if (mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
                    return this.restoreAndSkip();
                } else if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return null;
                } else if (!this.delayTimer.hasElapsedRandom(this.delay)) {
                    return null;
                } else {
                    PlayerEntity enemy = TargetSelector.getClosestEnemy();
                    if (enemy == null || mc.player.distanceTo(enemy) > this.targetRange.getValue()) {
                        this.predictedPlacePos = null;
                        return this.restoreAndSkip();
                    } else if (BlockUtil.isCobwebInLineOfSight(enemy)) {
                        this.predictedPlacePos = null;
                        return this.restoreAndSkip();
                    } else {
                        Integer hotbarSlot = InventoryUtil.findBlockSlot(Blocks.COBWEB);
                        int inventorySlot = -1;
                        if (hotbarSlot == null && this.fromInventory.getValue()) {
                            List inventorySlots = InventoryUtil.collectInventorySlots(Items.COBWEB, false);
                            if (!inventorySlots.isEmpty()) {
                                inventorySlot = (Integer)inventorySlots.get(0);
                            }
                        }

                        if (hotbarSlot == null && inventorySlot == -1) {
                            this.predictedPlacePos = null;
                            return this.restoreAndSkip();
                        } else {
                            boolean noSwap = this.swapMode.getValue().equals("None");
                            if (noSwap && !InventoryUtil.isHolding(Items.COBWEB) && inventorySlot == -1) {
                                this.predictedPlacePos = null;
                                return this.restoreAndSkip();
                            } else {
                                Vec3d predictedPos;
                                if (!this.onlyWhenFalling.getValue()) {
                                    predictedPos = this.predictPlacePos(enemy);
                                } else {
                                    if (this.fallTargetUuid == null
                                        || !this.fallTargetUuid.equals(enemy.getUuid())
                                        || PositionUtil.getHeightAboveGround(enemy) < 0.3F) {
                                        this.fallTargetUuid = enemy.getUuid();
                                        this.websThisFall = 0;
                                    }

                                    if (this.websThisFall >= this.maxWebsPerFall.getValue()) {
                                        this.predictedPlacePos = null;
                                        return this.restoreAndSkip();
                                    }

                                    predictedPos = this.predictFallLanding(enemy);
                                    if (predictedPos == null || predictedPos.y >= PositionUtil.getEntityPos(enemy).y - 0.5) {
                                        this.predictedPlacePos = null;
                                        return this.restoreAndSkip();
                                    }
                                }

                                if (predictedPos == null) {
                                    return this.restoreAndSkip();
                                } else {
                                    this.predictedPlacePos = predictedPos;
                                    BlockPos placePos = BlockPos.ofFloored(predictedPos.x, predictedPos.y, predictedPos.z);
                                    BlockPos[] candidates = this.headTrap.getValue() ? new BlockPos[]{placePos, placePos.up()} : new BlockPos[]{placePos};
                                    Rotation bestRotation = null;
                                    BlockHitResult bestHit = null;
                                    double bestAngle = Double.MAX_VALUE;
                                    Rotation fallbackRotation = null;
                                    Vec3d eyePos = mc.player.getEyePos();
                                    double reach = PlayerInteractions.getReachDistance();
                                    float playerYaw = mc.player.getYaw();
                                    float playerPitch = mc.player.getPitch();
                                    BlockPos feetPos = mc.player.getBlockPos();
                                    BlockPos headPos = feetPos.up();

                                    for (BlockPos candidate : candidates) {
                                        if (!candidate.equals(feetPos) && !candidate.equals(headPos)) {
                                            BlockState candidateState = mc.world.getBlockState(candidate);
                                            if (candidateState.isReplaceable() && candidateState.getFluidState().isEmpty()) {
                                                for (Direction direction : Direction.values()) {
                                                    BlockPos neighborPos = candidate.offset(direction);
                                                    BlockState neighborState = mc.world.getBlockState(neighborPos);
                                                    if (!neighborState.isAir() && !neighborState.isReplaceable() && neighborState.getFluidState().isEmpty()) {
                                                        Direction side = direction.getOpposite();
                                                        Vec3d hitVec = new Vec3d(
                                                            neighborPos.getX() + 0.5 + side.getOffsetX() * 0.5,
                                                            neighborPos.getY() + 0.5 + side.getOffsetY() * 0.5,
                                                            neighborPos.getZ() + 0.5 + side.getOffsetZ() * 0.5
                                                        );
                                                        if (!(eyePos.distanceTo(hitVec) > reach)) {
                                                            Rotation rotation = Prestige.silentRotationManager.getRotationTowards(hitVec);
                                                            float yawDelta = RotationUtil.wrapDegrees(rotation.getYaw() - playerYaw);
                                                            if (rotation.isClamped()) {
                                                                if (fallbackRotation == null) {
                                                                    fallbackRotation = rotation;
                                                                }
                                                            } else if (RotationUtil.raycastRotation(rotation) instanceof BlockHitResult rayHit
                                                                && rayHit.getBlockPos().equals(neighborPos)
                                                                && rayHit.getSide() == side
                                                                && !(eyePos.distanceTo(rayHit.getPos()) > reach)) {
                                                                float pitchDelta = rotation.getPitch() - playerPitch;
                                                                double angle = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
                                                                if (angle < bestAngle) {
                                                                    bestAngle = angle;
                                                                    bestRotation = rotation;
                                                                    bestHit = rayHit;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (bestRotation == null) {
                                        return fallbackRotation != null ? fallbackRotation : this.restoreAndSkip();
                                    } else if (bestHit == null) {
                                        return bestRotation;
                                    } else {
                                        if (!(!InventoryUtil.isHolding(Items.COBWEB) && this.originalSlot == -1 && !noSwap && hotbarSlot != null)) {
                                            BlockHitResult serverHit = this.getMatchingServerHit(bestHit);
                                            if (serverHit == null) {
                                                return bestRotation;
                                            }

                                            bestHit = serverHit;
                                        }

                                        CobwebPlaceState placeState = this.tryPlace(bestHit, hotbarSlot, inventorySlot, noSwap);
                                        if (placeState == CobwebPlaceState.PLACE_SENT) {
                                            this.recordPlacement();
                                        } else if (placeState == CobwebPlaceState.INVENTORY_QUEUED) {
                                            this.delayTimer.reset();
                                            this.delay.reroll();
                                        }

                                        return bestRotation;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                this.releaseSwap();
                return null;
            }
        } else {
            this.releaseSwap();
            return null;
        }
    }

    private BlockHitResult getMatchingServerHit(BlockHitResult desiredHit) {
        Rotation serverRotation = Prestige.silentRotationManager.getServerRotation();
        if (serverRotation == null) {
            return null;
        } else if (RotationUtil.raycastRotation(serverRotation) instanceof BlockHitResult serverHit) {
            if (!serverHit.getBlockPos().equals(desiredHit.getBlockPos())) {
                return null;
            } else if (serverHit.getSide() != desiredHit.getSide()) {
                return null;
            } else {
                return mc.player.getEyePos().distanceTo(serverHit.getPos()) > PlayerInteractions.getReachDistance() ? null : serverHit;
            }
        } else {
            return null;
        }
    }

    private CobwebPlaceState tryPlace(BlockHitResult hitResult, Integer hotbarSlot, int inventorySlot, boolean noSwap) {
        int selectedSlot = InventoryUtil.getSelectedSlot();
        if (!InventoryUtil.isHolding(Items.COBWEB)) {
            if (this.originalSlot != -1) {
                if (SwapBackTracker.isOwner(this) && selectedSlot == this.expectedSlot) {
                    this.restoreOriginalSlot();
                    return CobwebPlaceState.NONE;
                } else {
                    if (SwapBackTracker.isOwner(this)) {
                        SwapBackTracker.release(this);
                    }

                    this.clearSwapState();
                    return CobwebPlaceState.NONE;
                }
            } else if (!noSwap && hotbarSlot != null) {
                this.originalSlot = SwapBackTracker.claim(this);
                this.expectedSlot = hotbarSlot;
                this.swapWorld = mc.world;
                InventoryUtil.selectSlot(hotbarSlot);
                Prestige.silentRotationManager.setActedThisTick(true);
                return CobwebPlaceState.HOTBAR_SWAPPED;
            } else if (inventorySlot != -1) {
                if (Prestige.inventorySwapQueue != null) {
                    SwapQueueResult swapResult = Prestige.inventorySwapQueue.requestSwapThen(inventorySlot, selectedSlot, (Runnable)() -> {
                        if (this.placeFromInventory(hitResult, inventorySlot, selectedSlot)) {
                            this.recordPlacement();
                        }
                    });
                    if (swapResult == SwapQueueResult.QUEUED) {
                        return CobwebPlaceState.INVENTORY_QUEUED;
                    }

                    if (swapResult == SwapQueueResult.REJECTED) {
                        return CobwebPlaceState.NONE;
                    }
                } else {
                    InventoryUtil.clickSlot(inventorySlot, selectedSlot, SlotActionType.SWAP);
                }

                return this.placeFromInventory(hitResult, inventorySlot, selectedSlot) ? CobwebPlaceState.PLACE_SENT : CobwebPlaceState.NONE;
            } else {
                return CobwebPlaceState.NONE;
            }
        } else if (this.originalSlot != -1 && (!SwapBackTracker.isOwner(this) || selectedSlot != this.expectedSlot)) {
            if (SwapBackTracker.isOwner(this)) {
                SwapBackTracker.release(this);
            }

            this.clearSwapState();
            return CobwebPlaceState.NONE;
        } else {
            PlayerInteractions.interactBlock(hitResult);
            this.applyAutoSwitchBack();
            return CobwebPlaceState.PLACE_SENT;
        }
    }

    private boolean placeFromInventory(BlockHitResult hitResult, int inventorySlot, int hotbarSlot) {
        if (mc.player != null && mc.world != null) {
            PlayerInteractions.interactBlock(hitResult);
            if (this.autoSwitchBack.getValue().equals("Slot")) {
                this.queueSwitchBack(this.autoSwitchSlot.getValue() - 1);
            }

            Prestige.silentRotationManager.scheduleTask((Runnable)() -> {
                if (mc.player != null) {
                    InventoryUtil.swapSlot(inventorySlot, hotbarSlot);
                }
            });
            return true;
        } else {
            return false;
        }
    }

    private void recordPlacement() {
        if (this.onlyWhenFalling.getValue()) {
            this.websThisFall++;
        }

        this.delayTimer.reset();
        this.delay.reroll();
    }

    private void applyAutoSwitchBack() {
        switch (this.autoSwitchBack.getValue()) {
            case "Slot":
                this.queueSwitchBack(this.autoSwitchSlot.getValue() - 1);
                break;
            case "Original":
                if (this.originalSlot != -1) {
                    this.queueSwitchBack(this.originalSlot);
                } else {
                    this.releaseSwap();
                }
                break;
            default:
                this.releaseSwap();
        }
    }

    private void queueSwitchBack(int slot) {
        if (slot >= 0 && slot <= 8 && slot != InventoryUtil.getSelectedSlot()) {
            if (this.originalSlot == -1) {
                this.originalSlot = SwapBackTracker.claim(this);
                this.expectedSlot = InventoryUtil.getSelectedSlot();
                this.swapWorld = mc.world;
            }

            if (!SwapBackTracker.isOwner(this)) {
                this.clearSwapState();
            } else {
                this.switchBackSlot = slot;
                this.switchBackPending = true;
                this.scheduleSwitchBack();
            }
        } else {
            this.releaseSwap();
        }
    }

    private void restoreOriginalSlot() {
        if (!this.switchBackPending && this.originalSlot != -1) {
            if (mc.player != null && mc.world != null && this.swapWorld == mc.world) {
                if (!SwapBackTracker.isOwner(this)) {
                    this.clearSwapState();
                } else if (this.expectedSlot != -1 && InventoryUtil.getSelectedSlot() != this.expectedSlot) {
                    this.releaseSwap();
                } else {
                    this.queueSwitchBack(this.originalSlot);
                }
            } else {
                this.releaseSwap();
            }
        }
    }

    public boolean processSwitchBack() {
        if (!this.switchBackPending) {
            return false;
        } else if (mc.player != null && mc.world != null && this.swapWorld == mc.world) {
            if (!SwapBackTracker.isOwner(this)) {
                this.clearSwapState();
                return true;
            } else if (this.expectedSlot != -1 && InventoryUtil.getSelectedSlot() != this.expectedSlot) {
                this.releaseSwap();
                return true;
            } else if (Prestige.silentRotationManager.hasActedThisTick()) {
                return true;
            } else {
                if (InventoryUtil.getSelectedSlot() != this.switchBackSlot) {
                    InventoryUtil.selectSlot(this.switchBackSlot);
                    Prestige.silentRotationManager.setActedThisTick(true);
                }

                SwapBackTracker.release(this);
                this.clearSwapState();
                return true;
            }
        } else {
            this.releaseSwap();
            return true;
        }
    }

    private Rotation restoreAndSkip() {
        this.restoreOriginalSlot();
        this.processSwitchBack();
        return null;
    }

    private void finishSwitchBack() {
        if (mc.player != null && mc.world != null && this.swapWorld == mc.world) {
            this.restoreOriginalSlot();
            if (!this.switchBackPending) {
                this.releaseSwap();
            } else if (SwapBackTracker.isOwner(this) && mc.player != null && mc.world != null) {
                if (this.expectedSlot != -1 && InventoryUtil.getSelectedSlot() != this.expectedSlot) {
                    this.releaseSwap();
                } else if (Prestige.silentRotationManager.hasActedThisTick()) {
                    this.scheduleSwitchBack();
                } else {
                    this.processSwitchBack();
                }
            } else {
                this.releaseSwap();
            }
        } else {
            this.releaseSwap();
        }
    }

    public void scheduleSwitchBack() {
        if (!this.switchBackScheduled) {
            this.switchBackScheduled = true;
            Prestige.silentRotationManager.scheduleTask(new CobwebSwitchBackTask(this));
        }
    }

    private void releaseSwap() {
        if (SwapBackTracker.isOwner(this)) {
            SwapBackTracker.release(this);
        }

        this.clearSwapState();
    }

    private void clearSwapState() {
        this.originalSlot = -1;
        this.expectedSlot = -1;
        this.switchBackSlot = -1;
        this.switchBackPending = false;
        this.swapWorld = null;
    }

    private Vec3d predictPlacePos(PlayerEntity enemy) {
        return (PositionUtil.getHeightAboveGround(enemy) < 0.3F) ? this.predictGroundedPos(enemy) : this.predictAirbornePos(enemy);
    }

    private Vec3d predictFallLanding(PlayerEntity enemy) {
        Vec3d pos = PositionUtil.getEntityPos(enemy);
        Vec3d lastPos = PositionUtil.getLastPos(enemy);
        Vec3d velocity = enemy.getVelocity();
        double motionY = Math.min(pos.y - lastPos.y, velocity.y);
        Vec3d motion = new Vec3d(pos.x - lastPos.x, motionY, pos.z - lastPos.z);
        Box box = enemy.getBoundingBox();
        double halfWidth = Math.max((box.maxX - box.minX) / 2.0, 0.15);
        int bottomY = mc.world.getBottomY();
        double simX = pos.x;
        double simY = pos.y;
        double simZ = pos.z;

        for (int tick = 0; tick < 80; tick++) {
            double stepX = motion.x / 4;
            double stepY = motion.y / 4;
            double stepZ = motion.z / 4;

            for (int subStep = 0; subStep < 4; subStep++) {
                double nextX = simX + stepX;
                double nextY = simY + stepY;
                double nextZ = simZ + stepZ;
                if (motion.y <= 0.0) {
                    if (this.hasGroundBelow(nextX, nextY, nextZ, halfWidth)) {
                        return new Vec3d(nextX, MathHelper.floor(nextY - 1.0E-4) + 1.0, nextZ);
                    }
                }

                simX = nextX;
                simY = nextY;
                simZ = nextZ;
            }

            if (simY <= bottomY) {
                return null;
            }

            motion = motion.subtract(0.0, 0.08, 0.0).multiply(0.91, 0.98, 0.91);
        }

        return null;
    }

    private boolean hasGroundBelow(double posX, double posY, double posZ, double halfWidth) {
        int minX = MathHelper.floor(posX - halfWidth + 0.001);
        int maxX = MathHelper.floor(posX + halfWidth - 0.001);
        int minZ = MathHelper.floor(posZ - halfWidth + 0.001);
        int maxZ = MathHelper.floor(posZ + halfWidth - 0.001);
        int blockY = MathHelper.floor(posY - 0.001);

        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                BlockPos blockPos = new BlockPos(blockX, blockY, blockZ);
                BlockState state = mc.world.getBlockState(blockPos);
                if (!state.isAir() && !state.getCollisionShape(mc.world, blockPos).isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    private Vec3d predictAirbornePos(PlayerEntity enemy) {
        Vec3d pos = PositionUtil.getEntityPos(enemy);
        Vec3d lastPos = PositionUtil.getLastPos(enemy);
        Vec3d velocity = enemy.getVelocity();
        double motionY = Math.min(pos.y - lastPos.y, velocity.y);
        Vec3d motion = new Vec3d(pos.x - lastPos.x, motionY, pos.z - lastPos.z);
        Vec3d previousPos = pos;
        int ticks = this.leadTicks.getValue();
        int bottomY = mc.world.getBottomY();

        for (int tick = 0; tick < ticks; tick++) {
            previousPos = pos;
            pos = pos.add(motion);
            BlockPos belowPos = BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z);
            if (!mc.world.getBlockState(belowPos).isAir()) {
                return new Vec3d(pos.x, belowPos.getY() + 1.0, pos.z);
            }

            if (pos.y <= bottomY) {
                return null;
            }

            motion = motion.subtract(0.0, 0.08, 0.0).multiply(0.91, 0.98, 0.91);
        }

        return previousPos;
    }

    private Vec3d predictGroundedPos(PlayerEntity enemy) {
        Vec3d pos = PositionUtil.getEntityPos(enemy);
        Vec3d lastPos = PositionUtil.getLastPos(enemy);
        Vec3d motion = new Vec3d(pos.x - lastPos.x, 0.0, pos.z - lastPos.z);
        int ticks = this.leadTicks.getValue();

        for (int tick = 0; tick < ticks; tick++) {
            pos = pos.add(motion.x, 0.0, motion.z);
            BlockPos belowPos = BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z);
            if (mc.world.getBlockState(belowPos).isAir()) {
                break;
            }

            motion = motion.multiply(0.999, 1.0, 0.999);
            if (Math.abs(motion.x) < 0.005 && Math.abs(motion.z) < 0.005) {
                break;
            }
        }

        return pos;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.mode.getValue().equals("Auto") && this.render.getValue()) {
            Vec3d predictedPos = this.predictedPlacePos;
            if (predictedPos != null) {
                BlockPos blockPos = BlockPos.ofFloored(predictedPos.x, predictedPos.y, predictedPos.z);
                this.drawOutline(event, blockPos, this.renderColor.getValue());
                if (this.headTrap.getValue()) {
                    this.drawOutline(event, blockPos.up(), this.renderColor.getValue());
                }
            }
        }
    }

    private void drawOutline(Render3DEvent event, BlockPos pos, Color color) {
        CameraMatrices matrices = event.context;
        BufferSource buffers = event.renderer;
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        ShapeRenderer.drawBoxOutline(matrices, buffers, minX, minY, minZ, pos.getX() + 1.0F, pos.getY() + 1.0F, pos.getZ() + 1.0F, color);
    }

    private void advanceManualStage() {
        this.delayTimer.reset();
        this.delay.reroll();
        this.manualStage++;
    }
}

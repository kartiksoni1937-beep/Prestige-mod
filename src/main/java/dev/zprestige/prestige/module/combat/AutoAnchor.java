package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.AutoAnchorPlan;
import dev.zprestige.prestige.data.AnchorCandidate;
import dev.zprestige.prestige.data.AutoAnchorPlaceTarget;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.ToggleAnimation;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.SwapBackTracker;
import dev.zprestige.prestige.type.AutoAnchorState;
import dev.zprestige.prestige.type.Easing;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public class AutoAnchor extends Module implements RotationProvider {
    private FloatSetting targetRange;
    private FloatSetting minDamage;
    private FloatSetting maxSelfDamage;
    private BoolSetting antiSuicide;
    private BoolSetting safeAnchor;
    private EnumSetting safeBlock;
    private BoolSetting rotateSilently;
    private BoolSetting swapBack;
    private RangeSetting placeDelay;
    private RangeSetting chargeDelay;
    private RangeSetting detonateDelay;
    private BoolSetting onlyExisting;
    private BoolSetting render;
    private ColorSetting anchorColor;
    private ColorSetting safeColor;
    private ColorSetting targetColor;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[][] COVER_OFFSETS = new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1}, {0, 1, 0}};
    public static boolean sequenceActive;
    private AutoAnchorState state = AutoAnchorState.IDLE;
    private StopWatch placeDelayTimer = new StopWatch();
    private StopWatch chargeDelayTimer = new StopWatch();
    private StopWatch detonateDelayTimer = new StopWatch();
    private StopWatch stallTimer = new StopWatch();
    private int failedAttempts;
    private HashMap blacklistedAnchors = new HashMap();
    private long tickCount;
    private long cacheExpiryTick;
    private PlayerEntity cachedTarget;
    private BlockPos cachedPlayerBlockPos;
    private BlockPos cachedTargetBlockPos;
    private Vec3d cachedEyePos;
    private Vec3d cachedTargetPos;
    private Vec3d cachedTargetVelocity;
    private float cachedYaw;
    private int cachedStateHash;
    private volatile long blockUpdateCount;
    private long cachedBlockUpdateCount;
    private int hurtWaitTicks;
    private PlayerEntity target;
    private BlockPos anchorPos;
    private BlockPos anchorSupportPos;
    private Vec3d anchorHitVec;
    private BlockPos coverPos;
    private BlockPos coverSupportPos;
    private Vec3d coverHitVec;
    private BlockPos plannedTargetPos;
    private int originalSlot = -1;
    private Vec3d lastSentEyePos;
    private List placedAnchors = new ArrayList();
    private ToggleAnimation targetBoxFade = new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT);
    private ToggleAnimation anchorBoxFade = new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT);
    private ToggleAnimation coverBoxFade = new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT);
    private BlockPos renderedTargetPos;
    private BlockPos renderedAnchorPos;
    private BlockPos renderedCoverPos;
    private StopWatch errorNotifyTimer = new StopWatch();

    public AutoAnchor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.targetColor.visibleWhen((Predicate<Color>)ignored -> this.render.getValue());
        this.safeColor
            .visibleWhen((Predicate<Color>)ignored -> this.render.getValue() && this.safeAnchor.getValue());
        this.anchorColor.visibleWhen((Predicate<Color>)ignored -> this.render.getValue());
        this.safeBlock.visibleWhen((Predicate<String>)ignored -> this.safeAnchor.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        this.resetAll();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        if (this.originalSlot != -1 && this.swapBack.getValue() && Prestige.silentRotationManager.hasActedThisTick()) {
            Prestige.silentRotationManager.scheduleSlotSwitch(this.originalSlot);
            SwapBackTracker.release(this);
            this.originalSlot = -1;
        }

        this.restoreOriginalSlot();
        this.resetAll();
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Override
    public int getRotationPriority() {
        return 20;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetAll();
        this.placedAnchors.clear();
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (this.state != AutoAnchorState.IDLE) {
            event.cancel();
        }
    }

    @Subscribe
    public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
        if (this.state != AutoAnchorState.IDLE) {
            event.cancel();
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.state != AutoAnchorState.IDLE) {
            event.cancel();
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (event.getPhase() == EventPhase.PRE && mc.player != null) {
            this.lastSentEyePos = mc.player.getEyePos();
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            this.blockUpdateCount++;
            if (blockUpdate.getState() != null && blockUpdate.getState().getBlock() != Blocks.RESPAWN_ANCHOR) {
                mc.execute(() -> this.placedAnchors.remove(blockUpdate.getPos()));
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.render.getValue() && mc.player != null && mc.world != null) {
            float pulse = getPulse();
            boolean hasTarget = this.target != null;
            if (hasTarget) {
                this.renderedTargetPos = this.target.getBlockPos();
            }

            float targetProgress = updateFade(this.targetBoxFade, hasTarget);
            if (targetProgress > 0.01F && this.renderedTargetPos != null) {
                Color targetBoxColor = fadeColor(this.targetColor.getValue(), targetProgress, pulse);
                if (targetBoxColor.getAlpha() > 0) {
                    this.drawBlockOutline(event, this.renderedTargetPos, targetBoxColor);
                }
            }

            boolean hasAnchor = this.anchorPos != null;
            if (hasAnchor) {
                this.renderedAnchorPos = this.anchorPos;
            }

            float anchorProgress = updateFade(this.anchorBoxFade, hasAnchor);
            if (anchorProgress > 0.01F && this.renderedAnchorPos != null) {
                Color anchorBoxColor = fadeColor(this.anchorColor.getValue(), anchorProgress, pulse);
                if (anchorBoxColor.getAlpha() > 0) {
                    this.drawBlockOutline(event, this.renderedAnchorPos, anchorBoxColor);
                }
            }

            boolean hasCover = this.coverPos != null && this.state == AutoAnchorState.SAFE_COVER;
            if (hasCover) {
                this.renderedCoverPos = this.coverPos;
            }

            float coverProgress = updateFade(this.coverBoxFade, hasCover);
            if (coverProgress > 0.01F && this.renderedCoverPos != null) {
                Color coverBoxColor = fadeColor(this.safeColor.getValue(), coverProgress, pulse);
                if (coverBoxColor.getAlpha() > 0) {
                    this.drawBlockOutline(event, this.renderedCoverPos, coverBoxColor);
                }
            }
        }
    }

    private static float getPulse() {
        return 0.5F + 0.5F * (float)Math.sin(System.currentTimeMillis() / 900.0 * (Math.PI * 2));
    }

    private static float updateFade(ToggleAnimation animation, boolean visible) {
        if (animation.getState() != visible) {
            animation.setState(visible);
        }

        return animation.getProgress();
    }

    private static int scaleAlpha(int alpha, float progress, float pulse) {
        return Math.max(0, Math.min(255, Math.round(alpha * progress * (0.6F + 0.4F * pulse))));
    }

    private static Color fadeColor(Color color, float progress, float pulse) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), scaleAlpha(color.getAlpha(), progress, pulse));
    }

    private void drawBlockOutline(Render3DEvent event, BlockPos pos, Color color) {
        CameraMatrices matrices = event.context;
        BufferSource bufferSource = event.renderer;
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        ShapeRenderer.drawBoxOutline(matrices, bufferSource, minX, minY, minZ, pos.getX() + 1.0F, pos.getY() + 1.0F, pos.getZ() + 1.0F, color);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.tickCount++;
        if (!this.blacklistedAnchors.isEmpty()) {
            this.blacklistedAnchors.entrySet().removeIf(entry -> this.tickCount - (Long)((java.util.Map.Entry)entry).getValue() > 40L);
        }

        if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            sequenceActive = false;
        } else if (!AutoObbyCrystal.sequenceActive && !AnchorPearlCatch.active) {
            if (this.state != AutoAnchorState.IDLE && (!this.isTargetValid() || this.hasTargetMoved())) {
                this.abortSequence();
            }

            if (this.state != AutoAnchorState.IDLE) {
                if (this.stallTimer.hasElapsed(200.0F)) {
                    this.abortSequence();
                }
            }

            if (this.state == AutoAnchorState.IDLE) {
                this.restoreOriginalSlot();
                this.tryStartSequence();
            } else if (!this.rotateSilently.getValue()) {
                if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return;
                }

                this.executeStageWithCameraRotation();
            }

            sequenceActive = this.state != AutoAnchorState.IDLE;
        } else {
            if (this.state != AutoAnchorState.IDLE) {
                this.abortSequence();
            }

            this.restoreOriginalSlot();
            sequenceActive = false;
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.rotateSilently.getValue()) {
            return null;
        } else if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            return null;
        } else if (this.state == AutoAnchorState.IDLE || this.anchorPos == null || this.target == null) {
            return null;
        } else if (AutoObbyCrystal.sequenceActive) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (!this.isStageReady()) {
            return null;
        } else {
            Vec3d aimPoint = this.getStageAimPoint();
            if (this.isOutOfReach(aimPoint)) {
                this.abortSequence();
                return null;
            } else if (aimPoint == null) {
                this.registerFailure();
                return null;
            } else {
                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) >= 60.0F) {
                    this.abortSequence();
                    return null;
                } else if (rotation.isClamped()) {
                    return rotation;
                } else {
                    this.executeStage(rotation);
                    return this.state == AutoAnchorState.IDLE ? null : rotation;
                }
            }
        }
    }

    private void executeStageWithCameraRotation() {
        if (this.isStageReady()) {
            Vec3d aimPoint = this.getStageAimPoint();
            if (this.isOutOfReach(aimPoint)) {
                this.abortSequence();
            } else if (aimPoint == null) {
                this.registerFailure();
            } else {
                Rotation rotation = RotationUtil.getRotationTo(aimPoint);
                RotationUtil.applyRotation(rotation);
                this.executeStage(rotation);
            }
        }
    }

    private boolean isStageReady() {
        return switch (this.state) {
            case PLACE_ANCHOR -> true;
            case SAFE_COVER -> this.placeDelayTimer.hasElapsedRandom(this.placeDelay);
            case CHARGE_ANCHOR -> this.chargeDelayTimer.hasElapsedRandom(this.chargeDelay);
            case DETONATE -> this.detonateDelayTimer.hasElapsedRandom(this.detonateDelay);
            default -> false;
        };
    }

    private Vec3d getStageAimPoint() {
        return switch (this.state) {
            case PLACE_ANCHOR -> this.anchorHitVec;
            case SAFE_COVER -> this.coverHitVec;
            case CHARGE_ANCHOR, DETONATE -> RaycastUtil.getVisibleTopPoint(this.anchorPos);
            default -> null;
        };
    }

    private boolean isOutOfReach(Vec3d aimPoint) {
        return aimPoint == null ? false : mc.player.getEyePos().distanceTo(aimPoint) > 4.4 || this.lastSentEyePos != null && this.lastSentEyePos.distanceTo(aimPoint) > 4.4;
    }

    private boolean registerFailure() {
        if (++this.failedAttempts >= 3) {
            this.abortSequence();
            return true;
        } else {
            return false;
        }
    }

    private void executeStage(Rotation rotation) {
        switch (this.state) {
            case PLACE_ANCHOR:
                this.handlePlaceStage(rotation);
                break;
            case SAFE_COVER:
                this.handleCoverStage(rotation);
                break;
            case CHARGE_ANCHOR:
                this.handleChargeStage(rotation);
                break;
            case DETONATE:
                this.handleDetonateStage(rotation);
        }
    }

    private void handlePlaceStage(Rotation rotation) {
        if (BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.advanceAfterPlace();
        } else if (BlockUtil.isClickableBlock(this.anchorPos)) {
            this.abortSequence();
        } else {
            Integer anchorSlot = InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR);
            if (anchorSlot == null) {
                this.abortWithError("No respawn anchor in hotbar.");
            } else {
                BlockHitResult hitResult = this.raycastBlock(rotation);
                if (hitResult != null && hitResult.getBlockPos().equals(this.anchorSupportPos) && hitResult.getBlockPos().offset(hitResult.getSide()).equals(this.anchorPos)) {
                    this.failedAttempts = 0;
                    if (this.originalSlot == -1) {
                        this.originalSlot = SwapBackTracker.claim(this);
                    }

                    if (InventoryUtil.getSelectedSlot() != anchorSlot) {
                        InventoryUtil.selectSlot(anchorSlot);
                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.stallTimer.reset();
                    } else {
                        this.clickBlock(hitResult);
                        this.placedAnchors.add(this.anchorPos);
                        this.placeDelayTimer.reset();
                        this.placeDelay.reroll();
                        this.advanceAfterPlace();
                        this.stallTimer.reset();
                    }
                } else {
                    this.registerFailure();
                }
            }
        }
    }

    private void advanceAfterPlace() {
        if (this.safeAnchor.getValue() && this.coverPos != null) {
            this.state = AutoAnchorState.SAFE_COVER;
        } else {
            this.state = AutoAnchorState.CHARGE_ANCHOR;
        }

        this.failedAttempts = 0;
    }

    private void handleCoverStage(Rotation rotation) {
        if (this.coverPos != null && this.coverSupportPos != null && this.coverHitVec != null) {
            if (BlockUtil.isClickableBlock(this.coverPos)) {
                this.advanceToCharge();
            } else {
                Integer coverSlot = InventoryUtil.findHotbarSlot((this.safeBlock.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN).asItem());
                if (coverSlot == null) {
                    this.advanceToCharge();
                } else {
                    BlockHitResult hitResult = this.raycastBlock(rotation);
                    if (hitResult != null && hitResult.getBlockPos().equals(this.coverSupportPos) && hitResult.getBlockPos().offset(hitResult.getSide()).equals(this.coverPos)) {
                        this.failedAttempts = 0;
                        if (this.originalSlot == -1) {
                            this.originalSlot = SwapBackTracker.claim(this);
                        }

                        if (InventoryUtil.getSelectedSlot() != coverSlot) {
                            InventoryUtil.selectSlot(coverSlot);
                            Prestige.silentRotationManager.setActedThisTick(true);
                            this.stallTimer.reset();
                        } else {
                            this.clickBlock(hitResult);
                            this.chargeDelayTimer.reset();
                            this.chargeDelay.reroll();
                            this.state = AutoAnchorState.CHARGE_ANCHOR;
                            this.failedAttempts = 0;
                            this.stallTimer.reset();
                        }
                    } else {
                        this.registerFailure();
                    }
                }
            }
        } else {
            this.advanceToCharge();
        }
    }

    private void advanceToCharge() {
        this.state = AutoAnchorState.CHARGE_ANCHOR;
        this.failedAttempts = 0;
        this.stallTimer.reset();
    }

    private void handleChargeStage(Rotation rotation) {
        if (!BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.registerFailure();
        } else {
            if (this.getChargesAt(this.anchorPos) > 0) {
                this.advanceToDetonate();
            } else {
                Integer glowstoneSlot = InventoryUtil.findHotbarSlot(Items.GLOWSTONE);
                if (glowstoneSlot == null) {
                    this.abortWithError("No glowstone in hotbar.");
                } else {
                    BlockHitResult hitResult = this.raycastBlock(rotation);
                    if (hitResult != null && hitResult.getBlockPos().equals(this.anchorPos)) {
                        this.failedAttempts = 0;
                        if (this.originalSlot == -1) {
                            this.originalSlot = SwapBackTracker.claim(this);
                        }

                        if (InventoryUtil.getSelectedSlot() != glowstoneSlot) {
                            InventoryUtil.selectSlot(glowstoneSlot);
                            Prestige.silentRotationManager.setActedThisTick(true);
                            this.stallTimer.reset();
                        } else {
                            this.clickBlock(hitResult);
                            this.detonateDelayTimer.reset();
                            this.detonateDelay.reroll();
                            this.advanceToDetonate();
                        }
                    } else {
                        this.registerFailure();
                    }
                }
            }
        }
    }

    private void advanceToDetonate() {
        this.state = AutoAnchorState.DETONATE;
        this.failedAttempts = 0;
        this.stallTimer.reset();
    }

    private void handleDetonateStage(Rotation rotation) {
        if (!BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.registerFailure();
        } else {
            if (this.getChargesAt(this.anchorPos) <= 0) {
                this.advanceToCharge();
            } else {
                if (this.target != null && this.target.hurtTime > 2) {
                    this.stallTimer.reset();
                    if (++this.hurtWaitTicks <= 12) {
                        return;
                    }
                }

                this.hurtWaitTicks = 0;
                double selfDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, this.anchorPos.toCenterPos());
                float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                if (selfDamage > this.maxSelfDamage.getValue().floatValue()) {
                    this.blacklistedAnchors.put(this.anchorPos.toImmutable(), this.tickCount);
                    this.abortSequence();
                } else if (this.antiSuicide.getValue() && selfDamage >= health - 1.0F) {
                    this.abortSequence();
                } else if (InventoryUtil.isHolding(Items.GLOWSTONE)) {
                    int detonateSlot = this.originalSlot != -1 ? this.originalSlot : this.findNonGlowstoneSlot();
                    if (detonateSlot == -1) {
                        this.abortWithError("No non-glowstone slot to detonate with.");
                    } else {
                        InventoryUtil.selectSlot(detonateSlot);
                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.stallTimer.reset();
                    }
                } else {
                    BlockHitResult hitResult = this.raycastBlock(rotation);
                    if (hitResult != null && hitResult.getBlockPos().equals(this.anchorPos)) {
                        this.failedAttempts = 0;
                        this.clickBlock(hitResult);
                        this.abortSequence();
                    } else {
                        this.registerFailure();
                    }
                }
            }
        }
    }

    private int findNonGlowstoneSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() != Items.GLOWSTONE) {
                return slot;
            }
        }

        return -1;
    }

    private void clickBlock(BlockHitResult hitResult) {
        boolean accepted = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted();
        Prestige.silentRotationManager.setActedThisTick(true);
        if (accepted) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private BlockHitResult raycastBlock(Rotation rotation) {
        return RotationUtil.raycastRotation(rotation) instanceof BlockHitResult blockHit ? blockHit : null;
    }

    private void tryStartSequence() {
        PlayerEntity enemy = TargetSelector.getClosestEnemy();
        if (enemy != null && enemy.isAlive() && !(mc.player.distanceTo(enemy) > this.targetRange.getValue())) {
            if (!this.isFailedPlanCached(enemy)) {
                this.target = enemy;
                long updateCountBefore = this.blockUpdateCount;
                AutoAnchorPlan plan = this.findBestPlan();
                if (plan == null) {
                    if (updateCountBefore == this.blockUpdateCount) {
                        this.cacheFailedPlan(enemy, updateCountBefore);
                    }

                    this.target = null;
                } else {
                    this.clearPlanCache();
                    this.anchorPos = plan.anchorPos();
                    this.anchorSupportPos = plan.anchorSupportPos();
                    this.anchorHitVec = plan.anchorHitVec();
                    this.coverPos = plan.blockerPos();
                    this.coverSupportPos = plan.blockerSupportPos();
                    this.coverHitVec = plan.blockerHitVec();
                    this.plannedTargetPos = this.target.getBlockPos();
                    if (plan.anchorExists()) {
                        int charges = this.getChargesAt(this.anchorPos);
                        this.state = this.safeAnchor.getValue() && this.coverPos != null
                            ? AutoAnchorState.SAFE_COVER
                            : (charges > 0 ? AutoAnchorState.DETONATE : AutoAnchorState.CHARGE_ANCHOR);
                    } else {
                        this.state = AutoAnchorState.PLACE_ANCHOR;
                    }

                    this.failedAttempts = 0;
                    this.stallTimer.reset();
                    this.placeDelayTimer.reset();
                    this.placeDelay.reroll();
                    this.chargeDelayTimer.reset();
                    this.chargeDelay.reroll();
                    this.detonateDelayTimer.reset();
                    this.detonateDelay.reroll();
                }
            }
        } else {
            this.clearPlanCache();
        }
    }

    private Vec3d predictTargetPos() {
        Vec3d currentPos = PositionUtil.getEntityPos(this.target);
        Vec3d velocity = this.target.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6) {
            return currentPos;
        } else {
            int latency = 0;
            PlayerListEntry listEntry = mc.player.networkHandler.getPlayerListEntry(this.target.getUuid());
            if (listEntry != null) {
                latency = Math.max(0, listEntry.getLatency());
            }

            float predictTicks = MathHelper.clamp(3.0F + latency / 50.0F * 1.0F, 0.0F, 20.0F);
            Vec3d offset = new Vec3d(velocity.x * predictTicks, 0.0, velocity.z * predictTicks);
            double maxOffset = 3.0;
            if (offset.lengthSquared() > maxOffset * maxOffset) {
                offset = offset.lengthSquared() > 1.0E-6 ? offset.normalize().multiply(maxOffset) : Vec3d.ZERO;
            }

            return currentPos.add(offset);
        }
    }

    private AutoAnchorPlan findBestPlan() {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = PositionUtil.getEntityPos(this.target);
        Vec3d predictedPos = this.predictTargetPos();
        Vec3d predictionOffset = predictedPos.subtract(targetPos);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        List candidates = this.collectCandidates(eyePos, targetPos, predictedPos);
        candidates.sort((first, second) -> {
            int scoreComparison = Double.compare(((dev.zprestige.prestige.data.AnchorCandidate)second).score(), ((dev.zprestige.prestige.data.AnchorCandidate)first).score());
            return scoreComparison != 0 ? scoreComparison : Integer.compare(((dev.zprestige.prestige.data.AnchorCandidate)first).scanIndex(), ((dev.zprestige.prestige.data.AnchorCandidate)second).scanIndex());
        });
        AutoAnchorPlan bestPlan = null;
        double bestScore = -Double.MAX_VALUE;
        int bestScanIndex = Integer.MAX_VALUE;
        Block coverBlock = this.safeBlock.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
        BlockState coverState = coverBlock.getDefaultState();
        boolean hasCoverBlock = InventoryUtil.findHotbarSlot(coverBlock.asItem()) != null;

        for (AnchorCandidate candidate : (Iterable<AnchorCandidate>)(candidates)) {
            if (bestPlan != null) {
                if (!couldBeatBest(candidate.score(), candidate.scanIndex(), bestScore, bestScanIndex)) {
                    break;
                }
            }

            Vec3d anchorCenter = candidate.anchorPos().toCenterPos();
            Vec3d visiblePoint = RaycastUtil.getVisibleTopPoint(candidate.anchorPos());
            if (!candidate.anchorExists() || visiblePoint != null) {
                Vec3d aimPoint = visiblePoint != null ? visiblePoint : anchorCenter.add(0.0, 0.5, 0.0);
                if (!(eyePos.squaredDistanceTo(aimPoint) > 19.360000000000003)) {
                    if (this.rotateSilently.getValue()) {
                        if (Math.abs(RotationUtil.wrapDegrees(RotationUtil.getRotationTo(aimPoint).getYaw() - mc.player.getYaw())) >= 60.0F) {
                            continue;
                        }
                    }

                    double targetDamage = this.computeTargetDamage(candidate, anchorCenter, predictionOffset);
                    if (!(targetDamage < this.minDamage.getValue().floatValue())) {
                        double maxPossibleScore = scoreCandidate(candidate, targetDamage, 0.0);
                        if (bestPlan != null) {
                            if (!couldBeatBest(maxPossibleScore, candidate.scanIndex(), bestScore, bestScanIndex)) {
                                continue;
                            }
                        }

                        double selfDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorCenter);
                        if (!this.antiSuicide.getValue() || !(selfDamage >= health * 2.0F)) {
                            BlockPos bestCoverPos = null;
                            BlockPos bestCoverSupportPos = null;
                            Vec3d bestCoverHitVec = null;
                            double effectiveSelfDamage = selfDamage;
                            if (this.safeAnchor.getValue() && hasCoverBlock && selfDamage > 6.0) {
                                double lowestCoveredDamage = selfDamage;

                                for (int[] offset : COVER_OFFSETS) {
                                    BlockPos coverCandidate = candidate.anchorPos().add(offset[0], offset[1], offset[2]);
                                    if (!BlockUtil.isClickableBlock(coverCandidate) && !BlockUtil.hasEntityAt(coverCandidate)) {
                                        AutoAnchorPlaceTarget coverPlaceTarget = this.findPlaceTarget(coverCandidate, eyePos);
                                        if (coverPlaceTarget != null) {
                                            double coverDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorCenter, coverCandidate, coverState);
                                            if (!(coverDamage >= lowestCoveredDamage) && !(selfDamage - coverDamage < 2.0)) {
                                                lowestCoveredDamage = coverDamage;
                                                bestCoverPos = coverCandidate;
                                                bestCoverSupportPos = coverPlaceTarget.supportPos();
                                                bestCoverHitVec = coverPlaceTarget.hitVec();
                                            }
                                        }
                                    }
                                }

                                if (bestCoverPos != null) {
                                    effectiveSelfDamage = lowestCoveredDamage;
                                }
                            }

                            if (!(effectiveSelfDamage > this.maxSelfDamage.getValue().floatValue()) && (!this.antiSuicide.getValue() || !(effectiveSelfDamage >= health - 1.0F))) {
                                double score = scoreCandidate(candidate, targetDamage, effectiveSelfDamage);
                                if (isBetterThanBest(score, candidate.scanIndex(), bestScore, bestScanIndex)) {
                                    bestScore = score;
                                    bestScanIndex = candidate.scanIndex();
                                    bestPlan = new AutoAnchorPlan(candidate.anchorPos(), candidate.supportPos(), candidate.hitVec(), candidate.anchorExists(), bestCoverPos, bestCoverSupportPos, bestCoverHitVec);
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestPlan;
    }

    private List collectCandidates(Vec3d eyePos, Vec3d targetPos, Vec3d predictedPos) {
        ArrayList candidates = new ArrayList();
        BlockPos targetBlockPos = this.target.getBlockPos();
        Mutable mutablePos = new Mutable();
        boolean hasAnchorItem = InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR) != null;
        boolean hasGlowstone = InventoryUtil.findHotbarSlot(Items.GLOWSTONE) != null;
        int scanIndex = 0;
        if (this.findNonGlowstoneSlot() == -1) {
            return candidates;
        } else {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -5; dz <= 5; scanIndex++) {
                        mutablePos.set(targetBlockPos.getX() + dx, targetBlockPos.getY() + dy, targetBlockPos.getZ() + dz);
                        Vec3d center = new Vec3d(mutablePos.getX() + 0.5, mutablePos.getY() + 0.5, mutablePos.getZ() + 0.5);
                        double eyeDistanceSq = eyePos.squaredDistanceTo(center);
                        label130:
                        if (!(eyeDistanceSq > 21.159999999999997) && (this.blacklistedAnchors.isEmpty() || !this.blacklistedAnchors.containsKey(mutablePos))) {
                            boolean anchorExists = BlockUtil.isOf(mutablePos, Blocks.RESPAWN_ANCHOR);
                            BlockPos supportPos = null;
                            Vec3d hitVec = null;
                            BlockPos candidatePos;
                            if (anchorExists) {
                                candidatePos = mutablePos.toImmutable();
                            } else {
                                if (!hasAnchorItem
                                    || !hasGlowstone
                                    || this.onlyExisting.getValue()
                                    || BlockUtil.isClickableBlock(mutablePos)
                                    || BlockUtil.hasEntityAt(mutablePos)) {
                                    break label130;
                                }

                                candidatePos = mutablePos.toImmutable();
                                AutoAnchorPlaceTarget placeTarget = this.findPlaceTarget(candidatePos, eyePos);
                                if (placeTarget == null) {
                                    break label130;
                                }

                                supportPos = placeTarget.supportPos();
                                hitVec = placeTarget.hitVec();
                            }

                            int charges = anchorExists ? this.getChargesAt(candidatePos) : 0;
                            if (!anchorExists || charges > 0 || hasGlowstone) {
                                double currentDamageEstimate = estimateRawDamage(targetPos, center);
                                double predictedDamageEstimate = estimateRawDamage(predictedPos, center);
                                double maxDamageEstimate = Math.max(currentDamageEstimate, predictedDamageEstimate);
                                if (!(maxDamageEstimate < this.minDamage.getValue().floatValue())) {
                                    double eyeDistance = Math.sqrt(eyeDistanceSq);
                                    boolean sameLevel = candidatePos.getY() == targetBlockPos.getY();
                                    boolean adjacent = sameLevel && Math.max(Math.abs(candidatePos.getX() - targetBlockPos.getX()), Math.abs(candidatePos.getZ() - targetBlockPos.getZ())) == 1;
                                    candidates.add(new AnchorCandidate(candidatePos, supportPos, hitVec, anchorExists, charges, eyeDistance, sameLevel, adjacent, scanIndex, currentDamageEstimate, predictedDamageEstimate, computeScore(maxDamageEstimate, 0.0, eyeDistance, sameLevel, adjacent, anchorExists, charges)));
                                }
                            }
                        }

                        dz++;
                    }
                }
            }

            return candidates;
        }
    }

    private AutoAnchorPlaceTarget findPlaceTarget(BlockPos placePos, Vec3d eyePos) {
        BlockPos bestSupportPos = null;
        Vec3d bestHitVec = null;
        double bestDistanceSq = Double.MAX_VALUE;
        Direction[] directions = DIRECTIONS;

        for (Direction direction : directions) {
            BlockPos neighborPos = placePos.offset(direction);
            if (!mc.world.getBlockState(neighborPos).isReplaceable()) {
                Direction face = direction.getOpposite();
                Vec3d faceCenter = neighborPos.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                double distanceSq = eyePos.squaredDistanceTo(faceCenter);
                if (!(distanceSq > 18.0625) && !(distanceSq >= bestDistanceSq)) {
                    bestDistanceSq = distanceSq;
                    bestSupportPos = neighborPos;
                    bestHitVec = faceCenter;
                }
            }
        }

        return bestSupportPos == null ? null : new AutoAnchorPlaceTarget(bestSupportPos, bestHitVec);
    }

    private static double estimateRawDamage(Vec3d entityPos, Vec3d explosionPos) {
        double distance = entityPos.distanceTo(explosionPos);
        if (distance > 10.0) {
            return 0.0;
        } else {
            double impact = 1.0 - distance / 10.0;
            return Math.nextUp((float)(((impact * impact + impact) * 0.5 * 7.0 * 10.0 + 1.0) * 1.5));
        }
    }

    private double computeTargetDamage(AnchorCandidate candidate, Vec3d anchorCenter, Vec3d predictionOffset) {
        if (predictionOffset.lengthSquared() < 1.0E-6) {
            return Prestige.damageCalculator.getAnchorDamage(this.target, anchorCenter);
        } else if (candidate.predictedDamageEstimate() > candidate.targetDamageEstimate()) {
            double predictedDamage = Prestige.damageCalculator.getAnchorDamage(this.target, anchorCenter.subtract(predictionOffset));
            return candidate.targetDamageEstimate() <= predictedDamage ? predictedDamage : Math.max(predictedDamage, Prestige.damageCalculator.getAnchorDamage(this.target, anchorCenter));
        } else {
            double currentDamage = Prestige.damageCalculator.getAnchorDamage(this.target, anchorCenter);
            return candidate.predictedDamageEstimate() <= currentDamage ? currentDamage : Math.max(currentDamage, Prestige.damageCalculator.getAnchorDamage(this.target, anchorCenter.subtract(predictionOffset)));
        }
    }

    private static double scoreCandidate(AnchorCandidate candidate, double targetDamage, double selfDamage) {
        return computeScore(targetDamage, selfDamage, candidate.distance(), candidate.sameLevel(), candidate.adjacent(), candidate.anchorExists(), candidate.charges());
    }

    private static double computeScore(double targetDamage, double selfDamage, double distance, boolean sameLevel, boolean adjacent, boolean anchorExists, int charges) {
        double score = targetDamage - 0.6 * selfDamage - 0.15 * distance - 0.15 * distance * distance;
        if (sameLevel) {
            score += 4.0;
        }

        if (adjacent) {
            score += 3.5;
        }

        if (anchorExists) {
            double closeness = Math.max(0.0, 1.0 - distance / 4.4);
            score += 5.0 * closeness;
            if (charges > 0) {
                score += 6.0 * closeness;
            }
        }

        return score;
    }

    private static boolean couldBeatBest(double score, int scanIndex, double bestScore, int bestScanIndex) {
        return score > bestScore || score == bestScore && scanIndex < bestScanIndex;
    }

    private static boolean isBetterThanBest(double score, int scanIndex, double bestScore, int bestScanIndex) {
        return score > bestScore || score == bestScore && scanIndex < bestScanIndex;
    }

    private int getChargesAt(BlockPos pos) {
        BlockState blockState = mc.world.getBlockState(pos);
        return !blockState.isOf(Blocks.RESPAWN_ANCHOR) ? 0 : blockState.get(Properties.CHARGES);
    }

    private boolean isTargetValid() {
        return this.target != null && this.target.isAlive() && mc.player.distanceTo(this.target) <= 12.0F && Prestige.botFilter.isValidTarget(this.target);
    }

    private boolean hasTargetMoved() {
        if (this.plannedTargetPos != null && this.target != null) {
            BlockPos currentPos = this.target.getBlockPos();
            return Math.max(Math.abs(currentPos.getX() - this.plannedTargetPos.getX()), Math.max(Math.abs(currentPos.getY() - this.plannedTargetPos.getY()), Math.abs(currentPos.getZ() - this.plannedTargetPos.getZ()))) > 1;
        } else {
            return false;
        }
    }

    private boolean isFailedPlanCached(PlayerEntity enemy) {
        if (this.blockUpdateCount != this.cachedBlockUpdateCount) {
            return false;
        } else if (this.tickCount < this.cacheExpiryTick && this.cachedTarget == enemy) {
            if (!mc.player.getBlockPos().equals(this.cachedPlayerBlockPos) || !enemy.getBlockPos().equals(this.cachedTargetBlockPos)) {
                return false;
            } else if (this.cachedEyePos != null && mc.player.getEyePos().equals(this.cachedEyePos)) {
                if (this.cachedTargetPos == null || !PositionUtil.getEntityPos(enemy).equals(this.cachedTargetPos)) {
                    return false;
                } else if (this.cachedTargetVelocity == null || !enemy.getVelocity().equals(this.cachedTargetVelocity)) {
                    return false;
                } else {
                    return Float.compare(mc.player.getYaw(), this.cachedYaw) != 0 ? false : this.cachedStateHash == this.computeStateHash();
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void cacheFailedPlan(PlayerEntity enemy, long updateCount) {
        this.cachedTarget = enemy;
        this.cachedPlayerBlockPos = mc.player.getBlockPos().toImmutable();
        this.cachedTargetBlockPos = enemy.getBlockPos().toImmutable();
        this.cachedEyePos = mc.player.getEyePos();
        this.cachedTargetPos = PositionUtil.getEntityPos(enemy);
        this.cachedTargetVelocity = enemy.getVelocity();
        this.cachedYaw = mc.player.getYaw();
        this.cachedStateHash = this.computeStateHash();
        this.cachedBlockUpdateCount = updateCount;
        this.cacheExpiryTick = this.tickCount + 3L;
    }

    private int computeStateHash() {
        int hash = Float.floatToIntBits(this.targetRange.getValue());
        hash = 31 * hash + Float.floatToIntBits(this.minDamage.getValue());
        hash = 31 * hash + Float.floatToIntBits(this.maxSelfDamage.getValue());
        hash = 31 * hash + Boolean.hashCode(this.antiSuicide.getValue());
        hash = 31 * hash + Boolean.hashCode(this.safeAnchor.getValue());
        hash = 31 * hash + this.safeBlock.getValue().hashCode();
        hash = 31 * hash + Boolean.hashCode(this.rotateSilently.getValue());
        hash = 31 * hash + Boolean.hashCode(this.onlyExisting.getValue());
        hash = 31 * hash + Float.floatToIntBits(mc.player.getHealth() + mc.player.getAbsorptionAmount());

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            hash = 31 * hash + stack.getItem().hashCode();
            hash = 31 * hash + stack.getCount();
        }

        return hash;
    }

    private void clearPlanCache() {
        this.cacheExpiryTick = 0L;
        this.cachedTarget = null;
        this.cachedPlayerBlockPos = null;
        this.cachedTargetBlockPos = null;
        this.cachedEyePos = null;
        this.cachedTargetPos = null;
        this.cachedTargetVelocity = null;
        this.cachedYaw = 0.0F;
        this.cachedStateHash = 0;
        this.cachedBlockUpdateCount = this.blockUpdateCount;
    }

    private void abortWithError(String message) {
        if (this.errorNotifyTimer.hasElapsed(3000.0F)) {
            ModuleNotificationsElement.postNotification(this, message, NotificationType.ERROR);
            this.errorNotifyTimer.reset();
        }

        this.abortSequence();
    }

    private void restoreOriginalSlot() {
        if (this.originalSlot != -1) {
            if (mc.player == null) {
                SwapBackTracker.release(this);
                this.originalSlot = -1;
            } else if (!SwapBackTracker.isOwner(this)) {
                this.originalSlot = -1;
            } else if (!Prestige.silentRotationManager.hasActedThisTick()) {
                if (this.swapBack.getValue()) {
                    InventoryUtil.selectSlot(this.originalSlot);
                    Prestige.silentRotationManager.setActedThisTick(true);
                }

                SwapBackTracker.release(this);
                this.originalSlot = -1;
            }
        }
    }

    private void abortSequence() {
        this.restoreOriginalSlot();
        this.clearPlanCache();
        this.state = AutoAnchorState.IDLE;
        this.target = null;
        this.anchorPos = null;
        this.anchorSupportPos = null;
        this.anchorHitVec = null;
        this.coverPos = null;
        this.coverSupportPos = null;
        this.coverHitVec = null;
        this.plannedTargetPos = null;
        this.failedAttempts = 0;
        this.hurtWaitTicks = 0;
        sequenceActive = false;
    }

    private void resetAll() {
        this.clearPlanCache();
        this.state = AutoAnchorState.IDLE;
        this.target = null;
        this.anchorPos = null;
        this.anchorSupportPos = null;
        this.anchorHitVec = null;
        this.coverPos = null;
        this.coverSupportPos = null;
        this.coverHitVec = null;
        this.plannedTargetPos = null;
        this.originalSlot = -1;
        this.failedAttempts = 0;
        this.placeDelayTimer.reset();
        this.chargeDelayTimer.reset();
        this.detonateDelayTimer.reset();
        this.stallTimer.reset();
        sequenceActive = false;
        this.targetBoxFade.reset();
        this.anchorBoxFade.reset();
        this.coverBoxFade.reset();
        this.renderedTargetPos = null;
        this.renderedAnchorPos = null;
        this.renderedCoverPos = null;
    }
}

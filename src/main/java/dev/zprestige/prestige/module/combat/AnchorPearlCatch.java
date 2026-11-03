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
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.PearlCatchAnchorPlan;
import dev.zprestige.prestige.data.PearlCatchPlaceTarget;
import dev.zprestige.prestige.data.AnchorCatchPearlLanding;
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.SwapBackTracker;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.type.AnchorCatchState;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;

public class AnchorPearlCatch extends Module implements RotationProvider {
    private FloatSetting scanRange;
    private FloatSetting minDamage;
    private FloatSetting maxSelfDamage;
    private BoolSetting antiSuicide;
    private BoolSetting damageTick;
    private BoolSetting safeAnchor;
    private EnumSetting safeBlock;
    private BoolSetting rotateSilently;
    private FloatSetting silentAimFov;
    private BoolSetting swapBack;
    private RangeSetting placeDelay;
    private RangeSetting chargeDelay;
    private RangeSetting detonateDelay;
    private BoolSetting render;
    private ColorSetting anchorColor;
    private ColorSetting safeColor;
    private ColorSetting landingColor;
    private static final int[][] COVER_OFFSETS = new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1}};
    private static final Direction[] DIRECTIONS = Direction.values();
    public static boolean active;
    private AnchorCatchState state = AnchorCatchState.IDLE;
    private StopWatch placeTimer = new StopWatch();
    private StopWatch chargeTimer = new StopWatch();
    private StopWatch detonateTimer = new StopWatch();
    private StopWatch stallTimer = new StopWatch();
    private StopWatch notifyTimer = new StopWatch();
    private HashSet handledPearls = new HashSet();
    private UUID pearlUuid;
    private UUID throwerUuid;
    private Vec3d landingPos;
    private int ticksUntilLanding = -1;
    private BlockPos anchorPos;
    private BlockPos anchorSupportPos;
    private Vec3d anchorHitVec;
    private BlockPos coverPos;
    private BlockPos coverSupportPos;
    private Vec3d coverHitVec;
    private int swapBackSlot = -1;
    private int clickMisses;
    private int actionTicks;
    private int waitTicks;
    private int detonateTicks;
    private int targetAwayTicks;

    public AnchorPearlCatch() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.anchorColor.visibleWhen((Predicate<Color>)ignored -> this.render.getValue());
        this.landingColor.visibleWhen((Predicate<Color>)ignored -> this.render.getValue());
        this.safeBlock.visibleWhen((Predicate<String>)ignored -> this.safeAnchor.getValue());
        this.silentAimFov.visibleWhen((Predicate<Float>)ignored -> this.rotateSilently.getValue());
        this.safeColor
            .visibleWhen((Predicate<Color>)ignored -> this.render.getValue() && this.safeAnchor.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.handledPearls.clear();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        if (this.swapBackSlot != -1 && this.swapBack.getValue() && Prestige.silentRotationManager.hasActedThisTick()) {
            Prestige.silentRotationManager.scheduleSlotSwitch(this.swapBackSlot);
            SwapBackTracker.release(this);
            this.swapBackSlot = -1;
        }

        this.restoreOriginalSlot();
        this.resetState();
        Prestige.silentRotationManager.removeRotationProvider(this);
        active = false;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState();
        this.handledPearls.clear();
        active = false;
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (this.shouldBlockInput()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
        if (this.shouldBlockInput()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.shouldBlockInput()) {
            event.cancel();
        }
    }

    private boolean shouldBlockInput() {
        return this.state == AnchorCatchState.PLACE_ANCHOR || this.state == AnchorCatchState.SAFE_COVER || this.state == AnchorCatchState.CHARGE_ANCHOR;
    }

    private AnchorCatchPearlLanding predictLanding(EnderPearlEntity pearl) {
        double posX = pearl.getX();
        double posY = pearl.getY();
        double posZ = pearl.getZ();
        Vec3d velocity = pearl.getVelocity();
        double motionX = velocity.x;
        double motionY = velocity.y;
        double motionZ = velocity.z;

        for (int tick = 1; tick <= 200; tick++) {
            Vec3d prevPos = new Vec3d(posX, posY, posZ);
            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            motionX *= 0.99;
            motionZ *= 0.99;
            motionY = motionY * 0.99 - 0.03;
            BlockHitResult hit = mc.world.raycast(new RaycastContext(prevPos, new Vec3d(posX, posY, posZ), ShapeType.OUTLINE, FluidHandling.NONE, pearl));
            if (hit != null && hit.getType() == Type.BLOCK) {
                return new AnchorCatchPearlLanding(hit.getPos(), tick);
            }

            if (posY < -65.0) {
                break;
            }
        }

        return null;
    }

    private EnderPearlEntity findTrackedPearl() {
        if (this.pearlUuid != null && mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl && entity.getUuid().equals(this.pearlUuid)) {
                    return pearl;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private PlayerEntity findThrower() {
        if (this.throwerUuid != null && mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity player && entity.getUuid().equals(this.throwerUuid)) {
                    return player;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private double estimateLandingDamage(Vec3d targetPos, Vec3d explosionPos) {
        double explosionDiameter = 10.0;
        double distanceRatio = Math.sqrt(targetPos.squaredDistanceTo(explosionPos)) / explosionDiameter;
        if (distanceRatio > 1.0) {
            return 0.0;
        } else {
            int visibleRays = 0;

            for (double heightOffset : new double[]{0.2, 1.0, 1.7}) {
                Vec3d samplePos = targetPos.add(0.0, heightOffset, 0.0);
                BlockHitResult hit = mc.world.raycast(new RaycastContext(explosionPos, samplePos, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
                if (hit == null || hit.getType() == Type.MISS) {
                    visibleRays++;
                }
            }

            double impact = (1.0 - distanceRatio) * (visibleRays / 3.0);
            return (impact * impact + impact) / 2.0 * 7.0 * explosionDiameter + 1.0;
        }
    }

    private PearlCatchAnchorPlan findAnchorPlan(Vec3d landing) {
        BlockPos landingBlock = BlockPos.ofFloored(landing);
        Vec3d eyePos = mc.player.getEyePos();
        float selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        Block coverBlock = this.safeBlock.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
        boolean hasCoverBlock = InventoryUtil.findHotbarSlot(coverBlock.asItem()) != null;
        BlockPos feetBlock = landingBlock;
        BlockPos headBlock = landingBlock.up();
        PearlCatchAnchorPlan bestPlan = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidatePos = landingBlock.add(dx, dy, dz);
                    if (!candidatePos.equals(feetBlock) && !candidatePos.equals(headBlock)) {
                        Vec3d candidateCenter = candidatePos.toCenterPos();
                        if (!(eyePos.squaredDistanceTo(candidateCenter) > 19.360000000000003)) {
                            boolean anchorExists = BlockUtil.isOf(candidatePos, Blocks.RESPAWN_ANCHOR);
                            BlockPos supportPos = null;
                            Vec3d hitVec = null;
                            if (anchorExists) {
                                candidatePos = candidatePos.toImmutable();
                            } else {
                                if (BlockUtil.isClickableBlock(candidatePos) || BlockUtil.hasEntityAt(candidatePos)) {
                                    continue;
                                }

                                PearlCatchPlaceTarget placeTarget = this.findPlaceTarget(candidatePos, eyePos);
                                if (placeTarget == null) {
                                    continue;
                                }

                                supportPos = placeTarget.supportPos();
                                hitVec = placeTarget.hitVec();
                            }

                            double targetDamage = this.estimateLandingDamage(landing, candidateCenter);
                            if (!(targetDamage < this.minDamage.getValue().floatValue())) {
                                double selfDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, candidateCenter);
                                if (!this.antiSuicide.getValue() || !(selfDamage >= selfHealth * 2.0F)) {
                                    BlockPos bestCoverPos = null;
                                    BlockPos bestCoverSupportPos = null;
                                    Vec3d bestCoverHitVec = null;
                                    double effectiveSelfDamage = selfDamage;
                                    if (this.safeAnchor.getValue() && hasCoverBlock && selfDamage > 6.0) {
                                        double bestCoveredDamage = selfDamage;

                                        for (int[] offset : COVER_OFFSETS) {
                                            BlockPos coverCandidate = candidatePos.add(offset[0], offset[1], offset[2]);
                                            if (!coverCandidate.equals(feetBlock)
                                                && !coverCandidate.equals(headBlock)
                                                && !BlockUtil.isClickableBlock(coverCandidate)
                                                && !BlockUtil.hasEntityAt(coverCandidate)) {
                                                PearlCatchPlaceTarget coverTarget = this.findPlaceTarget(coverCandidate, eyePos);
                                                if (coverTarget != null) {
                                                    double coveredDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, candidateCenter, coverCandidate, coverBlock.getDefaultState());
                                                    if (!(coveredDamage >= bestCoveredDamage) && !(selfDamage - coveredDamage < 2.0)) {
                                                        bestCoveredDamage = coveredDamage;
                                                        bestCoverPos = coverCandidate;
                                                        bestCoverSupportPos = coverTarget.supportPos();
                                                        bestCoverHitVec = coverTarget.hitVec();
                                                    }
                                                }
                                            }
                                        }

                                        if (bestCoverPos != null) {
                                            effectiveSelfDamage = bestCoveredDamage;
                                        }
                                    }

                                    if (!(effectiveSelfDamage > this.maxSelfDamage.getValue().floatValue()) && (!this.antiSuicide.getValue() || !(effectiveSelfDamage >= selfHealth - 1.0F))) {
                                        double score = targetDamage - 0.5 * landing.distanceTo(candidateCenter) + (anchorExists ? 3.0 : 0.0);
                                        if (score > bestScore) {
                                            bestScore = score;
                                            bestPlan = new PearlCatchAnchorPlan(candidatePos, supportPos, hitVec, bestCoverPos, bestCoverSupportPos, bestCoverHitVec, anchorExists);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestPlan;
    }

    private PearlCatchPlaceTarget findPlaceTarget(BlockPos placePos, Vec3d eyePos) {
        BlockPos bestSupport = null;
        Vec3d bestHitVec = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = placePos.offset(direction);
            if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                Direction face = direction.getOpposite();
                Vec3d hitVec = neighbor.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                double distSq = eyePos.squaredDistanceTo(hitVec);
                if (!(distSq > 18.0625) && !(distSq >= bestDistSq)) {
                    bestDistSq = distSq;
                    bestSupport = neighbor;
                    bestHitVec = hitVec;
                }
            }
        }

        return bestSupport == null ? null : new PearlCatchPlaceTarget(bestSupport, bestHitVec);
    }

    private void scanForPearls() {
        if (InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR) != null) {
            if (InventoryUtil.findHotbarSlot(Items.GLOWSTONE) != null) {
                if (this.findNonGlowstoneSlot() != -1) {
                    double range = this.scanRange.getValue().floatValue();

                    for (Entity entity : mc.world.getEntities()) {
                        if (entity instanceof EnderPearlEntity pearl && !this.handledPearls.contains(pearl.getUuid())) {
                            Vec3d pearlPos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
                            if (!(mc.player.getEyePos().squaredDistanceTo(pearlPos) > range * range)
                                && pearl.getOwner() instanceof PlayerEntity thrower
                                && !thrower.equals(mc.player)
                                && Prestige.botFilter.isValidTarget(thrower)
                                && !Prestige.friendManager.isFriend(thrower.getName().getString())) {
                                AnchorCatchPearlLanding landing = this.predictLanding(pearl);
                                if (landing != null && landing.ticksUntilLanding() <= 100) {
                                    PearlCatchAnchorPlan plan = this.findAnchorPlan(landing.landingPos());
                                    if (plan != null) {
                                        this.pearlUuid = pearl.getUuid();
                                        this.throwerUuid = thrower.getUuid();
                                        this.landingPos = landing.landingPos();
                                        this.ticksUntilLanding = landing.ticksUntilLanding();
                                        this.anchorPos = plan.anchorPos();
                                        this.anchorSupportPos = plan.anchorSupportPos();
                                        this.anchorHitVec = plan.anchorHitVec();
                                        this.coverPos = plan.blockerPos();
                                        this.coverSupportPos = plan.blockerSupportPos();
                                        this.coverHitVec = plan.blockerHitVec();
                                        this.waitTicks = 0;
                                        this.actionTicks = 0;
                                        this.detonateTicks = 0;
                                        this.targetAwayTicks = 0;
                                        this.clickMisses = 0;
                                        this.stallTimer.reset();
                                        this.placeTimer.reset();
                                        this.placeDelay.reroll();
                                        this.chargeTimer.reset();
                                        this.chargeDelay.reroll();
                                        this.detonateTimer.reset();
                                        this.detonateDelay.reroll();
                                        this.state = AnchorCatchState.WAIT;
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void tickWait() {
        PlayerEntity thrower = this.findThrower();
        if (thrower != null && !Prestige.botFilter.isValidTarget(thrower)) {
            this.abort(null, true);
        } else if (++this.waitTicks > 120) {
            this.abort(null, true);
        } else {
            EnderPearlEntity pearl = this.findTrackedPearl();
            if (pearl != null) {
                AnchorCatchPearlLanding landing = this.predictLanding(pearl);
                if (landing == null) {
                    this.abort(null, true);
                } else {
                    this.ticksUntilLanding = landing.ticksUntilLanding();
                    if (landing.landingPos().distanceTo(this.landingPos) > 2.5) {
                        this.abort(null, false);
                    } else {
                        if (landing.ticksUntilLanding() <= this.getLeadTicks()) {
                            this.beginCatch();
                        }
                    }
                }
            } else {
                if (thrower != null && PositionUtil.getEntityPos(thrower).distanceTo(this.landingPos) <= 3.0) {
                    this.beginCatch();
                } else {
                    this.abort(null, true);
                }
            }
        }
    }

    private int getLeadTicks() {
        int ticks = 4 + Math.max(1, Math.round(this.placeDelay.getRolled() / 50.0F));
        if (this.safeAnchor.getValue() && this.coverPos != null) {
            ticks += 3;
        }

        ticks += Math.max(1, Math.round(this.chargeDelay.getRolled() / 50.0F));
        ticks += 3;
        return ticks + 2;
    }

    private void beginCatch() {
        this.actionTicks = 0;
        this.detonateTicks = 0;
        this.targetAwayTicks = 0;
        this.clickMisses = 0;
        this.stallTimer.reset();
        this.chargeTimer.reset();
        this.chargeDelay.reroll();
        if (BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.state = this.safeAnchor.getValue() && this.coverPos != null
                ? AnchorCatchState.SAFE_COVER
                : (BlockUtil.getAnchorCharges(this.anchorPos) > 0 ? AnchorCatchState.DETONATE : AnchorCatchState.CHARGE_ANCHOR);
            if (this.state == AnchorCatchState.DETONATE) {
                this.detonateTimer.reset();
                this.detonateDelay.reroll();
            }
        } else {
            this.state = AnchorCatchState.PLACE_ANCHOR;
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) {
            active = false;
        } else if (this.state == AnchorCatchState.WAIT) {
            this.tickWait();
            active = this.state == AnchorCatchState.WAIT && this.ticksUntilLanding >= 0 && this.ticksUntilLanding <= this.getLeadTicks() + 8;
        } else if (mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            active = false;
        } else if (!AutoObbyCrystal.sequenceActive && !AutoAnchor.sequenceActive && !AnchorMacro.acting) {
            if (this.state != AnchorCatchState.IDLE) {
                if (++this.actionTicks > 160) {
                    this.abort("timed out", true);
                    return;
                }

                if (this.state != AnchorCatchState.DETONATE) {
                    if (this.stallTimer.hasElapsed(200.0F)) {
                        this.abort("stalled", true);
                        return;
                    }
                }

                if (this.state == AnchorCatchState.DETONATE) {
                    this.tickDetonate();
                }
            } else {
                this.restoreOriginalSlot();
                this.scanForPearls();
            }

            if (this.state != AnchorCatchState.IDLE && !this.rotateSilently.getValue()) {
                if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return;
                }

                this.rotateAndPerformStage();
            }

            active = this.state != AnchorCatchState.IDLE;
        } else {
            if (this.state != AnchorCatchState.IDLE) {
                this.abort(null, true);
            }

            active = false;
        }
    }

    private void tickDetonate() {
        if (BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)
            && BlockUtil.getAnchorCharges(this.anchorPos) <= 0) {
            this.state = AnchorCatchState.CHARGE_ANCHOR;
            this.clickMisses = 0;
            this.detonateTicks = 0;
            this.stallTimer.reset();
            this.chargeTimer.reset();
            this.chargeDelay.reroll();
        } else {
            EnderPearlEntity pearl = this.findTrackedPearl();
            if (pearl == null) {
                this.ticksUntilLanding = 0;
            } else {
                AnchorCatchPearlLanding landing = this.predictLanding(pearl);
                this.ticksUntilLanding = landing != null ? landing.ticksUntilLanding() : 0;
            }

            if (++this.detonateTicks > 30 && !this.hasPearlLanded()) {
                this.abort("the pearl never landed", true);
            } else {
                if (InventoryUtil.isHolding(Items.GLOWSTONE) && !Prestige.silentRotationManager.hasActedThisTick()) {
                    int slot = this.findNonGlowstoneSlot();
                    if (slot != -1) {
                        InventoryUtil.selectSlot(slot);
                        Prestige.silentRotationManager.setActedThisTick(true);
                    }
                }

                if (this.hasPearlLanded()) {
                    PlayerEntity thrower = this.findThrower();
                    if (thrower != null && PositionUtil.getEntityPos(thrower).distanceTo(this.anchorPos.toCenterPos()) > 4.5 && ++this.targetAwayTicks > 10) {
                        this.abort("they got away from the anchor", true);
                        return;
                    }
                }
            }
        }
    }

    private boolean hasPearlLanded() {
        if (this.pearlUuid == null) {
            return true;
        } else {
            EnderPearlEntity pearl = this.findTrackedPearl();
            if (pearl == null) {
                return true;
            } else {
                AnchorCatchPearlLanding landing = this.predictLanding(pearl);
                return landing == null || landing.ticksUntilLanding() <= 1;
            }
        }
    }

    private boolean canDetonate() {
        if (!this.hasPearlLanded()) {
            return false;
        } else {
            PlayerEntity thrower = this.findThrower();
            if (thrower != null) {
                if (PositionUtil.getEntityPos(thrower).distanceTo(this.anchorPos.toCenterPos()) > 4.0) {
                    return false;
                }

                if (this.damageTick.getValue() && thrower.hurtTime > 0) {
                    return false;
                }
            }

            return true;
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.rotateSilently.getValue()) {
            return null;
        } else if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            return null;
        } else if (this.state == AnchorCatchState.IDLE || this.anchorPos == null) {
            return null;
        } else if (!AutoObbyCrystal.sequenceActive && !AutoAnchor.sequenceActive && !AnchorMacro.acting) {
            if (Prestige.silentRotationManager.hasActedThisTick()) {
                return null;
            } else {
                boolean waiting = this.state == AnchorCatchState.WAIT;
                if (!waiting || this.ticksUntilLanding >= 0 && this.ticksUntilLanding <= this.getLeadTicks() + 8) {
                    Vec3d aimPoint = waiting ? this.getPreAimPoint() : this.getStageAimPoint();
                    if (aimPoint == null) {
                        if (waiting) {
                            return null;
                        } else {
                            this.registerClickMiss();
                            return null;
                        }
                    } else if (!waiting && mc.player.getEyePos().distanceTo(aimPoint) > 4.4) {
                        this.abort("the anchor left our reach", true);
                        return null;
                    } else {
                        Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                        rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                        if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) >= this.silentAimFov.getValue()) {
                            if (waiting) {
                                return null;
                            } else {
                                this.abort("outside silent aim FOV", true);
                                return null;
                            }
                        } else if (!waiting && !rotation.isClamped() && this.isStageDelayElapsed()) {
                            this.performStage(rotation);
                            return this.state != AnchorCatchState.IDLE && this.state != AnchorCatchState.WAIT ? rotation : null;
                        } else {
                            return rotation;
                        }
                    }
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private Vec3d getPreAimPoint() {
        if (!BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            return this.anchorHitVec;
        } else {
            return this.safeAnchor.getValue() && this.coverPos != null && this.coverHitVec != null ? this.coverHitVec : RaycastUtil.getVisibleTopPoint(this.anchorPos);
        }
    }

    private void rotateAndPerformStage() {
        if (this.isStageDelayElapsed()) {
            Vec3d aimPoint = this.getStageAimPoint();
            if (aimPoint != null && mc.player.getEyePos().distanceTo(aimPoint) > 4.4) {
                this.abort("the anchor left our reach", true);
            } else if (aimPoint == null) {
                this.registerClickMiss();
            } else {
                Rotation rotation = RotationUtil.getRotationTo(aimPoint);
                RotationUtil.applyRotation(rotation);
                this.performStage(rotation);
            }
        }
    }

    private boolean isStageDelayElapsed() {
        return switch (this.state) {
            case PLACE_ANCHOR -> true;
            case SAFE_COVER -> this.placeTimer.hasElapsedRandom(this.placeDelay);
            case CHARGE_ANCHOR -> this.chargeTimer.hasElapsedRandom(this.chargeDelay);
            case DETONATE -> this.detonateTimer.hasElapsedRandom(this.detonateDelay);
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

    private boolean registerClickMiss() {
        if (++this.clickMisses >= 3) {
            this.abort("lost the click line", true);
            return true;
        } else {
            return false;
        }
    }

    private void performStage(Rotation rotation) {
        switch (this.state) {
            case PLACE_ANCHOR:
                this.placeAnchor(rotation);
                break;
            case SAFE_COVER:
                this.placeCover(rotation);
                break;
            case CHARGE_ANCHOR:
                this.chargeAnchor(rotation);
                break;
            case DETONATE:
                this.detonateAnchor(rotation);
        }
    }

    private void placeAnchor(Rotation rotation) {
        if (BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.enterCoverOrChargeStage();
        } else if (BlockUtil.isClickableBlock(this.anchorPos)) {
            this.abort("the anchor spot got blocked", true);
        } else {
            Integer anchorSlot = InventoryUtil.findHotbarSlot(Items.RESPAWN_ANCHOR);
            if (anchorSlot == null) {
                this.abortWithError("No respawn anchor in hotbar.");
            } else {
                BlockHitResult hit = this.raycastBlock(rotation);
                if (hit != null && hit.getBlockPos().equals(this.anchorSupportPos) && hit.getBlockPos().offset(hit.getSide()).equals(this.anchorPos)) {
                    this.clickMisses = 0;
                    if (this.swapBackSlot == -1) {
                        this.swapBackSlot = SwapBackTracker.claim(this);
                    }

                    if (InventoryUtil.getSelectedSlot() != anchorSlot) {
                        InventoryUtil.selectSlot(anchorSlot);
                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.stallTimer.reset();
                    } else {
                        this.clickBlock(hit);
                        this.placeTimer.reset();
                        this.placeDelay.reroll();
                        this.enterCoverOrChargeStage();
                        this.stallTimer.reset();
                    }
                } else {
                    this.registerClickMiss();
                }
            }
        }
    }

    private void enterCoverOrChargeStage() {
        if (this.safeAnchor.getValue() && this.coverPos != null) {
            this.state = AnchorCatchState.SAFE_COVER;
        } else {
            this.enterChargeStage();
        }

        this.clickMisses = 0;
    }

    private void placeCover(Rotation rotation) {
        if (this.coverPos != null && this.coverSupportPos != null && this.coverHitVec != null) {
            if (BlockUtil.isClickableBlock(this.coverPos)) {
                this.enterChargeStage();
            } else {
                Integer coverSlot = InventoryUtil.findHotbarSlot((this.safeBlock.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN).asItem());
                if (coverSlot == null) {
                    this.enterChargeStage();
                } else {
                    BlockHitResult hit = this.raycastBlock(rotation);
                    if (hit != null && hit.getBlockPos().equals(this.coverSupportPos) && hit.getBlockPos().offset(hit.getSide()).equals(this.coverPos)) {
                        this.clickMisses = 0;
                        if (this.swapBackSlot == -1) {
                            this.swapBackSlot = SwapBackTracker.claim(this);
                        }

                        if (InventoryUtil.getSelectedSlot() != coverSlot) {
                            InventoryUtil.selectSlot(coverSlot);
                            Prestige.silentRotationManager.setActedThisTick(true);
                            this.stallTimer.reset();
                        } else {
                            this.clickBlock(hit);
                            this.clickMisses = 0;
                            this.enterChargeStage();
                            this.stallTimer.reset();
                        }
                    } else {
                        this.registerClickMiss();
                    }
                }
            }
        } else {
            this.enterChargeStage();
        }
    }

    private void enterChargeStage() {
        this.state = AnchorCatchState.CHARGE_ANCHOR;
        this.clickMisses = 0;
        this.chargeTimer.reset();
        this.chargeDelay.reroll();
        this.stallTimer.reset();
    }

    private void chargeAnchor(Rotation rotation) {
        if (!BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.registerClickMiss();
        } else if (BlockUtil.getAnchorCharges(this.anchorPos) > 0) {
            this.enterDetonateStage();
        } else {
            Integer glowstoneSlot = InventoryUtil.findHotbarSlot(Items.GLOWSTONE);
            if (glowstoneSlot == null) {
                this.abortWithError("No glowstone in hotbar.");
            } else {
                BlockHitResult hit = this.raycastBlock(rotation);
                if (hit != null && hit.getBlockPos().equals(this.anchorPos)) {
                    this.clickMisses = 0;
                    if (this.swapBackSlot == -1) {
                        this.swapBackSlot = SwapBackTracker.claim(this);
                    }

                    if (InventoryUtil.getSelectedSlot() != glowstoneSlot) {
                        InventoryUtil.selectSlot(glowstoneSlot);
                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.stallTimer.reset();
                    } else {
                        this.clickBlock(hit);
                        this.enterDetonateStage();
                    }
                } else {
                    this.registerClickMiss();
                }
            }
        }
    }

    private void enterDetonateStage() {
        this.state = AnchorCatchState.DETONATE;
        this.clickMisses = 0;
        this.detonateTicks = 0;
        this.targetAwayTicks = 0;
        this.detonateTimer.reset();
        this.detonateDelay.reroll();
        this.stallTimer.reset();
    }

    private void detonateAnchor(Rotation rotation) {
        if (!BlockUtil.isOf(this.anchorPos, Blocks.RESPAWN_ANCHOR)) {
            this.registerClickMiss();
        } else if (BlockUtil.getAnchorCharges(this.anchorPos) <= 0) {
            this.state = AnchorCatchState.CHARGE_ANCHOR;
            this.clickMisses = 0;
            this.stallTimer.reset();
            this.chargeTimer.reset();
            this.chargeDelay.reroll();
        } else if (this.canDetonate()) {
            double selfDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, this.anchorPos.toCenterPos());
            float selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (selfDamage > this.maxSelfDamage.getValue().floatValue()) {
                this.abort("self damage too high", true);
            } else if (this.antiSuicide.getValue() && selfDamage >= selfHealth - 1.0F) {
                this.abort("would have killed us", true);
            } else if (InventoryUtil.isHolding(Items.GLOWSTONE)) {
                int detonateSlot = this.swapBackSlot != -1 ? this.swapBackSlot : this.findNonGlowstoneSlot();
                if (detonateSlot == -1) {
                    this.abortWithError("No non-glowstone slot to detonate with.");
                } else {
                    InventoryUtil.selectSlot(detonateSlot);
                    Prestige.silentRotationManager.setActedThisTick(true);
                    this.stallTimer.reset();
                }
            } else {
                BlockHitResult hit = this.raycastBlock(rotation);
                if (hit != null && hit.getBlockPos().equals(this.anchorPos)) {
                    this.clickMisses = 0;
                    this.clickBlock(hit);
                    this.finishCatch();
                } else {
                    this.registerClickMiss();
                }
            }
        }
    }

    private void finishCatch() {
        if (this.pearlUuid != null) {
            this.handledPearls.add(this.pearlUuid);
        }

        this.restoreOriginalSlot();
        this.resetState();
    }

    private void abort(String reason, boolean markHandled) {
        if (reason != null) {
            if (this.notifyTimer.hasElapsed(3000.0F)) {
                ModuleNotificationsElement.postNotification(this, "Anchor Pearl Catch: " + reason, NotificationType.INFO);
                this.notifyTimer.reset();
            }
        }

        if (markHandled && this.pearlUuid != null) {
            this.handledPearls.add(this.pearlUuid);
        }

        this.restoreOriginalSlot();
        this.resetState();
    }

    private void abortWithError(String message) {
        if (this.notifyTimer.hasElapsed(3000.0F)) {
            ModuleNotificationsElement.postNotification(this, message, NotificationType.ERROR);
            this.notifyTimer.reset();
        }

        this.abort(null, true);
    }

    private void resetState() {
        this.state = AnchorCatchState.IDLE;
        this.pearlUuid = null;
        this.throwerUuid = null;
        this.landingPos = null;
        this.ticksUntilLanding = -1;
        this.anchorPos = null;
        this.anchorSupportPos = null;
        this.anchorHitVec = null;
        this.coverPos = null;
        this.coverSupportPos = null;
        this.coverHitVec = null;
        this.clickMisses = 0;
        this.actionTicks = 0;
        this.waitTicks = 0;
        this.detonateTicks = 0;
        this.targetAwayTicks = 0;
        active = false;
    }

    private void restoreOriginalSlot() {
        if (this.swapBackSlot != -1) {
            if (mc.player == null) {
                SwapBackTracker.release(this);
                this.swapBackSlot = -1;
            } else if (!SwapBackTracker.isOwner(this)) {
                this.swapBackSlot = -1;
            } else if (!Prestige.silentRotationManager.hasActedThisTick()) {
                if (this.swapBack.getValue()) {
                    InventoryUtil.selectSlot(this.swapBackSlot);
                    Prestige.silentRotationManager.setActedThisTick(true);
                }

                SwapBackTracker.release(this);
                this.swapBackSlot = -1;
            }
        }
    }

    private void clickBlock(BlockHitResult hit) {
        boolean accepted = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted();
        Prestige.silentRotationManager.setActedThisTick(true);
        if (accepted) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private BlockHitResult raycastBlock(Rotation rotation) {
        return RotationUtil.raycastRotation(rotation) instanceof BlockHitResult blockHit ? blockHit : null;
    }

    private int findNonGlowstoneSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() != Items.GLOWSTONE) {
                return slot;
            }
        }

        return -1;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.render.getValue() && mc.player != null && mc.world != null) {
            if (this.state != AnchorCatchState.IDLE) {
                if (this.state == AnchorCatchState.WAIT && this.landingPos != null) {
                    this.drawBlockOutline(event, BlockPos.ofFloored(this.landingPos), this.landingColor.getValue());
                }

                if (this.anchorPos != null && this.state != AnchorCatchState.WAIT) {
                    this.drawBlockOutline(event, this.anchorPos, this.anchorColor.getValue());
                }

                if (this.state == AnchorCatchState.SAFE_COVER && this.coverPos != null) {
                    this.drawBlockOutline(event, this.coverPos, this.safeColor.getValue());
                }
            }
        }
    }

    private void drawBlockOutline(Render3DEvent event, BlockPos pos, Color color) {
        CameraMatrices matrices = event.context;
        BufferSource buffers = event.renderer;
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        ShapeRenderer.drawBoxOutline(matrices, buffers, minX, minY, minZ, pos.getX() + 1.0F, pos.getY() + 1.0F, pos.getZ() + 1.0F, color);
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (this.render.getValue()) {
            if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) {
                if (mc.world != null && mc.player != null) {
                    if (this.state != AnchorCatchState.IDLE && this.anchorPos != null) {
                        String etaText = this.ticksUntilLanding > 0 ? String.format("%.1fs", this.ticksUntilLanding / 20.0F) : "!";
                        Vec3d screenPos = Render2D.worldToScreen(this.state == AnchorCatchState.WAIT && this.landingPos != null
                            ? new Vec3d(this.landingPos.x, this.landingPos.y + 0.8, this.landingPos.z)
                            : new Vec3d(this.anchorPos.getX() + 0.5, this.anchorPos.getY() + 1.3, this.anchorPos.getZ() + 0.5));
                        if (!(screenPos.z < 0.0) && !(screenPos.z >= 1.0)) {
                            MsdfFont font = Prestige.fontManager.getDefaultFont();
                            float lineHeight = font.getLineHeight();
                            String title = "Anchor";
                            float titleWidth = font.getTextWidth(title);
                            float etaWidth = font.getTextWidth(etaText);
                            float lineGap = 3.0F;
                            float dividerHeight = 0.5F;
                            float paddingX = 4.0F;
                            float paddingY = 3.0F;
                            float cornerRadius = 5.0F;
                            float contentWidth = Math.max(titleWidth, etaWidth);
                            float contentHeight = lineHeight + lineGap + dividerHeight + lineGap + lineHeight;
                            float boxWidth = contentWidth + 2.0F * paddingX;
                            float boxHeight = contentHeight + 2.0F * paddingY;
                            float centerX = (float)screenPos.x;
                            float centerY = (float)screenPos.y;
                            float boxX = centerX - boxWidth / 2.0F;
                            float boxY = centerY - boxHeight / 2.0F;
                            Matrix4f matrix = new Matrix4f();
                            Color backgroundColor = new Color(14, 14, 14, 200);
                            Color dividerColor = new Color(255, 255, 255, 50);
                            Color textColor = new Color(255, 255, 255, 255);
                            Color titleColor = this.anchorColor.getValue();
                            BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, boxX, boxY, boxWidth, boxHeight, 5, cornerRadius);
                            RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, boxX, boxY, boxWidth, boxHeight, backgroundColor, cornerRadius);
                            font.drawText(matrix, title, centerX - titleWidth / 2.0F, boxY + paddingY, titleColor);
                            float dividerLeft = boxX + paddingX;
                            float dividerRight = boxX + boxWidth - paddingX;
                            float dividerY = boxY + paddingY + lineHeight + lineGap;
                            ShapeRenderer.drawRect(matrix, dividerLeft, dividerY, dividerRight, dividerY + dividerHeight, dividerColor);
                            font.drawText(matrix, etaText, centerX - etaWidth / 2.0F, dividerY + dividerHeight + lineGap, textColor);
                        }
                    }
                }
            }
        }
    }
}

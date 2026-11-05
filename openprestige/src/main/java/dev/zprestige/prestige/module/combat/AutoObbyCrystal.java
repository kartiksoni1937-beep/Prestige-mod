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
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.math.ObbyCrystalCandidate;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.ToggleAnimation;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.SwapBackTracker;
import dev.zprestige.prestige.type.ObbyCrystalState;
import dev.zprestige.prestige.type.Easing;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public class AutoObbyCrystal extends Module implements RotationProvider {
    private FloatSetting targetRange;
    private FloatSetting minDamage;
    private FloatSetting maxSelfDamage;
    private BoolSetting antiSuicide;
    private EnumSetting breakMode;
    private BoolSetting rotateSilently;
    private BoolSetting swapBack;
    private BoolSetting render;
    private BoolSetting advanced;
    private FloatSetting maxTargetDistance;
    private RangeSetting placeDelay;
    private RangeSetting breakDelay;
    private RangeSetting firstActionDelay;
    private IntSetting midSequenceMovementTolerance;
    private FloatSetting midSequenceDamageTolerance;
    private BoolSetting debugAborts;
    private BoolSetting fastSwap;
    private FloatSetting silentAimFov;
    private EnumSetting momentumMode;
    private FloatSetting momentumWeight;
    private FloatSetting yawChangeWeight;
    private FloatSetting yawChangeCap;
    private BoolSetting prefer1x1;
    private BoolSetting preferExistingObby;
    private BoolSetting onlyExistingObby;
    private IntSetting pathLookahead;
    private BoolSetting predictTarget;
    private FloatSetting predictTicks;
    private FloatSetting predictPingBonus;
    private FloatSetting predictMaxOffset;
    private BoolSetting predictSelf;
    private FloatSetting selfPredictTicks;
    private FloatSetting selfPredictMaxOffset;
    private FloatSetting reach;
    private BoolSetting renderTarget;
    private BoolSetting renderPlacement;
    private BoolSetting renderCandidates;
    private IntSetting maxCandidates;
    private ColorSetting obsidianColor;
    private ColorSetting crystalColor;
    private ColorSetting targetColor;
    private ColorSetting candidateColor;
    public static boolean sequenceActive;
    private ObbyCrystalState state = ObbyCrystalState.IDLE;
    private StopWatch placeTimer = new StopWatch();
    private StopWatch breakTimer = new StopWatch();
    private StopWatch stageTimer = new StopWatch();
    private StopWatch firstActionTimer = new StopWatch();
    private boolean actedThisSequence;
    private boolean obsidianJustPlaced;
    private PlayerEntity target;
    private BlockPos basePos;
    private BlockPos supportPos;
    private Direction placeSide;
    private Vec3d hitVec;
    private BlockPos targetStartPos;
    private int swapBackSlot = -1;
    private boolean breakTimeoutRetried;
    private boolean crystalOutOfReach;
    private int lastAttackedCrystalId = -1;
    private List candidates = new ArrayList();
    private Vec3d serverEyePos;
    private Entity trackedEntity;
    private Vec3d trackedLastPos;
    private Vec3d trackedVelocity = Vec3d.ZERO;
    private long trackedLastTime = Long.MIN_VALUE;
    private ToggleAnimation targetFade = new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT);
    private ToggleAnimation placementFade = new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT);
    private LinkedHashMap candidateFades = new LinkedHashMap();
    private HashSet renderedCandidates = new HashSet();
    private BlockPos lastTargetPos;
    private BlockPos lastBasePos;
    private int targetDrift;
    private StopWatch errorNotifyTimer = new StopWatch();

    public AutoObbyCrystal() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.maxTargetDistance.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.silentAimFov
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.rotateSilently.getValue());
        this.firstActionDelay.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.predictTarget.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.predictPingBonus
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.predictTarget.getValue());
        this.renderTarget
            .visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue() && this.render.getValue());
        this.reach.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.predictTicks
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.predictTarget.getValue());
        this.midSequenceDamageTolerance.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.debugAborts.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.candidateColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.advanced.getValue() && this.render.getValue() && this.renderCandidates.getValue()
            );
        this.crystalColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.advanced.getValue() && this.render.getValue() && this.renderPlacement.getValue()
            );
        this.targetColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.advanced.getValue() && this.render.getValue() && this.renderTarget.getValue()
            );
        this.yawChangeWeight.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.predictMaxOffset
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.predictTarget.getValue());
        this.selfPredictTicks
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.predictSelf.getValue());
        this.breakDelay.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.yawChangeCap
            .visibleWhen(
                (Predicate<Float>)ignored -> this.advanced.getValue() && this.yawChangeWeight.getValue() > 0.0F
            );
        this.selfPredictMaxOffset
            .visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue() && this.predictSelf.getValue());
        this.midSequenceMovementTolerance.visibleWhen((Predicate<Integer>)ignored -> this.advanced.getValue());
        this.fastSwap.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.momentumMode.visibleWhen((Predicate<String>)ignored -> this.advanced.getValue());
        this.onlyExistingObby.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.renderCandidates
            .visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue() && this.render.getValue());
        this.momentumWeight
            .visibleWhen(
                (Predicate<Float>)ignored -> this.advanced.getValue() && !this.momentumMode.getValue().equals("None")
            );
        this.obsidianColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.advanced.getValue() && this.render.getValue() && this.renderPlacement.getValue()
            );
        this.placeDelay.visibleWhen((Predicate<Float>)ignored -> this.advanced.getValue());
        this.maxCandidates
            .visibleWhen(
                (Predicate<Integer>)ignored -> this.advanced.getValue() && this.render.getValue() && this.renderCandidates.getValue()
            );
        this.preferExistingObby.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.pathLookahead.visibleWhen((Predicate<Integer>)ignored -> this.advanced.getValue());
        this.prefer1x1.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
        this.renderPlacement
            .visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue() && this.render.getValue());
        this.predictSelf.visibleWhen((Predicate<Boolean>)ignored -> this.advanced.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        this.resetState();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        if (this.swapBackSlot != -1 && this.swapBack.getValue() && Prestige.silentRotationManager.hasActedThisTick()) {
            Prestige.silentRotationManager.scheduleSlotSwitch(this.swapBackSlot);
            SwapBackTracker.release(this);
            this.swapBackSlot = -1;
        }

        this.restoreSwapSlot();
        this.resetState();
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Override
    public int getRotationPriority() {
        return 30;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState();
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (this.state != ObbyCrystalState.IDLE) {
            if (!this.breakMode.getValue().equals("Manual")
                || this.state != ObbyCrystalState.BREAK_CRYSTAL
                || !(mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystalEntity)) {
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
        if (this.state != ObbyCrystalState.IDLE) {
            event.cancel();
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.state != ObbyCrystalState.IDLE) {
            event.cancel();
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (event.getPhase() == EventPhase.PRE && mc.player != null) {
            this.serverEyePos = mc.player.getEyePos();
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.render.getValue() && mc.player != null && mc.world != null) {
            float pulse = getPulse();
            boolean showTarget = this.renderTarget.getValue() && this.target != null;
            if (showTarget) {
                this.lastTargetPos = this.target.getBlockPos();
            }

            float targetProgress = updateFade(this.targetFade, showTarget);
            if (this.renderTarget.getValue() && targetProgress > 0.01F && this.lastTargetPos != null) {
                Color targetBoxColor = fadeColor(this.targetColor.getValue(), targetProgress, pulse);
                if (targetBoxColor.getAlpha() > 0) {
                    this.drawOutline(event, this.lastTargetPos, targetBoxColor);
                    this.drawOutline(event, this.lastTargetPos.up(), targetBoxColor);
                }
            }

            boolean showPlacement = this.renderPlacement.getValue() && this.basePos != null;
            if (showPlacement) {
                this.lastBasePos = this.basePos;
            }

            float placementProgress = updateFade(this.placementFade, showPlacement);
            if (this.renderPlacement.getValue() && placementProgress > 0.01F && this.lastBasePos != null) {
                boolean hasBase = BlockUtil.isOf(this.lastBasePos, Blocks.OBSIDIAN)
                    || BlockUtil.isOf(this.lastBasePos, Blocks.BEDROCK);
                this.drawBox(event, this.lastBasePos, fadeColor(this.obsidianColor.getValue(), placementProgress, pulse), !hasBase, pulse);
                this.drawBox(event, this.lastBasePos.up(), fadeColor(this.crystalColor.getValue(), placementProgress, pulse), true, pulse);
            }

            this.renderedCandidates.clear();
            boolean showCandidates = this.renderCandidates.getValue();
            if (showCandidates && this.state == ObbyCrystalState.IDLE && !this.candidates.isEmpty()) {
                int count = Math.min(this.candidates.size(), this.maxCandidates.getValue());
                Color color = this.candidateColor.getValue();

                for (int index = 0; index < count; index++) {
                    BlockPos candidatePos = ((ObbyCrystalCandidate)this.candidates.get(index)).basePos.up();
                    this.renderedCandidates.add(candidatePos);
                    ToggleAnimation fade = (ToggleAnimation)this.candidateFades.computeIfAbsent(candidatePos, ignored -> new ToggleAnimation(180.0F, false, Easing.SINE_IN_OUT));
                    if (!fade.getState()) {
                        fade.setState(true);
                    }

                    int alpha = scaleAlpha(Math.max(30, color.getAlpha() - color.getAlpha() * index / Math.max(1, count)), fade.getProgress(), pulse);
                    if (alpha > 0) {
                        this.drawOutline(event, candidatePos, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    }
                }
            }

            Iterator iterator = this.candidateFades.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry entry = (Entry)iterator.next();
                if (!this.renderedCandidates.contains(entry.getKey())) {
                    ToggleAnimation staleFade = (ToggleAnimation)entry.getValue();
                    if (staleFade.getState()) {
                        staleFade.setState(false);
                    }

                    float progress = staleFade.getProgress();
                    if (progress < 0.01F) {
                        iterator.remove();
                    } else if (showCandidates) {
                        Color staleColor = this.candidateColor.getValue();
                        int staleAlpha = scaleAlpha(30, progress, pulse);
                        if (staleAlpha > 0) {
                            this.drawOutline(
                                event, (BlockPos)entry.getKey(), new Color(staleColor.getRed(), staleColor.getGreen(), staleColor.getBlue(), staleAlpha)
                            );
                        }
                    }
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

    private void drawOutline(Render3DEvent event, BlockPos pos, Color color) {
        CameraMatrices matrices = event.context;
        BufferSource buffers = event.renderer;
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        ShapeRenderer.drawBoxOutline(matrices, buffers, minX, minY, minZ, pos.getX() + 1.0F, pos.getY() + 1.0F, pos.getZ() + 1.0F, color);
    }

    private void drawBox(Render3DEvent event, BlockPos pos, Color color, boolean filled, float pulse) {
        if (filled) {
            Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(1, color.getAlpha() / 4));
            float inflate = 0.01F * pulse;
            ShapeRenderer.drawFilledBox(event.context, event.renderer, pos.getX() - inflate, pos.getY() - inflate, pos.getZ() - inflate, pos.getX() + 1.0F + inflate, pos.getY() + 1.0F + inflate, pos.getZ() + 1.0F + inflate, fillColor);
        }

        this.drawOutline(event, pos, color);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            sequenceActive = false;
        } else if (AutoHitCrystal.active) {
            if (this.state != ObbyCrystalState.IDLE) {
                this.abort("AutoHitCrystal enabled");
            }

            this.restoreSwapSlot();
            sequenceActive = false;
        } else {
            this.trackVelocity((Entity)(this.target != null ? this.target : TargetSelector.getClosestEnemy()));
            if (this.state != ObbyCrystalState.IDLE && !this.isTargetValid()) {
                this.abort("invalid target");
            }

            if (this.state != ObbyCrystalState.IDLE && this.hasTargetMoved()) {
                this.abort("targetMoved d=" + this.targetDrift);
            }

            if (this.state != ObbyCrystalState.IDLE) {
                if (this.stageTimer.hasElapsed(this.getStageTimeout())) {
                    if (this.state == ObbyCrystalState.BREAK_CRYSTAL && !this.breakTimeoutRetried) {
                        this.breakTimeoutRetried = true;
                        this.state = ObbyCrystalState.PLACE_CRYSTAL;
                        this.stageTimer.reset();
                    } else {
                        this.abort("stageTimeout " + (int)this.getStageTimeout() + "ms");
                    }
                }
            }

            if (this.state == ObbyCrystalState.IDLE) {
                this.restoreSwapSlot();
                this.startSequence();
            } else if (this.state == ObbyCrystalState.BREAK_CRYSTAL && this.breakMode.getValue().equals("Manual")) {
                if (this.basePos != null && !this.hasCrystalAt(this.basePos.up()) && this.breakTimer.hasElapsedRandom(this.breakDelay)) {
                    this.state = ObbyCrystalState.PLACE_CRYSTAL;
                    this.stageTimer.reset();
                }
            } else if (!this.rotateSilently.getValue()) {
                if (Prestige.silentRotationManager.hasActedThisTick()) {
                    return;
                }

                this.rotateAndAct();
            }

            sequenceActive = this.state != ObbyCrystalState.IDLE;
        }
    }

    private void trackVelocity(Entity entity) {
        long worldTime = mc.world.getTime();
        if (entity == null) {
            this.trackedEntity = null;
            this.trackedLastPos = null;
            this.trackedVelocity = Vec3d.ZERO;
            this.trackedLastTime = worldTime;
        } else {
            Vec3d pos = PositionUtil.getEntityPos(entity);
            if (entity == this.trackedEntity && this.trackedLastPos != null && worldTime - this.trackedLastTime == 1L) {
                this.trackedVelocity = this.trackedVelocity.multiply(0.5).add(pos.subtract(this.trackedLastPos).multiply(0.5));
                this.trackedLastPos = pos;
                this.trackedLastTime = worldTime;
            } else {
                this.trackedEntity = entity;
                this.trackedLastPos = pos;
                this.trackedLastTime = worldTime;
                this.trackedVelocity = Vec3d.ZERO;
            }
        }
    }

    private Vec3d getTargetVelocity() {
        return this.target != null && this.trackedEntity == this.target ? this.trackedVelocity : Vec3d.ZERO;
    }

    private boolean isTargetMoving() {
        Vec3d velocity = this.getTargetVelocity();
        return velocity.x * velocity.x + velocity.z * velocity.z > 4.0E-4;
    }

    private double getPredictedDamage(Vec3d crystalPos, Vec3d predictedPos, BlockPos crystalBase, double ignoredDamage) {
        return Prestige.damageCalculator.getCrystalDamageAt(this.target, crystalPos, predictedPos, crystalBase);
    }

    @Override
    public Rotation provideRotation() {
        if (!this.rotateSilently.getValue()) {
            return null;
        } else if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused() || mc.player.isUsingItem()) {
            return null;
        } else if (this.state == ObbyCrystalState.IDLE || this.basePos == null || this.target == null) {
            return null;
        } else if (AutoHitCrystal.active) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (!this.isActionReady()) {
            return null;
        } else {
            Vec3d aimPoint = this.getAimPoint();
            if (this.isOutOfReach(aimPoint)) {
                this.abort("unreachable");
                return null;
            } else if (aimPoint == null) {
                return null;
            } else {
                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.silentAimFov.getValue()) {
                    this.abort("silentAimFov");
                    return null;
                } else if (rotation.isClamped()) {
                    return rotation;
                } else {
                    this.executeStage(rotation);
                    return this.state == ObbyCrystalState.IDLE ? null : rotation;
                }
            }
        }
    }

    private void rotateAndAct() {
        if (this.isActionReady()) {
            Vec3d aimPoint = this.getAimPoint();
            if (this.isOutOfReach(aimPoint)) {
                this.abort("unreachable");
            } else if (aimPoint != null) {
                Rotation rotation = RotationUtil.getRotationTo(aimPoint);
                RotationUtil.applyRotation(rotation);
                this.executeStage(rotation);
            }
        }
    }

    private boolean isActionReady() {
        if (!this.actedThisSequence) {
            return this.state == ObbyCrystalState.BREAK_CRYSTAL && this.breakMode.getValue().equals("Manual")
                ? false
                : this.state != ObbyCrystalState.IDLE && this.firstActionTimer.hasElapsedRandom(this.firstActionDelay);
        } else {
            return switch (this.state) {
                case PLACE_OBSIDIAN -> true;
                case PLACE_CRYSTAL -> this.obsidianJustPlaced || this.placeTimer.hasElapsedRandom(this.placeDelay);
                case BREAK_CRYSTAL -> !this.breakMode.getValue().equals("Manual") && this.breakTimer.hasElapsedRandom(this.breakDelay);
                default -> false;
            };
        }
    }

    private Vec3d getAimPoint() {
        switch (this.state) {
            case PLACE_OBSIDIAN:
                return this.hitVec;
            case PLACE_CRYSTAL:
                return RaycastUtil.getVisiblePoint(this.basePos);
            case BREAK_CRYSTAL:
                EndCrystalEntity crystal = this.findBreakableCrystal();
                return crystal == null ? null : this.getCrystalAimPoint(crystal);
            default:
                return null;
        }
    }

    private boolean isOutOfReach(Vec3d aimPoint) {
        if (this.state == ObbyCrystalState.BREAK_CRYSTAL) {
            return this.crystalOutOfReach;
        } else {
            return aimPoint == null ? false : mc.player.getEyePos().distanceTo(aimPoint) > 4.4 || this.serverEyePos != null && this.serverEyePos.distanceTo(aimPoint) > 4.4;
        }
    }

    private void executeStage(Rotation rotation) {
        switch (this.state) {
            case PLACE_OBSIDIAN:
                this.placeObsidian(rotation);
                break;
            case PLACE_CRYSTAL:
                this.placeCrystal(rotation);
                break;
            case BREAK_CRYSTAL:
                this.breakCrystal(rotation);
        }
    }

    private Vec3d getCrystalAimPoint(EndCrystalEntity crystal) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = crystal.getBoundingBox();
        Vec3d closest = new Vec3d(
            MathHelper.clamp(eyePos.x, box.minX, box.maxX), MathHelper.clamp(eyePos.y, box.minY, box.maxY), MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
        );
        Vec3d toCenter = box.getCenter().subtract(closest);
        return toCenter.lengthSquared() > 1.0E-6 ? closest.add(toCenter.normalize().multiply(0.15)) : closest;
    }

    private boolean ensureSlotSelected(int slot) {
        if (InventoryUtil.getSelectedSlot() == slot) {
            return true;
        } else {
            InventoryUtil.selectSlot(slot);
            if (this.fastSwap.getValue()) {
                return true;
            } else {
                Prestige.silentRotationManager.setActedThisTick(true);
                this.stageTimer.reset();
                return false;
            }
        }
    }

    private void placeObsidian(Rotation rotation) {
        if (!BlockUtil.isOf(this.basePos, Blocks.OBSIDIAN) && !BlockUtil.isOf(this.basePos, Blocks.BEDROCK)) {
            if (!BlockUtil.isClickableBlock(this.basePos)) {
                if (!this.hasBlockingEntity(this.basePos, 1)) {
                    Integer obsidianSlot = InventoryUtil.findHotbarSlot(Items.OBSIDIAN);
                    if (obsidianSlot == null) {
                        this.failWithError("No obsidian in hotbar.");
                        return;
                    }

                    if (RotationUtil.raycastRotation(rotation) instanceof BlockHitResult blockHit
                        && blockHit.getBlockPos().equals(this.supportPos)
                        && blockHit.getBlockPos().offset(blockHit.getSide()).equals(this.basePos)) {
                        if (this.swapBackSlot == -1) {
                            this.swapBackSlot = SwapBackTracker.claim(this);
                        }

                        if (!this.ensureSlotSelected(obsidianSlot)) {
                            return;
                        }

                        this.clickBlock(blockHit);
                        this.obsidianJustPlaced = true;
                        this.state = ObbyCrystalState.PLACE_CRYSTAL;
                        this.stageTimer.reset();
                        return;
                    }

                    return;
                }
            }

            this.abort("base blocked");
        } else {
            this.state = ObbyCrystalState.PLACE_CRYSTAL;
            this.stageTimer.reset();
        }
    }

    private void placeCrystal(Rotation rotation) {
        if (BlockUtil.isOf(this.basePos, Blocks.OBSIDIAN) || BlockUtil.isOf(this.basePos, Blocks.BEDROCK)) {
            BlockPos spawnPos = this.basePos.up();
            if (this.hasCrystalAt(spawnPos)) {
                this.state = ObbyCrystalState.BREAK_CRYSTAL;
                this.stageTimer.reset();
            } else {
                if (mc.world.getBlockState(spawnPos).isAir()) {
                    if (!this.hasBlockingEntity(spawnPos, 2)) {
                        Vec3d crystalPos = new Vec3d(this.basePos.getX() + 0.5, this.basePos.getY() + 1.0, this.basePos.getZ() + 0.5);
                        double rawDamage = Prestige.damageCalculator.getCrystalDamage(this.target, crystalPos, false, this.basePos, false);
                        double bestDamage;
                        if (this.predictTarget.getValue() && this.isTargetMoving()) {
                            bestDamage = Math.max(rawDamage, this.getPredictedDamage(crystalPos, this.predictTargetPos(), this.basePos, rawDamage));
                        } else {
                            bestDamage = rawDamage;
                        }

                        double targetDamage = bestDamage;
                        double selfDamage = Prestige.damageCalculator.getCrystalDamage(mc.player, crystalPos, false, this.basePos, false);
                        if (targetDamage < this.minDamage.getValue() * this.midSequenceDamageTolerance.getValue()) {
                            this.abort("re-gate targetDamage " + String.format("%.1f", targetDamage));
                            return;
                        }

                        if (!(selfDamage > this.maxSelfDamage.getValue().floatValue())
                            && (!this.antiSuicide.getValue() || !(selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0F))) {
                            Integer crystalSlot = InventoryUtil.findHotbarSlot(Items.END_CRYSTAL);
                            if (crystalSlot == null) {
                                this.failWithError("No end crystals in hotbar.");
                                return;
                            }

                            if (RotationUtil.raycastRotation(rotation) instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(this.basePos)) {
                                if (this.swapBackSlot == -1) {
                                    this.swapBackSlot = SwapBackTracker.claim(this);
                                }

                                if (!this.ensureSlotSelected(crystalSlot)) {
                                    return;
                                }

                                this.clickBlock(blockHit);
                                this.breakTimer.reset();
                                this.breakDelay.reroll();
                                this.obsidianJustPlaced = false;
                                this.state = ObbyCrystalState.BREAK_CRYSTAL;
                                this.stageTimer.reset();
                                return;
                            }

                            return;
                        }

                        this.abort("re-gate selfDamage " + String.format("%.1f", selfDamage));
                        return;
                    }
                }

                this.abort("crystal spawn blocked");
            }
        }
    }

    private void breakCrystal(Rotation rotation) {
        EndCrystalEntity crystal = this.findBreakableCrystal();
        if (crystal != null) {
            Box box = crystal.getBoundingBox();
            if (this.canRayHitBox(box, mc.player.getEyePos(), rotation)
                && (this.serverEyePos == null || this.canRayHitBox(box, this.serverEyePos, rotation))) {
                Vec3d crystalPos = new Vec3d(this.basePos.getX() + 0.5, this.basePos.getY() + 1.0, this.basePos.getZ() + 0.5);
                double selfDamage = Prestige.damageCalculator.getCrystalDamage(mc.player, crystalPos, false, this.basePos, false);
                if (!(selfDamage > this.maxSelfDamage.getValue().floatValue())
                    && (!this.antiSuicide.getValue() || !(selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0F))) {
                    if (!AutoCrystal.active || !(mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() == crystal)) {
                        if (this.breakMode.getValue().equals("SwapAttack") && this.swapBackSlot != -1 && InventoryUtil.getSelectedSlot() != this.swapBackSlot) {
                            InventoryUtil.selectSlot(this.swapBackSlot);
                            Prestige.silentRotationManager.setActedThisTick(true);
                            this.stageTimer.reset();
                        } else {
                            this.attackCrystal(crystal);
                            this.lastAttackedCrystalId = crystal.getId();
                            this.placeTimer.reset();
                            this.placeDelay.reroll();
                            this.breakTimeoutRetried = false;
                            this.state = ObbyCrystalState.PLACE_CRYSTAL;
                            this.stageTimer.reset();
                        }
                    }
                }
            }
        }
    }

    private void clickBlock(BlockHitResult blockHit) {
        boolean accepted = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit).isAccepted();
        Prestige.silentRotationManager.setActedThisTick(true);
        this.actedThisSequence = true;
        if (accepted) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void attackCrystal(Entity crystal) {
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        Prestige.silentRotationManager.setActedThisTick(true);
        this.actedThisSequence = true;
    }

    private boolean hasCrystalAt(BlockPos pos) {
        if (this.lastAttackedCrystalId != -1 && mc.world.getEntityById(this.lastAttackedCrystalId) == null) {
            this.lastAttackedCrystalId = -1;
        }

        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        return !mc.world.getOtherEntities(null, box, entity -> entity instanceof EndCrystalEntity && entity.getId() != this.lastAttackedCrystalId).isEmpty();
    }

    private boolean hasBlockingEntity(BlockPos pos, int height) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0);
        return !mc.world
            .getOtherEntities(
                null, box, entity -> entity instanceof ItemEntity ? false : !(entity instanceof EndCrystalEntity) || entity.getId() != this.lastAttackedCrystalId
            )
            .isEmpty();
    }

    private boolean isBlockedOrInPath(BlockPos pos, int height) {
        if (this.hasBlockingEntity(pos, height)) {
            return true;
        } else {
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0);
            int lookahead = this.pathLookahead.getValue();
            if (lookahead <= 0) {
                return false;
            } else {
                if (this.target != null) {
                    Vec3d targetVelocity = this.getTargetVelocity();
                    if (targetVelocity.lengthSquared() > 1.0E-6) {
                        Box targetBox = this.target.getBoundingBox();

                        for (int step = 1; step <= lookahead; step++) {
                            if (targetBox.offset(targetVelocity.multiply(step)).intersects(box)) {
                                return true;
                            }
                        }
                    }
                }

                Vec3d selfVelocity = mc.player.getVelocity();
                if (selfVelocity.lengthSquared() > 1.0E-6) {
                    Box selfBox = mc.player.getBoundingBox();

                    for (int selfStep = 1; selfStep <= lookahead; selfStep++) {
                        if (selfBox.offset(selfVelocity.multiply(selfStep)).intersects(box)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }
    }

    private void startSequence() {
        PlayerEntity enemy = TargetSelector.getClosestEnemy();
        if (enemy != null && enemy.isAlive() && !(mc.player.distanceTo(enemy) > this.targetRange.getValue())) {
            this.target = enemy;
            ObbyCrystalCandidate candidate = this.findBestCandidate();
            if (candidate == null) {
                this.target = null;
            } else {
                this.basePos = candidate.basePos;
                this.supportPos = candidate.supportPos;
                this.placeSide = candidate.placeSide;
                this.hitVec = candidate.hitVec;
                this.targetStartPos = this.target.getBlockPos();
                this.breakTimeoutRetried = false;
                this.state = candidate.hasCrystal ? ObbyCrystalState.BREAK_CRYSTAL : (candidate.needsObsidian ? ObbyCrystalState.PLACE_OBSIDIAN : ObbyCrystalState.PLACE_CRYSTAL);
                this.stageTimer.reset();
                this.actedThisSequence = false;
                this.obsidianJustPlaced = false;
                this.firstActionTimer.reset();
                this.firstActionDelay.reroll();
                this.placeTimer.reset();
                this.breakTimer.reset();
                this.placeDelay.reroll();
                this.breakDelay.reroll();
            }
        }
    }

    private Vec3d predictTargetPos() {
        Vec3d targetPos = PositionUtil.getEntityPos(this.target);
        if (!this.predictTarget.getValue()) {
            return targetPos;
        } else {
            Vec3d velocity = this.getTargetVelocity();
            if (velocity.lengthSquared() < 1.0E-6) {
                return targetPos;
            } else {
                int ping = 0;
                PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(this.target.getUuid());
                if (entry != null) {
                    ping = Math.max(0, entry.getLatency());
                }

                float leadTicks = MathHelper.clamp(this.predictTicks.getValue() + ping / 50.0F * this.predictPingBonus.getValue(), 0.0F, 20.0F);
                Vec3d offset = new Vec3d(velocity.x * leadTicks, 0.0, velocity.z * leadTicks);
                double maxOffset = this.predictMaxOffset.getValue().floatValue();
                if (offset.lengthSquared() > maxOffset * maxOffset) {
                    offset = offset.lengthSquared() > 1.0E-6 ? offset.normalize().multiply(maxOffset) : Vec3d.ZERO;
                }

                return targetPos.add(offset);
            }
        }
    }

    private Vec3d predictEyePos() {
        Vec3d eyePos = mc.player.getEyePos();
        if (!this.predictSelf.getValue()) {
            return eyePos;
        } else {
            Vec3d velocity = mc.player.getVelocity();
            if (velocity.lengthSquared() < 1.0E-6) {
                return eyePos;
            } else {
                Vec3d offset = new Vec3d(velocity.x * this.selfPredictTicks.getValue().floatValue(), 0.0, velocity.z * this.selfPredictTicks.getValue().floatValue());
                double maxOffset = this.selfPredictMaxOffset.getValue().floatValue();
                if (offset.lengthSquared() > maxOffset * maxOffset) {
                    offset = offset.lengthSquared() > 1.0E-6 ? offset.normalize().multiply(maxOffset) : Vec3d.ZERO;
                }

                return eyePos.add(offset);
            }
        }
    }

    private double getYawChange(Vec3d point) {
        Rotation rotation = Prestige.silentRotationManager.getRotationTowards(point);
        if (rotation == null) {
            return -1.0;
        } else {
            return Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw()));
        }
    }

    private ObbyCrystalCandidate findBestCandidate() {
        BlockPos targetFeet = this.target.getBlockPos();
        Vec3d predictedTargetPos = this.predictTargetPos();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d predictedEyePos = this.predictEyePos();
        Vec3d momentumDir = this.getMomentumDirection(predictedTargetPos);
        float selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean usePrediction = this.predictTarget.getValue() && this.isTargetMoving();
        ObbyCrystalCandidate best = null;
        this.candidates.clear();
        Mutable scanPos = new Mutable();

        for (int offsetX = -4; offsetX <= 4; offsetX++) {
            for (int offsetZ = -4; offsetZ <= 4; offsetZ++) {
                for (int offsetY = -2; offsetY <= 2; offsetY++) {
                    scanPos.set(targetFeet.getX() + offsetX, targetFeet.getY() + offsetY, targetFeet.getZ() + offsetZ);
                    double centerX = scanPos.getX() + 0.5;
                    double centerY = scanPos.getY() + 0.5;
                    double centerZ = scanPos.getZ() + 0.5;
                    if (!(eyePos.squaredDistanceTo(centerX, centerY, centerZ) > 27.5625) || !(predictedEyePos.squaredDistanceTo(centerX, centerY, centerZ) > 27.5625)) {
                        BlockPos spawnPos = scanPos.up();
                        boolean existingBase = BlockUtil.isOf(scanPos, Blocks.OBSIDIAN) || BlockUtil.isOf(scanPos, Blocks.BEDROCK);
                        boolean crystalPresent = existingBase && this.hasCrystalAt(spawnPos);
                        if (!crystalPresent) {
                            if (!mc.world.getBlockState(spawnPos).isAir()) {
                                continue;
                            }

                            if (this.isBlockedOrInPath(spawnPos, 2)) {
                                continue;
                            }
                        }

                        BlockPos base = scanPos.toImmutable();
                        BlockPos support = null;
                        Direction side = null;
                        Vec3d aimPoint;
                        if (existingBase) {
                            if (crystalPresent) {
                                Vec3d crystalCenter = new Vec3d(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5);
                                if (eyePos.distanceTo(crystalCenter) > this.reach.getValue().floatValue() + 0.5
                                    && predictedEyePos.distanceTo(crystalCenter) > this.reach.getValue().floatValue() + 0.5) {
                                    continue;
                                }

                                if (this.rotateSilently.getValue()) {
                                    Rotation rotation = Prestige.silentRotationManager.getRotationTowards(crystalCenter);
                                    if (rotation == null) {
                                        continue;
                                    }

                                    if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.silentAimFov.getValue()) {
                                        continue;
                                    }
                                }

                                aimPoint = crystalCenter;
                            } else {
                                aimPoint = RaycastUtil.getVisiblePoint(base);
                                if (aimPoint == null || this.rotateSilently.getValue() && !this.canHitSilently(aimPoint, base)) {
                                    continue;
                                }
                            }
                        } else {
                            if (this.onlyExistingObby.getValue() || BlockUtil.isClickableBlock(base)) {
                                continue;
                            }

                            if (this.isBlockedOrInPath(base, 1)) {
                                continue;
                            }

                            aimPoint = null;
                            double bestSupportDist = Double.MAX_VALUE;

                            for (Direction direction : Direction.values()) {
                                BlockPos neighbor = base.offset(direction);
                                if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                                    Direction opposite = direction.getOpposite();
                                    Vec3d faceCenter = neighbor.toCenterPos().add(opposite.getOffsetX() * 0.5, opposite.getOffsetY() * 0.5, opposite.getOffsetZ() * 0.5);
                                    double faceDist = eyePos.distanceTo(faceCenter);
                                    if ((!(faceDist > 4.25) || !(predictedEyePos.distanceTo(faceCenter) > 4.25))
                                        && (!this.rotateSilently.getValue() || this.canHitSilently(faceCenter, neighbor))
                                        && faceDist < bestSupportDist) {
                                        bestSupportDist = faceDist;
                                        support = neighbor;
                                        side = opposite;
                                        aimPoint = faceCenter;
                                    }
                                }
                            }

                            if (support == null
                                || eyePos.distanceTo(base.toCenterPos().add(0.0, 0.5, 0.0)) > 4.25
                                    && predictedEyePos.distanceTo(base.toCenterPos().add(0.0, 0.5, 0.0)) > 4.25) {
                                continue;
                            }
                        }

                        Vec3d crystalPos = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
                        double rawDamage = Prestige.damageCalculator.getCrystalDamage(this.target, crystalPos, false, base, false);
                        double damage = rawDamage;
                        if (usePrediction && rawDamage < this.minDamage.getValue().floatValue()) {
                            damage = Math.max(rawDamage, this.getPredictedDamage(crystalPos, predictedTargetPos, base, rawDamage));
                        }

                        if (!(damage < this.minDamage.getValue().floatValue())) {
                            double selfDamage = Prestige.damageCalculator.getCrystalDamage(mc.player, crystalPos, false, base, false);
                            if (!(selfDamage > this.maxSelfDamage.getValue().floatValue()) && (!this.antiSuicide.getValue() || !(selfDamage >= selfHealth - 1.0F))) {
                                double score = damage;
                                if (damage >= this.target.getHealth() + this.target.getAbsorptionAmount()) {
                                    score = damage + 50.0;
                                }

                                if (momentumDir != null) {
                                    Vec3d pushDir = predictedTargetPos.subtract(crystalPos);
                                    pushDir = new Vec3d(pushDir.x, 0.0, pushDir.z);
                                    if (pushDir.lengthSquared() > 1.0E-4) {
                                        score += this.momentumWeight.getValue().floatValue() * 8.0 * Math.max(0.0, pushDir.normalize().dotProduct(momentumDir));
                                    }
                                }

                                if (this.prefer1x1.getValue() && this.isAdjacentToFeet(base, targetFeet)) {
                                    score += 3.0;
                                }

                                if (existingBase && this.preferExistingObby.getValue()) {
                                    score += crystalPresent ? 8.0 : 4.0;
                                }

                                if (this.yawChangeWeight.getValue() > 0.0F) {
                                    double yawChange = this.getYawChange(aimPoint);
                                    if (yawChange >= 0.0) {
                                        score -= this.yawChangeWeight.getValue().floatValue() * Math.min(yawChange, (double)this.yawChangeCap.getValue().floatValue());
                                    }
                                }

                                score -= 0.2 * eyePos.distanceTo(aimPoint);
                                ObbyCrystalCandidate candidate = new ObbyCrystalCandidate(base, support, side, aimPoint, !existingBase, crystalPresent, score);
                                this.candidates.add(candidate);
                                if (best == null || score > best.score) {
                                    best = candidate;
                                }
                            }
                        }
                    }
                }
            }
        }

        this.candidates.sort(Comparator.<ObbyCrystalCandidate>comparingDouble(entry -> entry.score).reversed());
        return best;
    }

    private boolean canHitSilently(Vec3d point, BlockPos pos) {
        Rotation rotation = Prestige.silentRotationManager.getRotationTowards(point);
        if (rotation == null) {
            return false;
        } else {
            if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.silentAimFov.getValue()) {
                return false;
            } else {
                return rotation.isClamped()
                    ? true
                    : RotationUtil.raycastRotation(rotation) instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos);
            }
        }
    }

    private Vec3d getMomentumDirection(Vec3d targetPos) {
        String mode = this.momentumMode.getValue();
        if (mode.equals("None")) {
            return null;
        } else {
            switch (mode) {
                case "Away From Self":
                    Vec3d awayFromSelf = targetPos.subtract(PositionUtil.getPlayerPos());
                    awayFromSelf = new Vec3d(awayFromSelf.x, 0.0, awayFromSelf.z);
                    return awayFromSelf.lengthSquared() > 1.0E-4 ? awayFromSelf.normalize() : null;
                case "Toward Center":
                    Vec3d towardCenter = new Vec3d(-targetPos.x, 0.0, -targetPos.z);
                    return towardCenter.lengthSquared() > 1.0E-4 ? towardCenter.normalize() : null;
                case "Toward Hole":
                    BlockPos hole = this.findNearestHole();
                    if (hole == null) {
                        return null;
                    }

                    Vec3d towardHole = hole.toCenterPos().subtract(targetPos);
                    towardHole = new Vec3d(towardHole.x, 0.0, towardHole.z);
                    return towardHole.lengthSquared() > 1.0E-4 ? towardHole.normalize() : null;
                default:
                    return null;
            }
        }
    }

    private BlockPos findNearestHole() {
        BlockPos targetFeet = this.target.getBlockPos();
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        Mutable scanPos = new Mutable();

        for (int offsetX = -6; offsetX <= 6; offsetX++) {
            for (int offsetZ = -6; offsetZ <= 6; offsetZ++) {
                scanPos.set(targetFeet.getX() + offsetX, targetFeet.getY(), targetFeet.getZ() + offsetZ);
                if (mc.world.getBlockState(scanPos).isAir() && mc.world.getBlockState(scanPos.up()).isAir() && BlockUtil.isClickableBlock(scanPos.down())) {
                    boolean surrounded = true;

                    for (Direction direction : Direction.values()) {
                        if (direction.getOffsetY() == 0 && !BlockUtil.isClickableBlock(scanPos.offset(direction))) {
                            surrounded = false;
                            break;
                        }
                    }

                    if (surrounded) {
                        double distSq = offsetX * offsetX + offsetZ * offsetZ;
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = scanPos.toImmutable();
                        }
                    }
                }
            }
        }

        return nearest;
    }

    private boolean isAdjacentToFeet(BlockPos base, BlockPos feet) {
        return base.getY() == feet.getY() && Math.max(Math.abs(base.getX() - feet.getX()), Math.abs(base.getZ() - feet.getZ())) == 1;
    }

    private EndCrystalEntity findBreakableCrystal() {
        this.crystalOutOfReach = false;
        if (this.basePos != null && this.hasCrystalAt(this.basePos.up())) {
            Vec3d spawnCenter = this.basePos.up().toCenterPos();
            Vec3d eyePos = mc.player.getEyePos();
            Box searchBox = new Box(this.basePos.up()).expand(1.5);

            for (Entity entity : mc.world.getOtherEntities(null, searchBox, nearby -> nearby instanceof EndCrystalEntity)) {
                if (entity instanceof EndCrystalEntity crystal
                    && crystal.isAlive()
                    && crystal.getId() != this.lastAttackedCrystalId
                    && crystal.age >= 2
                    && !(PositionUtil.getEntityPos(crystal).distanceTo(spawnCenter) > 1.0)) {
                    Box box = crystal.getBoundingBox();
                    double distance = distanceToBox(box, eyePos);
                    if (this.serverEyePos != null) {
                        distance = Math.max(distance, distanceToBox(box, this.serverEyePos));
                    }

                    if (!(distance > this.reach.getValue().floatValue())) {
                        return crystal;
                    }

                    this.crystalOutOfReach = true;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static double distanceToBox(Box box, Vec3d point) {
        double dx = Math.max(box.minX - point.x, Math.max(0.0, point.x - box.maxX));
        double dy = Math.max(box.minY - point.y, Math.max(0.0, point.y - box.maxY));
        double dz = Math.max(box.minZ - point.z, Math.max(0.0, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean canRayHitBox(Box box, Vec3d origin, Rotation rotation) {
        return box.raycast(origin, origin.add(RotationUtil.getDirectionVector(rotation).multiply(6.0))).map(hitPos -> origin.distanceTo(hitPos) <= this.reach.getValue().floatValue()).orElse(false);
    }

    private boolean isTargetValid() {
        return this.target != null
            && this.target.isAlive()
            && mc.player.distanceTo(this.target) <= this.maxTargetDistance.getValue()
            && Prestige.botFilter.isValidTarget(this.target);
    }

    private boolean hasTargetMoved() {
        if (this.targetStartPos != null && this.target != null) {
            BlockPos currentPos = this.target.getBlockPos();
            this.targetDrift = Math.max(Math.abs(currentPos.getX() - this.targetStartPos.getX()), Math.max(Math.abs(currentPos.getY() - this.targetStartPos.getY()), Math.abs(currentPos.getZ() - this.targetStartPos.getZ())));
            return this.targetDrift > (this.state == ObbyCrystalState.PLACE_OBSIDIAN ? 2 : this.midSequenceMovementTolerance.getValue());
        } else {
            return false;
        }
    }

    private void failWithError(String message) {
        if (this.errorNotifyTimer.hasElapsed(3000.0F)) {
            ModuleNotificationsElement.postNotification(this, message, NotificationType.ERROR);
            this.errorNotifyTimer.reset();
        }

        this.resetSequence();
    }

    private void abort(String reason) {
        if (this.debugAborts.getValue() && this.state != ObbyCrystalState.IDLE) {
            ModuleNotificationsElement.postNotification(this, "abort: " + this.state + ", " + reason, NotificationType.INFO);
        }

        this.resetSequence();
    }

    private float getStageTimeout() {
        int ping = 0;
        PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
        if (entry != null) {
            ping = Math.max(0, entry.getLatency());
        }

        return this.state == ObbyCrystalState.BREAK_CRYSTAL
            ? Math.min(1500.0F, Math.max(500.0F, 300.0F + ping * 1.5F))
            : Math.min(600.0F, Math.max(200.0F, 120.0F + ping * 0.8F));
    }

    private void restoreSwapSlot() {
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

    private void resetSequence() {
        this.restoreSwapSlot();
        this.state = ObbyCrystalState.IDLE;
        this.target = null;
        this.basePos = null;
        this.supportPos = null;
        this.placeSide = null;
        this.hitVec = null;
        this.targetStartPos = null;
        this.breakTimeoutRetried = false;
        this.obsidianJustPlaced = false;
        this.candidates.clear();
        sequenceActive = false;
    }

    private void resetState() {
        this.state = ObbyCrystalState.IDLE;
        this.target = null;
        this.basePos = null;
        this.supportPos = null;
        this.placeSide = null;
        this.hitVec = null;
        this.targetStartPos = null;
        this.swapBackSlot = -1;
        this.breakTimeoutRetried = false;
        this.obsidianJustPlaced = false;
        this.actedThisSequence = false;
        this.lastAttackedCrystalId = -1;
        this.serverEyePos = null;
        this.trackedEntity = null;
        this.trackedLastPos = null;
        this.trackedVelocity = Vec3d.ZERO;
        this.trackedLastTime = Long.MIN_VALUE;
        this.candidates.clear();
        sequenceActive = false;
        this.targetFade.reset();
        this.placementFade.reset();
        this.candidateFades.clear();
        this.renderedCandidates.clear();
        this.lastTargetPos = null;
        this.lastBasePos = null;
    }
}

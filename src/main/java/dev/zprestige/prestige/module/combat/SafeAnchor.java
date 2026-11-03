package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.AnchorUseGuard;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.math.BlockChangeTracker;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.DelayTimer;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SafeAnchor extends Module implements RotationProvider {
    private EnumSetting mode;
    private BoolSetting onlyOwn;
    private FloatSetting minDamage;
    private FloatSetting maxDamage;
    private EnumSetting coverTiming;
    private RangeSetting delay;
    private EnumSetting swap;
    private EnumSetting block;
    private IntSetting limit;
    private FloatSetting fov;
    private HashMap coverCounts = new HashMap();
    private HashMap reactionTimers = new HashMap();
    private BlockPos lastPlacedAnchor;
    private int lastAnchorCharges;
    private ArrayList nearbyAnchors = new ArrayList();
    private ArrayList ownAnchors = new ArrayList();
    private StopWatch delayTimer = new StopWatch();
    private static boolean coverPending;

    public SafeAnchor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.coverTiming.visibleWhen((Predicate<String>)ignored -> this.mode.getValue().equals("Legit"));
        this.fov.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Blatant"));
        this.onlyOwn.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Blatant"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    public static boolean isCoverPending() {
        return coverPending;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.coverCounts.clear();
        this.reactionTimers.clear();
        this.nearbyAnchors.clear();
        this.ownAnchors.clear();
        this.lastPlacedAnchor = null;
        this.lastAnchorCharges = 0;
        coverPending = false;
    }

    private void updateCoverPending() {
        coverPending = false;
        if (this.mode.getValue().equalsIgnoreCase("Legit")) {
            if (this.lastPlacedAnchor == null) {
                return;
            }

            if (!BlockUtil.isOf(this.lastPlacedAnchor, Blocks.RESPAWN_ANCHOR)
                || PositionUtil.getPlayerPos().distanceTo(this.lastPlacedAnchor.toCenterPos()) > PlayerInteractions.getReachDistance()
                || mc.player.getY() < this.lastPlacedAnchor.getY()
                || (Integer)this.coverCounts.getOrDefault(this.lastPlacedAnchor, 0) >= this.limit.getValue()) {
                this.lastAnchorCharges = 0;
                return;
            }

            int charges = BlockUtil.getAnchorCharges(this.lastPlacedAnchor);
            if (charges > 0 && this.lastAnchorCharges == 0) {
                this.delayTimer.reset();
                this.delay.reroll();
            }

            this.lastAnchorCharges = charges;
            if (!this.coverTiming.getValue().equals("Before Charge") && charges == 0) {
                return;
            }

            if (this.delayTimer.hasElapsed(this.delay.getRolled() + 250.0F)) {
                return;
            }

            if (this.swap.getValue().equals("None")
                && !InventoryUtil.isHolding((this.block.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN).asItem())) {
                return;
            }

            coverPending = Prestige.damageCalculator.getAnchorDamage(mc.player, this.lastPlacedAnchor.toCenterPos()) >= this.minDamage.getValue().floatValue();
        } else {
            for (BlockPos anchorPos : (Iterable<BlockPos>)(this.reactionTimers.keySet())) {
                if (BlockUtil.isOf(anchorPos, Blocks.RESPAWN_ANCHOR)
                    && (!this.onlyOwn.getValue() || this.ownAnchors.contains(anchorPos))
                    && (Integer)this.coverCounts.getOrDefault(anchorPos, 0) < this.limit.getValue()) {
                    DelayTimer reactionTimer = (DelayTimer)this.reactionTimers.get(anchorPos);
                    if ((reactionTimer != null && !reactionTimer.hasElapsed() || !this.delayTimer.hasElapsedRandom(this.delay)) && Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos()) >= this.minDamage.getValue().floatValue()) {
                        coverPending = true;
                        return;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.updateCoverPending();
        if (this.onlyOwn.getValue()) {
            this.ownAnchors.removeIf(ownAnchorPos -> mc.world.getBlockState(((net.minecraft.util.math.BlockPos)ownAnchorPos)).getBlock() != Blocks.RESPAWN_ANCHOR);
        }

        this.coverCounts.keySet().removeIf(coveredAnchorPos -> !BlockUtil.isOf(((net.minecraft.util.math.BlockPos)coveredAnchorPos), Blocks.RESPAWN_ANCHOR));
        this.reactionTimers.keySet().removeIf(timedAnchorPos -> !BlockUtil.isOf(((net.minecraft.util.math.BlockPos)timedAnchorPos), Blocks.RESPAWN_ANCHOR));
        if (this.mode.getValue().equalsIgnoreCase("Legit") && mc.currentScreen == null && !mc.player.isUsingItem() && mc.player.isOnGround()) {
            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                if (this.lastPlacedAnchor != null) {
                    if (BlockUtil.isOf(this.lastPlacedAnchor, Blocks.RESPAWN_ANCHOR)
                        && !(
                            PositionUtil.getPlayerPos().distanceTo(this.lastPlacedAnchor.toCenterPos())
                                > PlayerInteractions.getReachDistance()
                        )) {
                        if (this.delayTimer.hasElapsedRandom(this.delay)) {
                            BlockPos anchorPos = this.lastPlacedAnchor;
                            if (!(mc.player.getY() < anchorPos.getY())) {
                                Block coverBlock = this.block.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
                                if (!this.swap.getValue().equals("None") || InventoryUtil.isHolding(coverBlock.asItem())) {
                                    if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
                                        if (!BlockChangeTracker.isRecentlyExploded(crosshairHit.getBlockPos())
                                            && !BlockChangeTracker.isRecentlyPlaced(crosshairHit.getBlockPos())) {
                                            BlockPos hitPos = crosshairHit.getBlockPos();
                                            if (!anchorPos.equals(hitPos)
                                                && (Integer)this.coverCounts.getOrDefault(anchorPos, 0) < this.limit.getValue()
                                                && (
                                                    !this.coverTiming.getValue().equals("After Charge")
                                                        || BlockUtil.getAnchorCharges(anchorPos) != 0
                                                )) {
                                                if (!(
                                                    Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos())
                                                        < this.minDamage.getValue().floatValue()
                                                )) {
                                                    if ((
                                                            !BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.FIRE)
                                                                    && !BlockUtil.isOf(
                                                                        crosshairHit.getBlockPos(),
                                                                            BlockUtil.getShortGrass()
                                                                    )
                                                                ? hitPos.getY() < anchorPos.getY()
                                                                : hitPos.getY() <= anchorPos.getY()
                                                        )
                                                        && hitPos.getY() >= anchorPos.getY() - 1) {
                                                        if (!(
                                                            mc.player.squaredDistanceTo(anchorPos.toCenterPos()) > mc.player.squaredDistanceTo(this.lastPlacedAnchor.toCenterPos())
                                                        )) {
                                                            if (!(
                                                                anchorPos.getSquaredDistance(this.lastPlacedAnchor.toCenterPos())
                                                                    > mc.player.squaredDistanceTo(this.lastPlacedAnchor.toCenterPos())
                                                            )) {
                                                                BlockPos coverPos = !BlockUtil.isOf(hitPos, Blocks.FIRE)
                                                                        && !BlockUtil.isOf(
                                                                            hitPos, BlockUtil.getShortGrass()
                                                                        )
                                                                    ? hitPos.add(
                                                                        crosshairHit.getSide().getOffsetX(),
                                                                        crosshairHit.getSide().getOffsetY(),
                                                                        crosshairHit.getSide().getOffsetZ()
                                                                    )
                                                                    : hitPos;
                                                                BlockState previousState = mc.world.getBlockState(coverPos);
                                                                mc.world.setBlockState(coverPos, coverBlock.getDefaultState());
                                                                double coveredDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos());
                                                                mc.world.setBlockState(coverPos, previousState);
                                                                if (!(coveredDamage > this.maxDamage.getValue().floatValue())) {
                                                                    this.coverCounts.merge(anchorPos, 1, (java.util.function.BiFunction)(oldCount, increment) -> Integer.sum((Integer)oldCount, (Integer)increment));
                                                                    Prestige.silentRotationManager.clearRotation();
                                                                    InventoryUtil.runWithBlock(coverBlock, () -> PlayerInteractions.interactBlock(crosshairHit), this.swap.getValue().equals("Silent"));
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        this.lastPlacedAnchor = null;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (AnchorUseGuard.shouldCancelItemUse()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            if ((interactPacket.getHand().equals(Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem() == Items.RESPAWN_ANCHOR) {
                BlockHitResult hitResult = interactPacket.getBlockHitResult();
                BlockPos placePos = hitResult.getBlockPos().add(hitResult.getSide().getOffsetX(), hitResult.getSide().getOffsetY(), hitResult.getSide().getOffsetZ());
                if (!this.onlyOwn.getValue() || this.ownAnchors.contains(placePos)) {
                    return;
                }

                this.ownAnchors
                    .add(
                        !BlockUtil.isOf(hitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                                && !BlockUtil.isOf(hitResult.getBlockPos(), Blocks.FIRE)
                                && !BlockUtil.isOf(hitResult.getBlockPos(), BlockUtil.getShortGrass())
                            ? placePos
                            : hitResult.getBlockPos()
                    );
            }
        }

        if (this.mode.getValue().equalsIgnoreCase("Legit")) {
            if (event.getPacket() instanceof PlayerInteractBlockC2SPacket legitPacket) {
                if ((legitPacket.getHand().equals(Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem() == Items.RESPAWN_ANCHOR) {
                    BlockHitResult legitHit = legitPacket.getBlockHitResult();
                    BlockPos legitPlacePos = legitHit.getBlockPos().add(legitHit.getSide().getOffsetX(), legitHit.getSide().getOffsetY(), legitHit.getSide().getOffsetZ());
                    this.lastPlacedAnchor = !BlockUtil.isOf(legitHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                            && !BlockUtil.isOf(legitHit.getBlockPos(), Blocks.FIRE)
                            && !BlockUtil.isOf(legitHit.getBlockPos(), BlockUtil.getShortGrass())
                        ? legitPlacePos
                        : legitHit.getBlockPos();
                    this.lastAnchorCharges = 0;
                    this.delayTimer.reset();
                    this.delay.reroll();
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
        this.coverCounts.clear();
        this.reactionTimers.clear();
        this.nearbyAnchors.clear();
        this.lastPlacedAnchor = null;
        this.lastAnchorCharges = 0;
        coverPending = false;
    }

    @Override
    public Rotation provideRotation() {
        if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (!this.mode.getValue().equalsIgnoreCase("Blatant")) {
            return null;
        } else if (mc.currentScreen == null && !mc.player.isUsingItem()) {
            if (!this.delayTimer.hasElapsedRandom(this.delay)) {
                return null;
            } else if (this.swap.getValue().equals("None")
                && !InventoryUtil.isHolding((net.minecraft.item.Item)(this.block.getValue().equals("Glowstone") ? Blocks.GLOWSTONE.asItem() : Blocks.OBSIDIAN.asItem()))
                )
             {
                return null;
            } else {
                Block coverBlock = this.block.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
                this.nearbyAnchors.clear();

                for (BlockPos scannedPos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(10, (Predicate<BlockPos>)pos -> BlockUtil.isOf(pos, Blocks.RESPAWN_ANCHOR)))) {
                    if (PositionUtil.getPlayerPos().distanceTo(scannedPos.toCenterPos()) <= 10.0) {
                        this.nearbyAnchors.add(scannedPos);
                        this.reactionTimers.computeIfAbsent(scannedPos, ignored -> new DelayTimer(this.delay.randomInRange()));
                    }
                }

                HitResult bestHit = null;
                Rotation bestRotation = null;
                BlockPos bestAnchorPos = null;
                double bestCoveredDamage = Double.MAX_VALUE;

                for (BlockPos anchorPos : (Iterable<BlockPos>)(this.nearbyAnchors)) {
                    if (this.ownAnchors.contains(anchorPos) || !this.onlyOwn.getValue()) {
                        DelayTimer reactionTimer = (DelayTimer)this.reactionTimers.get(anchorPos);
                        if (reactionTimer != null
                            && reactionTimer.hasElapsed()
                            && !(Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos()) < this.minDamage.getValue().floatValue())
                            && (Integer)this.coverCounts.getOrDefault(anchorPos, 0) < this.limit.getValue()) {
                            for (int dx = -5; dx <= 5; dx++) {
                                for (int dz = -5; dz <= 5; dz++) {
                                    if (!(
                                        PositionUtil.getPlayerPos().distanceTo(anchorPos.toCenterPos()) > PlayerInteractions.getReachDistance()
                                    )) {
                                        BlockPos supportPos = anchorPos.add(dx, -1, dz);
                                        if (BlockUtil.isClickableBlock(supportPos) && !BlockUtil.hasEntityAt(supportPos.up())) {
                                            BlockPos coverPos = anchorPos.add(dx, 0, dz);
                                            double coveredDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos(), coverPos, coverBlock.getDefaultState());
                                            if (!(coveredDamage > this.maxDamage.getValue().floatValue())) {
                                                Vec3d aimPoint = RaycastUtil.getVisibleTopPoint(supportPos);
                                                if (aimPoint != null) {
                                                    Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                                                    float yawDelta = MathHelper.wrapDegrees(rotation.getYaw() - mc.player.getYaw());
                                                    if (!(yawDelta > this.fov.getValue()) && !(yawDelta < -this.fov.getValue())) {
                                                        HitResult rayHit = RotationUtil.raycastRotation(rotation);
                                                        if (!(rayHit.getPos().distanceTo(aimPoint) > 0.5) && (bestHit == null || coveredDamage < bestCoveredDamage)) {
                                                            bestHit = rayHit;
                                                            bestCoveredDamage = coveredDamage;
                                                            bestRotation = rotation;
                                                            bestAnchorPos = anchorPos;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (bestHit == null) {
                    return null;
                } else if (bestRotation.isClamped()) {
                    return bestRotation;
                } else {
                    HitResult hitToPlace = bestHit;
                    if (!(
                        hitToPlace instanceof BlockHitResult blockHit
                            && (
                                BlockChangeTracker.isRecentlyExploded(blockHit.getBlockPos())
                                    || BlockChangeTracker.isRecentlyPlaced(blockHit.getBlockPos())
                            )
                    )) {
                        InventoryUtil.runWithBlock(coverBlock, () -> PlayerInteractions.interactBlock((BlockHitResult)hitToPlace), this.swap.getValue().equals("Silent"));
                        this.coverCounts.merge(bestAnchorPos, 1, (java.util.function.BiFunction)(oldCount, increment) -> Integer.sum((Integer)oldCount, (Integer)increment));
                        this.delayTimer.reset();
                        this.delay.reroll();
                        return bestRotation;
                    } else {
                        return bestRotation;
                    }
                }
            }
        } else {
            return null;
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState() == null || blockUpdate.getState().getBlock() == Blocks.RESPAWN_ANCHOR) {
                return;
            }

            mc.execute(() -> this.ownAnchors.remove(blockUpdate.getPos()));
        }
    }
}

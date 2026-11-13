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
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
    private StopWatch delayTimer = new StopWatch();
    private static boolean coverPending;

    public SafeAnchor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        // Own-anchor tracking has to run from startup, not from onEnable, or anchors placed earlier are unknown.
        BlockChangeTracker.init();
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
        this.lastPlacedAnchor = null;
        this.lastAnchorCharges = 0;
        coverPending = false;
    }

    /** Nearest respawn anchor this client placed that is still standing and in reach, or null. */
    private BlockPos closestOwnAnchor() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object entry : BlockChangeTracker.getOwnAnchors()) {
            BlockPos pos = (BlockPos)entry;
            double distance = PositionUtil.getPlayerPos().distanceTo(pos.toCenterPos());
            if (distance <= PlayerInteractions.getReachDistance() && distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    /** True when a block can be put into this position: air, a replaceable block, fire or short grass. */
    private boolean canCover(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isReplaceable() || state.isOf(Blocks.FIRE) || state.isOf(BlockUtil.getShortGrass());
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
                    && (!this.onlyOwn.getValue() || BlockChangeTracker.isOwnAnchor(anchorPos))
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
        // After enabling mid-fight the module never saw the placement, so adopt the closest own anchor first.
        if (this.lastPlacedAnchor == null || !BlockUtil.isOf(this.lastPlacedAnchor, Blocks.RESPAWN_ANCHOR)) {
            this.lastPlacedAnchor = this.closestOwnAnchor();
        }
        this.updateCoverPending();

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
                                        BlockPos hitPos = crosshairHit.getBlockPos();
                                        BlockPos coverPos = BlockUtil.getPlacePos(crosshairHit);
                                        boolean touchesAnchor = Math.abs(coverPos.getX() - anchorPos.getX()) <= 1
                                            && Math.abs(coverPos.getY() - anchorPos.getY()) <= 1
                                            && Math.abs(coverPos.getZ() - anchorPos.getZ()) <= 1;
                                        // The cover has to land in a free block next to the anchor. The old version required
                                        // the crosshair on the block directly under the anchor, whose only usable face
                                        // points up, so the cover position always resolved to the anchor itself: the client
                                        // asked to place into an occupied block, the server refused, and the anchor's one
                                        // allowed cover was used up anyway. Now any face that produces a free block touching
                                        // the anchor is accepted, and the damage simulation decides whether it is worth it.
                                        if (BlockUtil.isClickableBlock(hitPos)
                                            && !anchorPos.equals(hitPos)
                                            && !anchorPos.equals(coverPos)
                                            && touchesAnchor
                                            && this.canCover(coverPos)
                                            && !BlockUtil.hasEntityAt(coverPos)
                                            && (Integer)this.coverCounts.getOrDefault(anchorPos, 0) < this.limit.getValue()
                                            && (
                                                !this.coverTiming.getValue().equals("After Charge")
                                                    || BlockUtil.getAnchorCharges(anchorPos) != 0
                                            )
                                            && !BlockChangeTracker.isRecentlyExploded(coverPos)
                                            && !BlockChangeTracker.isRecentlyPlaced(coverPos)) {
                                            double uncoveredDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos());
                                            double coveredDamage = Prestige.damageCalculator.getAnchorDamage(
                                                mc.player, anchorPos.toCenterPos(), coverPos, coverBlock.getDefaultState()
                                            );
                                            if (uncoveredDamage >= this.minDamage.getValue().floatValue()
                                                && coveredDamage < uncoveredDamage
                                                && coveredDamage <= this.maxDamage.getValue().floatValue()) {
                                                this.coverCounts.merge(anchorPos, 1, (java.util.function.BiFunction)(oldCount, increment) -> Integer.sum((Integer)oldCount, (Integer)increment));
                                                Prestige.silentRotationManager.clearRotation();
                                                InventoryUtil.runWithBlock(coverBlock, () -> PlayerInteractions.interactBlock(crosshairHit), this.swap.getValue().equals("Silent"));
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
        this.lastPlacedAnchor = null;
        this.lastAnchorCharges = 0;
        coverPending = false;
    }

    @Override
    public Rotation provideRotation() {
        if (Prestige.silentRotationManager.hasActedThisTick()
            || !this.mode.getValue().equalsIgnoreCase("Blatant")
            || mc.currentScreen != null
            || mc.player.isUsingItem()
            || !this.delayTimer.hasElapsedRandom(this.delay)) {
            return null;
        }

        Block coverBlock = this.block.getValue().equals("Glowstone") ? Blocks.GLOWSTONE : Blocks.OBSIDIAN;
        if (this.swap.getValue().equals("None") && !InventoryUtil.isHolding(coverBlock.asItem())) {
            return null;
        }

        int radius = (int)Math.ceil(PlayerInteractions.getReachDistance());
        HitResult bestHit = null;
        Rotation bestRotation = null;
        Rotation pendingRotation = null;
        BlockPos bestAnchor = null;
        double bestDamage = Double.MAX_VALUE;

        for (BlockPos anchorPos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(radius, (Predicate<BlockPos>)pos -> BlockUtil.isOf(pos, Blocks.RESPAWN_ANCHOR)))) {
            if (this.onlyOwn.getValue() && !BlockChangeTracker.isOwnAnchor(anchorPos)) {
                continue;
            }
            if ((Integer)this.coverCounts.getOrDefault(anchorPos, 0) >= this.limit.getValue()) {
                continue;
            }
            if (PositionUtil.getPlayerPos().distanceTo(anchorPos.toCenterPos()) > PlayerInteractions.getReachDistance()) {
                continue;
            }
            if (Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos()) < this.minDamage.getValue().floatValue()) {
                continue;
            }

            DelayTimer reactionTimer = (DelayTimer)this.reactionTimers.computeIfAbsent(anchorPos, ignored -> new DelayTimer(this.delay.randomInRange()));
            if (!reactionTimer.hasElapsed()) {
                continue;
            }

            // Six faces instead of an 11x11 sweep: a cover one block from the anchor is the only useful one.
            for (Direction direction : Direction.values()) {
                BlockPos coverPos = anchorPos.offset(direction);
                if (!this.canCover(coverPos) || BlockUtil.hasEntityAt(coverPos)) {
                    continue;
                }
                BlockPos supportPos = coverPos.down();
                if (!BlockUtil.isClickableBlock(supportPos)) {
                    continue;
                }
                double coveredDamage = Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos(), coverPos, coverBlock.getDefaultState());
                if (coveredDamage > this.maxDamage.getValue().floatValue()) {
                    continue;
                }
                Vec3d aimPoint = RaycastUtil.getVisibleTopPoint(supportPos);
                if (aimPoint == null) {
                    continue;
                }
                // FOV has to be measured on the real target: the stepped rotation is always short of it.
                Rotation aim = RotationUtil.getLimitedRotationToPoint(
                    mc.player.getYaw(), mc.player.getPitch(), (float)aimPoint.x, (float)aimPoint.y, (float)aimPoint.z, 1000.0F
                );
                float yawDelta = MathHelper.wrapDegrees(aim.getYaw() - mc.player.getYaw());
                if (yawDelta > this.fov.getValue() || yawDelta < -this.fov.getValue()) {
                    continue;
                }
                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                // The manager turns in steps, so a target that is still far away comes back clamped. That rotation
                // has to be handed back so the aim can converge; returning null - what the old code did - means
                // "do not rotate at all", which is why the aim never arrived and no cover was ever placed.
                if (rotation.isClamped()) {
                    if (pendingRotation == null) {
                        pendingRotation = rotation;
                    }
                    continue;
                }
                HitResult rayHit = RotationUtil.raycastRotation(rotation);
                if (rayHit.getPos().distanceTo(aimPoint) > 0.5) {
                    continue;
                }
                if (coveredDamage < bestDamage) {
                    bestDamage = coveredDamage;
                    bestAnchor = anchorPos;
                    bestHit = rayHit;
                    bestRotation = rotation;
                }
            }
        }

        if (!(bestHit instanceof BlockHitResult blockHit)) {
            return pendingRotation;
        }
        if (BlockChangeTracker.isRecentlyExploded(blockHit.getBlockPos()) || BlockChangeTracker.isRecentlyPlaced(blockHit.getBlockPos())) {
            return bestRotation;
        }
        InventoryUtil.runWithBlock(coverBlock, () -> PlayerInteractions.interactBlock(blockHit), this.swap.getValue().equals("Silent"));
        this.coverCounts.merge(bestAnchor, 1, (java.util.function.BiFunction)(oldCount, increment) -> Integer.sum((Integer)oldCount, (Integer)increment));
        this.delayTimer.reset();
        this.delay.reroll();
        return bestRotation;
    }

}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
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
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AnchorExploder extends Module implements RotationProvider {
    private BoolSetting onlyOwnAnchors;
    private RangeSetting delay;
    private BoolSetting glowstone;
    private EnumSetting glowstoneSwap;
    private BoolSetting glowstoneSwapBack;
    private BoolSetting explode;
    private EnumSetting explodeSwap;
    private IntSetting explodeSlot;
    private BoolSetting explodeSwapBack;
    private BoolSetting explodeDamageTick;
    private BoolSetting disableAfterExplode;
    private MultiSetting silentAim;
    private FloatSetting silentAimFov;
    public static boolean lookingAtAnchor;
    private StopWatch actionTimer = new StopWatch();
    private ArrayList ownAnchors = new ArrayList();
    private int glowstoneSwapBackSlot = -1;
    private int explodeSwapBackSlot = -1;

    public AnchorExploder() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.glowstoneSwap.visibleWhen((Predicate<String>)ignored -> this.glowstone.getValue());
        this.explodeSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.explode.getValue() && this.explodeSwap.getValue().equals("Swap")
            );
        this.explodeDamageTick.visibleWhen((Predicate<Boolean>)ignored -> this.explode.getValue());
        this.explodeSlot.visibleWhen((Predicate<Integer>)ignored -> this.explode.getValue());
        this.silentAimFov
            .visibleWhen(
                (Predicate<Float>)ignored -> this.silentAim.isSelected("Glowstone")
                        || this.silentAim.isSelected("Explode")
            );
        this.glowstoneSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.glowstone.getValue() && this.glowstoneSwap.getValue().equals("Swap")
            );
        this.explodeSwap.visibleWhen((Predicate<String>)ignored -> this.explode.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public boolean isPersistent() {
        return Boolean.FALSE.equals(this.disableAfterExplode.getValue());
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.getKey() == 9998 && mc.player.getMainHandStack().getItem().equals(Blocks.GLOWSTONE.asItem())) {
            event.cancel();
        } else {
            if (AnchorUseGuard.shouldCancelItemUse()) {
                event.cancel();
            }
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.ownAnchors.clear();
        this.glowstoneSwapBackSlot = -1;
        this.explodeSwapBackSlot = -1;
        lookingAtAnchor = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.ownAnchors.clear();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                if (!AutoObbyCrystal.sequenceActive && !AnchorPearlCatch.active) {
                    if (!SafeAnchor.isCoverPending()) {
                        if (this.actionTimer.hasElapsedRandom(this.delay)) {
                            this.actionTimer.reset();
                            this.delay.reroll();
                            if (this.onlyOwnAnchors.getValue()) {
                                this.ownAnchors.removeIf(anchorPos -> mc.world.getBlockState(((net.minecraft.util.math.BlockPos)anchorPos)).getBlock() != Blocks.RESPAWN_ANCHOR);
                            }

                            if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
                                if (BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
                                    lookingAtAnchor = true;
                                    if (BlockChangeTracker.isRecentlyExploded(crosshairHit.getBlockPos())
                                        || BlockChangeTracker.isRecentlyPlaced(crosshairHit.getBlockPos())) {
                                        return;
                                    }

                                    if (this.glowstone.getValue() && this.tryChargeAnchor()) {
                                        return;
                                    }

                                    if (this.explode.getValue()) {
                                        this.tryExplodeAnchor();
                                    }
                                } else {
                                    lookingAtAnchor = false;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.silentAim.isSelected("Glowstone") && !this.silentAim.isSelected("Explode")) {
            return null;
        } else if (mc.player.isUsingItem()) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else {
            boolean silentCharge = this.silentAim.isSelected("Glowstone") && this.glowstone.getValue();
            boolean silentExplode = this.silentAim.isSelected("Explode") && this.explode.getValue();
            if (!silentCharge && !silentExplode) {
                return null;
            } else if (SafeAnchor.isCoverPending()) {
                return null;
            } else if (AnchorPearlCatch.active) {
                return null;
            } else if (mc.crosshairTarget instanceof BlockHitResult crosshairHit && BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
                return null;
            } else {
                Rotation bestRotation = null;
                boolean bestNeedsCharge = false;
                double bestDistance = Double.MAX_VALUE;

                for (BlockPos anchorPos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(5, (Predicate<BlockPos>)pos -> BlockUtil.isOf(pos, Blocks.RESPAWN_ANCHOR)))) {
                    if (!(PositionUtil.getPlayerPos().distanceTo(anchorPos.toCenterPos()) > PlayerInteractions.getReachDistance())
                        && (!this.onlyOwnAnchors.getValue() || this.ownAnchors.contains(anchorPos))) {
                        int charges = BlockUtil.getAnchorCharges(anchorPos);
                        boolean needsCharge = silentCharge && charges == 0;
                        boolean canExplode = silentExplode && charges > 0;
                        if (needsCharge || canExplode) {
                            Vec3d aimPoint = RaycastUtil.getVisibleTopPoint(anchorPos);
                            if (aimPoint == null) {
                                aimPoint = anchorPos.toCenterPos();
                            }

                            Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                            rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                            float yawDelta = rotation.getYaw() - mc.player.getYaw();
                            if (!(yawDelta > this.silentAimFov.getValue())
                                && !(yawDelta < -this.silentAimFov.getValue())
                                && RotationUtil.raycastRotation(rotation) instanceof BlockHitResult rayHit
                                && rayHit.getBlockPos().equals(anchorPos)) {
                                double distance = PositionUtil.getPlayerPos().distanceTo(anchorPos.toCenterPos());
                                if (distance < bestDistance) {
                                    bestDistance = distance;
                                    bestRotation = rotation;
                                    bestNeedsCharge = needsCharge;
                                }
                            }
                        }
                    }
                }

                if (bestRotation == null) {
                    return null;
                } else if (bestRotation.isClamped()) {
                    return bestRotation;
                } else if (RotationUtil.raycastRotation(bestRotation) instanceof BlockHitResult bestHit) {
                    if (!BlockChangeTracker.isRecentlyExploded(bestHit.getBlockPos())
                        && !BlockChangeTracker.isRecentlyPlaced(bestHit.getBlockPos())) {
                        if (bestNeedsCharge) {
                            switch (this.glowstoneSwap.getValue()) {
                                case "None":
                                    if (!InventoryUtil.isHolding(Items.GLOWSTONE)) {
                                        return null;
                                    }
                                    break;
                                case "Swap":
                                    if (this.glowstoneSwapBack.getValue()) {
                                        this.glowstoneSwapBackSlot = InventoryUtil.getSelectedSlot();
                                    }

                                    if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                                        return null;
                                    }
                            }

                            PlayerInteractions.interactBlock(bestHit);
                        } else {
                            switch (this.explodeSwap.getValue()) {
                                case "None":
                                    if (this.explodeSlot.getValue() - 1 != InventoryUtil.getSelectedSlot()) {
                                        return null;
                                    }
                                    break;
                                case "Swap":
                                    if (this.explodeSwapBack.getValue()) {
                                        this.explodeSwapBackSlot = InventoryUtil.getSelectedSlot();
                                    }

                                    InventoryUtil.selectSlot(this.explodeSlot.getValue() - 1);
                            }

                            if (this.explodeDamageTick.getValue()) {
                                PlayerEntity enemy = TargetSelector.getClosestEnemy();
                                if (enemy != null && mc.player.distanceTo(enemy) < 10.0F && enemy.hurtTime != 0) {
                                    return null;
                                }
                            }

                            PlayerInteractions.interactBlock(bestHit);
                            BlockChangeTracker.markExplosion(bestHit.getBlockPos());
                        }

                        return bestRotation;
                    } else {
                        return bestRotation;
                    }
                } else {
                    return null;
                }
            }
        }
    }

    private boolean tryChargeAnchor() {
        if (this.glowstoneSwapBackSlot != -1) {
            InventoryUtil.selectSlot(this.glowstoneSwapBackSlot);
            this.glowstoneSwapBackSlot = -1;
            return false;
        } else if (!(mc.crosshairTarget instanceof BlockHitResult crosshairHit)) {
            return false;
        } else if (!mc.player.isSneaking()
            && BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
            && (!this.onlyOwnAnchors.getValue() || this.ownAnchors.contains(crosshairHit.getBlockPos()))
            && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) == 0) {
            switch (this.glowstoneSwap.getValue()) {
                case "None":
                    if (!InventoryUtil.isHolding(Items.GLOWSTONE)) {
                        return false;
                    }
                    break;
                case "Swap":
                    if (this.glowstoneSwapBack.getValue()) {
                        this.glowstoneSwapBackSlot = InventoryUtil.getSelectedSlot();
                    }

                    if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                        return false;
                    }
            }

            Prestige.silentRotationManager.clearRotation();
            PlayerInteractions.interactBlock(crosshairHit);
            return true;
        } else {
            return false;
        }
    }

    private void tryExplodeAnchor() {
        if (this.explodeDamageTick.getValue()) {
            PlayerEntity enemy = TargetSelector.getClosestEnemy();
            if (enemy != null && mc.player.distanceTo(enemy) < 10.0F && enemy.hurtTime != 0) {
                return;
            }
        }

        if (this.explodeSwapBackSlot != -1) {
            InventoryUtil.selectSlot(this.explodeSwapBackSlot);
            this.explodeSwapBackSlot = -1;
        } else if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
            if (!mc.player.isSneaking()
                && BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                && (!this.onlyOwnAnchors.getValue() || this.ownAnchors.contains(crosshairHit.getBlockPos()))
                && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) != 0) {
                switch (this.explodeSwap.getValue()) {
                    case "None":
                        if (this.explodeSlot.getValue() - 1 != InventoryUtil.getSelectedSlot()) {
                            return;
                        }
                        break;
                    case "Swap":
                        if (this.explodeSwapBack.getValue()) {
                            this.explodeSwapBackSlot = InventoryUtil.getSelectedSlot();
                        }

                        InventoryUtil.selectSlot(this.explodeSlot.getValue() - 1);
                }

                Prestige.silentRotationManager.clearRotation();
                PlayerInteractions.interactBlock(crosshairHit);
                BlockChangeTracker.markExplosion(crosshairHit.getBlockPos());
                if (this.disableAfterExplode.getValue()) {
                    this.toggle();
                }
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            if ((interactPacket.getHand().equals(Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem() == Items.RESPAWN_ANCHOR) {
                BlockHitResult hitResult = interactPacket.getBlockHitResult();
                BlockPos placePos = hitResult.getBlockPos().add(hitResult.getSide().getOffsetX(), hitResult.getSide().getOffsetY(), hitResult.getSide().getOffsetZ());
                if (this.onlyOwnAnchors.getValue() && !this.ownAnchors.contains(placePos)) {
                    this.ownAnchors
                        .add(
                            !BlockUtil.isOf(hitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                                    && !BlockUtil.isOf(hitResult.getBlockPos(), Blocks.FIRE)
                                    && !BlockUtil.isOf(
                                        hitResult.getBlockPos(), BlockUtil.getShortGrass()
                                    )
                                ? placePos
                                : hitResult.getBlockPos()
                        );
                }
            }
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState() != null && blockUpdate.getState().getBlock() != Blocks.RESPAWN_ANCHOR) {
                mc.execute(() -> this.ownAnchors.remove(blockUpdate.getPos()));
            }
        }
    }
}

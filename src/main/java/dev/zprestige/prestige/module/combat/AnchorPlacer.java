package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
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
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AnchorPlacer extends Module implements RotationProvider {
    private EnumSetting fireMode;
    private RangeSetting delay;
    private EnumSetting swap;
    private BoolSetting swapBack;
    private BoolSetting silentAim;
    private FloatSetting silentAimFov;
    public ArrayList placedAnchors = new ArrayList();
    private StopWatch actionTimer = new StopWatch();
    private int swapBackSlot = -1;
    public static boolean acting;
    private int actingUntilAge = 0;

    public AnchorPlacer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.silentAimFov.visibleWhen((Predicate<Float>)ignored -> this.silentAim.getValue());
        this.swapBack.visibleWhen((Predicate<Boolean>)ignored -> this.swap.getValue().equals("Swap"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public boolean isPersistent() {
        return "Persistent".equals(this.fireMode.getValue());
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.placedAnchors.clear();
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate && blockUpdate.getState() != null && blockUpdate.getState().getBlock() != Blocks.RESPAWN_ANCHOR) {
            mc.execute(() -> this.placedAnchors.remove(blockUpdate.getPos()));
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (AnchorUseGuard.shouldCancelItemUse()) {
            event.cancel();
        }
    }

    @Override
    public void onEnable() {
        this.swapBackSlot = -1;
        acting = false;
        this.actingUntilAge = 0;
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.placedAnchors.clear();
        this.swapBackSlot = -1;
        acting = false;
        this.actingUntilAge = 0;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private void disableIfOneShot() {
        if (this.fireMode.getValue().equals("One-Shot")) {
            this.toggle();
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        acting = mc.player != null && mc.player.age <= this.actingUntilAge;
        if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                if (!AutoObbyCrystal.sequenceActive && !AnchorPearlCatch.active) {
                    if (!SafeAnchor.isCoverPending()) {
                        if (this.actionTimer.hasElapsedRandom(this.delay)) {
                            if (this.swapBackSlot != -1) {
                                this.actingUntilAge = mc.player.age + 2;
                                acting = true;
                                InventoryUtil.selectSlot(this.swapBackSlot);
                                this.swapBackSlot = -1;
                                this.disableIfOneShot();
                            } else if (!this.silentAim.getValue()
                                || mc.crosshairTarget instanceof BlockHitResult targetedHit
                                    && (
                                        BlockUtil.isClickableBlockHit(targetedHit)
                                                && !BlockUtil.hasEntityAt(
                                                    BlockUtil.getPlacePos(targetedHit)
                                                )
                                            || BlockUtil.isOf(targetedHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                                    )) {
                                if (!(mc.crosshairTarget instanceof BlockHitResult crosshairHit)) {
                                    this.disableIfOneShot();
                                } else if (!BlockChangeTracker.isRecentlyExploded(crosshairHit.getBlockPos())
                                    && !BlockChangeTracker.isRecentlyPlaced(crosshairHit.getBlockPos())) {
                                    boolean emptyAnchor = BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                                        && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) == 0;
                                    if (BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                                        && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) > 0) {
                                        this.actingUntilAge = mc.player.age + 2;
                                        acting = true;
                                        this.explodeAnchor(crosshairHit);
                                    } else if (!BlockUtil.isClickableBlockHit(crosshairHit) && !emptyAnchor) {
                                        this.disableIfOneShot();
                                    } else {
                                        this.actingUntilAge = mc.player.age + 2;
                                        acting = true;
                                        this.placeOrChargeAnchor(crosshairHit, emptyAnchor);
                                    }
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
        if (!this.silentAim.getValue()) {
            return null;
        } else if (mc.player.isUsingItem()) {
            return null;
        } else if (!this.actionTimer.hasElapsedRandom(this.delay)) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (SafeAnchor.isCoverPending()) {
            return null;
        } else if (AnchorPearlCatch.active) {
            return null;
        } else if (!(
            mc.crosshairTarget instanceof BlockHitResult crosshairHit
                && (
                    BlockUtil.isClickableBlockHit(crosshairHit) && !BlockUtil.hasEntityAt(BlockUtil.getPlacePos(crosshairHit))
                        || BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                )
        )) {
            Rotation bestRotation = null;
            double bestAngleDelta = Double.MAX_VALUE;

            for (BlockPos candidatePos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(5, (Predicate<BlockPos>)pos -> BlockUtil.isClickableBlock(pos)))) {
                if (!BlockUtil.hasEntityAt(candidatePos.up())) {
                    Vec3d aimPoint = RaycastUtil.getVisibleTopPoint(candidatePos);
                    if (aimPoint != null && !(mc.player.getEyePos().distanceTo(aimPoint) > 4.4)) {
                        Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                        rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                        float yawDelta = rotation.getYaw() - mc.player.getYaw();
                        float pitchDelta = rotation.getPitch() - mc.player.getPitch();
                        if (!(Math.abs(yawDelta) > this.silentAimFov.getValue())
                            && RotationUtil.raycastRotation(rotation) instanceof BlockHitResult rayHit
                            && (rayHit.getBlockPos().equals(candidatePos) || rayHit.getBlockPos().up().equals(candidatePos))) {
                            double angleDelta = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
                            if (angleDelta < bestAngleDelta) {
                                bestAngleDelta = angleDelta;
                                bestRotation = rotation;
                            }
                        }
                    }
                }
            }

            if (bestRotation == null) {
                return null;
            } else if (bestRotation.isClamped()) {
                return bestRotation;
            } else if (!(RotationUtil.raycastRotation(bestRotation) instanceof BlockHitResult bestHit)) {
                return bestRotation;
            } else if (!BlockChangeTracker.isRecentlyExploded(bestHit.getBlockPos())
                && !BlockChangeTracker.isRecentlyPlaced(bestHit.getBlockPos())) {
                this.actOnBlockHit(bestHit);
                this.actionTimer.reset();
                this.delay.reroll();
                return bestRotation;
            } else {
                return bestRotation;
            }
        } else {
            return null;
        }
    }

    private void actOnBlockHit(BlockHitResult hitResult) {
        boolean emptyAnchor = BlockUtil.isOf(hitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)
            && BlockUtil.getAnchorCharges(hitResult.getBlockPos()) == 0;
        if (BlockUtil.isOf(hitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)
            && BlockUtil.getAnchorCharges(hitResult.getBlockPos()) > 0) {
            this.explodeAnchor(hitResult);
        } else if (BlockUtil.isClickableBlockHit(hitResult) || emptyAnchor) {
            this.placeOrChargeAnchor(hitResult, emptyAnchor);
        }
    }

    private void explodeAnchor(BlockHitResult hitResult) {
        Prestige.silentRotationManager.clearRotation();
        switch (this.swap.getValue()) {
            case "Swap":
                Integer totemSlot = InventoryUtil.findHotbarSlot(Items.TOTEM_OF_UNDYING);
                if (totemSlot != null) {
                    if (this.swapBack.getValue()) {
                        this.swapBackSlot = InventoryUtil.getSelectedSlot();
                    }

                    InventoryUtil.selectSlot(totemSlot);
                } else if (InventoryUtil.isHolding(Items.GLOWSTONE)) {
                    Integer nonGlowstoneSlot = InventoryUtil.findSlotWithOtherItem(Items.GLOWSTONE);
                    if (nonGlowstoneSlot == null) {
                        return;
                    }

                    if (this.swapBack.getValue()) {
                        this.swapBackSlot = InventoryUtil.getSelectedSlot();
                    }

                    InventoryUtil.selectSlot(nonGlowstoneSlot);
                }

                PlayerInteractions.interactBlock(hitResult);
                if (this.swapBackSlot == -1) {
                    this.disableIfOneShot();
                }
                break;
            case "None":
                if (!InventoryUtil.isHolding(Items.GLOWSTONE)) {
                    PlayerInteractions.interactBlock(hitResult);
                    this.disableIfOneShot();
                }
        }
    }

    private void placeOrChargeAnchor(BlockHitResult hitResult, boolean emptyAnchor) {
        switch (this.swap.getValue()) {
            case "None":
                if (emptyAnchor) {
                    if (!InventoryUtil.isHolding(Items.GLOWSTONE)) {
                        return;
                    }
                } else if (!InventoryUtil.isHolding(Items.RESPAWN_ANCHOR)) {
                    return;
                }
                break;
            case "Swap":
                if (this.swapBack.getValue()) {
                    this.swapBackSlot = InventoryUtil.getSelectedSlot();
                }

                if (emptyAnchor) {
                    if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                        this.disableIfOneShot();
                        return;
                    }
                } else if (!InventoryUtil.switchToBlock(Blocks.RESPAWN_ANCHOR)) {
                    this.disableIfOneShot();
                    return;
                }
        }

        BlockPos placePos = !BlockUtil.isOf(hitResult.getBlockPos(), Blocks.FIRE)
                && !BlockUtil.isOf(hitResult.getBlockPos(), BlockUtil.getShortGrass())
            ? hitResult.getBlockPos().add(hitResult.getSide().getOffsetX(), hitResult.getSide().getOffsetY(), hitResult.getSide().getOffsetZ())
            : hitResult.getBlockPos();
        if (!emptyAnchor) {
            this.placedAnchors.add(placePos);
        }

        Prestige.silentRotationManager.clearRotation();
        PlayerInteractions.interactBlock(hitResult);
        this.actionTimer.reset();
        this.delay.reroll();
        if (this.swapBackSlot == -1) {
            this.disableIfOneShot();
        }
    }
}

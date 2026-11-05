package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AnchorMacro extends Module {
    private BoolSetting onlyOwnAnchors;
    private RangeSetting delay;
    private BoolSetting place;
    private KeybindSetting placeBind;
    private EnumSetting placeSwap;
    private BoolSetting placeSwapBack;
    private BoolSetting glowstone;
    private EnumSetting glowstoneSwap;
    private BoolSetting glowstoneSwapBack;
    private BoolSetting onlyOnPlaceBind;
    private BoolSetting explode;
    private EnumSetting explodeSwap;
    private IntSetting explodeSlot;
    private BoolSetting explodeSwapBack;
    public static boolean acting;
    private StopWatch actionTimer = new StopWatch();
    private boolean placedThisPress;
    private ArrayList ownAnchors = new ArrayList();
    private ArrayList bindPlacedAnchors = new ArrayList();
    private int placeSwapBackSlot = -1;
    private int glowstoneSwapBackSlot = -1;
    private int explodeSwapBackSlot = -1;

    public AnchorMacro() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.explodeSlot.visibleWhen((Predicate<Integer>)ignored -> this.explode.getValue());
        this.explodeSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.explode.getValue() && this.explodeSwap.getValue().equals("Swap")
            );
        this.glowstoneSwap.visibleWhen((Predicate<String>)ignored -> this.glowstone.getValue());
        this.onlyOnPlaceBind.visibleWhen((Predicate<Boolean>)ignored -> this.glowstone.getValue());
        this.glowstoneSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.glowstone.getValue() && this.glowstoneSwap.getValue().equals("Swap")
            );
        this.explodeSwap.visibleWhen((Predicate<String>)ignored -> this.explode.getValue());
        this.placeBind.visibleWhen((Predicate<Integer>)ignored -> this.place.getValue());
        this.placeSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.place.getValue() && this.placeSwap.getValue().equals("Swap")
            );
        this.placeSwap.visibleWhen((Predicate<String>)ignored -> this.place.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.ownAnchors.clear();
        this.bindPlacedAnchors.clear();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
            if (!AutoObbyCrystal.sequenceActive && !AnchorPearlCatch.active && !Prestige.silentRotationManager.hasActedThisTick()) {
                if (this.actionTimer.hasElapsedRandom(this.delay)) {
                    if (this.onlyOwnAnchors.getValue()) {
                        this.ownAnchors.removeIf(anchorPos -> mc.world.getBlockState(((net.minecraft.util.math.BlockPos)anchorPos)).getBlock() != Blocks.RESPAWN_ANCHOR);
                    }

                    this.actionTimer.reset();
                    this.delay.reroll();
                    acting = true;
                    if (!this.place.getValue() || !this.tryPlaceAnchor()) {
                        if (!this.glowstone.getValue() || !this.tryChargeAnchor()) {
                            if (this.explode.getValue()) {
                                this.tryExplodeAnchor();
                            }

                            acting = false;
                        }
                    }
                }
            }
        }
    }

    private boolean tryPlaceAnchor() {
        if (this.placeBind.getValue() == -1) {
            return false;
        } else if (this.placeSwapBackSlot != -1) {
            InventoryUtil.selectSlot(this.placeSwapBackSlot);
            this.placeSwapBackSlot = -1;
            return false;
        } else if (!this.placeBind.isPressed()) {
            this.placedThisPress = false;
            return false;
        } else {
            if (!this.placedThisPress) {
                ModuleNotificationsElement.postNotification(this, "Is placing an anchor.", NotificationType.INFO);
            }

            if (!this.placedThisPress && mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
                boolean emptyAnchor = BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                    && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) == 0;
                if (!BlockUtil.isClickableBlockHit(crosshairHit) && !emptyAnchor) {
                    return false;
                } else {
                    int silentRestoreSlot = -1;
                    switch (this.placeSwap.getValue()) {
                        case "None":
                            if (emptyAnchor && !InventoryUtil.isHolding(Items.GLOWSTONE)) {
                                return false;
                            }

                            if (!InventoryUtil.isHolding(Items.RESPAWN_ANCHOR)) {
                                return false;
                            }
                            break;
                        case "Swap":
                            if (this.placeSwapBack.getValue()) {
                                this.placeSwapBackSlot = InventoryUtil.getSelectedSlot();
                            }

                            if (emptyAnchor) {
                                if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                                    return false;
                                }
                            } else if (!InventoryUtil.switchToBlock(Blocks.RESPAWN_ANCHOR)) {
                                return false;
                            }
                            break;
                        case "Silent":
                            silentRestoreSlot = InventoryUtil.getSelectedSlot();
                            if (emptyAnchor) {
                                if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                                    return false;
                                }
                            } else if (!InventoryUtil.switchToBlock(Blocks.RESPAWN_ANCHOR)) {
                                return false;
                            }
                    }

                    if (!emptyAnchor) {
                        this.bindPlacedAnchors
                            .add(
                                !BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.FIRE)
                                        && !BlockUtil.isOf(crosshairHit.getBlockPos(), BlockUtil.getShortGrass())
                                    ? crosshairHit.getBlockPos().add(crosshairHit.getSide().getOffsetX(), crosshairHit.getSide().getOffsetY(), crosshairHit.getSide().getOffsetZ())
                                    : crosshairHit.getBlockPos()
                            );
                    }

                    PlayerInteractions.interactBlock(crosshairHit);
                    if (silentRestoreSlot != -1) {
                        InventoryUtil.selectSlot(silentRestoreSlot);
                    }

                    this.placedThisPress = true;
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public boolean tryChargeAnchor() {
        if (this.glowstoneSwapBackSlot != -1) {
            InventoryUtil.selectSlot(this.glowstoneSwapBackSlot);
            this.glowstoneSwapBackSlot = -1;
            return false;
        } else if (!(mc.crosshairTarget instanceof BlockHitResult crosshairHit)) {
            return false;
        } else if (mc.player.isSneaking()
            || !BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
            || this.onlyOwnAnchors.getValue() && !this.ownAnchors.contains(crosshairHit.getBlockPos())
            || BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) != 0) {
            return false;
        } else if (this.onlyOnPlaceBind.getValue() && !this.bindPlacedAnchors.contains(crosshairHit.getBlockPos())) {
            return false;
        } else {
            int silentRestoreSlot = -1;
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
                    break;
                case "Silent":
                    silentRestoreSlot = InventoryUtil.getSelectedSlot();
                    if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
                        return false;
                    }
            }

            PlayerInteractions.interactBlock(crosshairHit);
            if (silentRestoreSlot != -1) {
                InventoryUtil.selectSlot(silentRestoreSlot);
            }

            return true;
        }
    }

    private void tryExplodeAnchor() {
        if (this.explodeSwapBackSlot != -1) {
            InventoryUtil.selectSlot(this.explodeSwapBackSlot);
            this.explodeSwapBackSlot = -1;
        } else if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
            if (!mc.player.isSneaking()
                && BlockUtil.isBlock(crosshairHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                && (!this.onlyOwnAnchors.getValue() || this.ownAnchors.contains(crosshairHit.getBlockPos()))
                && BlockUtil.getAnchorCharges(crosshairHit.getBlockPos()) != 0) {
                int silentRestoreSlot = -1;
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
                        break;
                    case "Silent":
                        silentRestoreSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(this.explodeSlot.getValue() - 1);
                }

                PlayerInteractions.interactBlock(crosshairHit);
                if (silentRestoreSlot != -1) {
                    InventoryUtil.selectSlot(silentRestoreSlot);
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
                if (!this.onlyOwnAnchors.getValue() || this.ownAnchors.contains(placePos)) {
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
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState() == null || blockUpdate.getState().getBlock() == Blocks.RESPAWN_ANCHOR) {
                return;
            }

            mc.execute(() -> {
                this.bindPlacedAnchors.remove(blockUpdate.getPos());
                this.ownAnchors.remove(blockUpdate.getPos());
            });
        }
    }
}

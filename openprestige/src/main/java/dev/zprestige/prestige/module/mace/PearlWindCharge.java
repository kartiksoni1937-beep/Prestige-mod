package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.JumpKeyPresser;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;

public class PearlWindCharge extends Module implements RotationProvider {
    public PearlWindCharge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting autoSwap;
    private BoolSetting jump;
    private boolean pearlThrown;
    private boolean pearlLanded;
    private static final long LAST_HOTBAR_SLOT = 5771087529172271112L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.pearlThrown = false;
        this.pearlLanded = false;
    }

    @Override
    public Rotation provideRotation() {
        if (!this.pearlThrown) {
            if (this.autoSwap.getValue() && !InventoryUtil.isHolding(Items.ENDER_PEARL)) {
                int pearlSlot = InventoryUtil.findHotbarSlot(Items.ENDER_PEARL);
                if (pearlSlot == -1) {
                    this.toggle();
                    return null;
                } else {
                    InventoryUtil.selectSlot(pearlSlot);
                    return null;
                }
            } else {
                if (InventoryUtil.isHolding(Items.ENDER_PEARL)) {
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    this.pearlThrown = true;
                }

                return null;
            }
        } else {
            if (this.pearlLanded) {
                int windChargeSlot = this.findWindChargeSlot();
                if (windChargeSlot != -1) {
                    float previousPitch = mc.player.getPitch();
                    mc.player.setPitch(90.0F);
                    InventoryUtil.selectSlot(windChargeSlot);
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    if (this.jump.getValue() && mc.player.isOnGround()) {
                        JumpKeyPresser.pressJump();
                    }

                    mc.player.setPitch(previousPitch);
                    this.toggle();
                    return new Rotation(mc.player.getYaw(), 90.0F);
                }

                this.toggle();
            }

            return null;
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.pearlLanded = true;
        }

        if (event.getPacket() instanceof BundleS2CPacket bundle) {
            for (Packet bundledPacket : bundle.getPackets()) {
                if (bundledPacket instanceof PlayerPositionLookS2CPacket) {
                    this.pearlLanded = true;
                    break;
                }
            }
        }
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private int findWindChargeSlot() {
        for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
            if (isWindCharge(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    public static boolean isWindCharge(ItemStack stack) {
        return stack.getItem().equals(Items.WIND_CHARGE);
    }
}

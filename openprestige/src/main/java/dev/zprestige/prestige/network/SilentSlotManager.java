package dev.zprestige.prestige.network;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class SilentSlotManager implements MinecraftAccess {
    private int spoofedSlot;
    private boolean sendingOwnPacket;

    public SilentSlotManager() {
        super();
        this.spoofedSlot = -1;
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.spoofedSlot != -1) {
            this.restoreSlot();
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (this.spoofedSlot != -1) {
            if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket && !this.sendingOwnPacket) {
                event.cancel();
                return;
            }

            if (event.getPacket() instanceof PlayerInteractItemC2SPacket
                || event.getPacket() instanceof PlayerInteractBlockC2SPacket
                || event.getPacket() instanceof PlayerInteractEntityC2SPacket
                    && mc.player != null && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                this.restoreSlot();
            }
        }
    }

    public void spoofSlot(int slot) {
        this.spoofedSlot = slot;
        this.sendingOwnPacket = true;
        try {
            PacketSender.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } finally {
            this.sendingOwnPacket = false;
        }
    }

    public void restoreSlot() {
        if (this.spoofedSlot != -1) {
            this.sendingOwnPacket = true;
            try {
                PacketSender.sendPacket(new UpdateSelectedSlotC2SPacket(InventoryUtil.getSelectedSlot()));
            } finally {
                this.sendingOwnPacket = false;
                this.spoofedSlot = -1;
            }
        }
    }

    public int getSpoofedSlot() {
        return this.spoofedSlot;
    }
}

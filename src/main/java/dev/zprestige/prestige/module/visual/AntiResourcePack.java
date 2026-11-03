package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.network.PacketSender;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket.Status;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

public class AntiResourcePack extends Module {
    public AntiResourcePack() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof ResourcePackSendS2CPacket packet) {
            event.cancel();
            PacketSender.sendPacket(new ResourcePackStatusC2SPacket(packet.id(), Status.ACCEPTED));
            PacketSender.sendPacket(new ResourcePackStatusC2SPacket(packet.id(), Status.SUCCESSFULLY_LOADED));
        }
    }
}

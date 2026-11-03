package dev.zprestige.prestige.network;

import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.network.packet.Packet;

public class PacketSender implements MinecraftAccess {
    public static void sendPacket(Packet packet) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }
}

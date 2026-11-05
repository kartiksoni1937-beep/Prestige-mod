package dev.zprestige.prestige.event;

import net.minecraft.network.packet.Packet;

public class PacketReceiveEvent extends Event {
    private final Packet packet;

    public PacketReceiveEvent(Packet receivedPacket) {
        this.packet = receivedPacket;
    }

    public Packet getPacket() {
        return this.packet;
    }
}

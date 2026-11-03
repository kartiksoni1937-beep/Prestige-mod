package dev.zprestige.prestige.event;

import net.minecraft.network.packet.Packet;

public class PacketSendEvent extends Event {
    private final Packet packet;

    public PacketSendEvent(Packet sentPacket) {
        this.packet = sentPacket;
    }

    public Packet getPacket() {
        return this.packet;
    }
}

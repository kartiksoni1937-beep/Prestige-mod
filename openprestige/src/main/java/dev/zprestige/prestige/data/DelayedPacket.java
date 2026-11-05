package dev.zprestige.prestige.data;

import net.minecraft.network.packet.Packet;

public record DelayedPacket(Packet<?> packet, long releaseTime) {
}

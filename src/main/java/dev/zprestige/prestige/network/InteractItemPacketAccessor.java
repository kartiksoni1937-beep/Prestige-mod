package dev.zprestige.prestige.network;

import dev.zprestige.prestige.reflect.FieldAccessor;
import dev.zprestige.prestige.reflect.MappedField;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

public class InteractItemPacketAccessor {
    private static final MappedField YAW_FIELD = FieldAccessor.mappedField(PlayerInteractItemC2SPacket.class, "yaw", float.class);
    private static final MappedField PITCH_FIELD = FieldAccessor.mappedField(PlayerInteractItemC2SPacket.class, "pitch", float.class);
    private final PlayerInteractItemC2SPacket packet;

    public InteractItemPacketAccessor(PlayerInteractItemC2SPacket interactPacket) {
        this.packet = interactPacket;
    }

    public void setYaw(float yaw) {
        YAW_FIELD.setFloat(this.packet, yaw);
    }

    public void setPitch(float pitch) {
        PITCH_FIELD.setFloat(this.packet, pitch);
    }
}

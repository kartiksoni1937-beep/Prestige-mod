package dev.zprestige.prestige.event;

import dev.zprestige.prestige.type.EventPhase;

public class TravelEvent extends Event {
    public float yaw;
    public float pitch;
    public static float savedYaw;
    public static float savedPitch;

    public TravelEvent(EventPhase eventPhase, float initialYaw, float initialPitch) {
        super(eventPhase);
        this.yaw = initialYaw;
        this.pitch = initialPitch;
    }
}

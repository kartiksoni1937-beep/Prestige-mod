package dev.zprestige.prestige.event;

import dev.zprestige.prestige.type.EventPhase;

public class SendMovementPacketsEvent extends Event {
    private float yaw;
    private float pitch;
    public static float savedYaw;
    public static float savedPitch;

    public SendMovementPacketsEvent(EventPhase eventPhase, float initialYaw, float initialPitch) {
        super(eventPhase);
        this.yaw = initialYaw;
        this.pitch = initialPitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float value) {
        this.yaw = value;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float value) {
        this.pitch = value;
    }
}

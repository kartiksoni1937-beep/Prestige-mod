package dev.zprestige.prestige.event;

public class CameraRotationEvent extends Event {
    public static boolean applyingOverride = false;
    private float yaw;
    private float pitch;

    public CameraRotationEvent(float initialYaw, float initialPitch) {
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

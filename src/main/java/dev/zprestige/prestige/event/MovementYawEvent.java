package dev.zprestige.prestige.event;

import net.minecraft.util.math.Vec3d;

public class MovementYawEvent extends Event {
    private float yaw;
    private Vec3d movementInput;

    public MovementYawEvent(float initialYaw) {
        this.yaw = initialYaw;
    }

    public MovementYawEvent(float initialYaw, Vec3d input) {
        this.yaw = initialYaw;
        this.movementInput = input;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float value) {
        this.yaw = value;
    }

    public Vec3d getMovementInput() {
        return this.movementInput;
    }

    public void setMovementInput(Vec3d newInput) {
        this.movementInput = newInput;
    }
}

package dev.zprestige.prestige.event;

public final class VelocityUpdateEvent extends Event {
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public VelocityUpdateEvent(double x, double y, double z) {
        this.velocityX = x;
        this.velocityY = y;
        this.velocityZ = z;
    }

    public double getVelocityX() {
        return this.velocityX;
    }

    public void setVelocityX(double value) {
        this.velocityX = value;
    }

    public double getVelocityY() {
        return this.velocityY;
    }

    public void setVelocityY(double value) {
        this.velocityY = value;
    }

    public double getVelocityZ() {
        return this.velocityZ;
    }

    public void setVelocityZ(double value) {
        this.velocityZ = value;
    }
}

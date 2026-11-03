package dev.zprestige.prestige.event;

public class CameraPositionEvent extends Event {
    public static boolean applyingOverride = false;
    private double x;
    private double y;
    private double z;

    public CameraPositionEvent(double posX, double posY, double posZ) {
        this.x = posX;
        this.y = posY;
        this.z = posZ;
    }

    public final double getX() {
        return this.x;
    }

    public final void setX(double value) {
        this.x = value;
    }

    public final double getY() {
        return this.y;
    }

    public final void setY(double value) {
        this.y = value;
    }

    public final double getZ() {
        return this.z;
    }

    public final void setZ(double value) {
        this.z = value;
    }
}

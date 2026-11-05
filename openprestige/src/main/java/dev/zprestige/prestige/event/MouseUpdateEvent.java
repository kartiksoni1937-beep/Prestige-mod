package dev.zprestige.prestige.event;

public final class MouseUpdateEvent extends Event {
    private final double deltaX;
    private final double deltaY;

    public MouseUpdateEvent(double cursorDeltaX, double cursorDeltaY) {
        this.deltaX = cursorDeltaX;
        this.deltaY = cursorDeltaY;
    }

    public double getDeltaX() {
        return this.deltaX;
    }

    public double getDeltaY() {
        return this.deltaY;
    }
}

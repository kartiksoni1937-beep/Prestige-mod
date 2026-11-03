package dev.zprestige.prestige.render;

public final class DelayTimer {
    private long startTime = System.currentTimeMillis();
    private float delayMs;

    public DelayTimer(float delay) {
        this.delayMs = delay;
    }

    public boolean hasElapsed() {
        return (float)(System.currentTimeMillis() - this.startTime) >= this.delayMs;
    }
}

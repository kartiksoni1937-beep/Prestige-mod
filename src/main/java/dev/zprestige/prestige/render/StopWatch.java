package dev.zprestige.prestige.render;

import dev.zprestige.prestige.RangeSetting;

public class StopWatch {
    private long startTime = System.currentTimeMillis();

    public StopWatch() {
    }

    public boolean hasElapsed(float millis) {
        return (float)(System.currentTimeMillis() - this.startTime) > millis;
    }

    public float getElapsed() {
        return (float)(System.currentTimeMillis() - this.startTime);
    }

    public boolean hasElapsedRandom(RangeSetting delayRange) {
        return this.hasElapsed(delayRange.getRolled());
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
    }
}

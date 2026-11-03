package dev.zprestige.prestige.util;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FrameEvent;

public class FrameDeltaTimer {
    private long lastFrameTime;
    private long frameTimeMs;

    public FrameDeltaTimer() {
        super();
        this.lastFrameTime = 0L;
        this.frameTimeMs = 0L;
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        this.frameTimeMs = System.currentTimeMillis() - this.lastFrameTime;
        this.lastFrameTime = System.currentTimeMillis();
        if ((float)this.frameTimeMs > 30.0F) {
            this.frameTimeMs = 0L;
        }
    }

    public float getAnimationDelta() {
        return (float)this.frameTimeMs * 0.005F;
    }
}

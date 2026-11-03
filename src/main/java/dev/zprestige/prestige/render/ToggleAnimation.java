package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.Easing;
import java.util.function.Supplier;

public class ToggleAnimation {
    private final Supplier duration;
    private final boolean initialState;
    private final Supplier easing;
    private long startTime;
    private boolean state;
    private static final long UNSET_START_TIME = 0L;

    public ToggleAnimation(Supplier durationSupplier, boolean startOn, Supplier easingSupplier) {
        this.startTime = UNSET_START_TIME;
        this.duration = durationSupplier;
        this.initialState = startOn;
        this.easing = easingSupplier;
        this.state = startOn;
    }

    public ToggleAnimation(float durationMs, boolean startOn, Easing easingType) {
        this(() -> durationMs, startOn, () -> easingType);
    }

    public ToggleAnimation(Supplier durationSupplier, boolean startOn, Easing easingType) {
        this(durationSupplier, startOn, () -> easingType);
    }

    public ToggleAnimation(float durationMs, boolean startOn, Supplier easingSupplier) {
        this(() -> durationMs, startOn, easingSupplier);
    }

    public boolean getState() {
        return this.state;
    }

    public void setState(boolean newState) {
        if (!newState) {
            this.startTime = System.currentTimeMillis() - (long)((1.0 - this.getLinearProgress()) * ((Float)this.duration.get()).floatValue());
        } else {
            this.startTime = System.currentTimeMillis() - (long)(this.getLinearProgress() * ((Float)this.duration.get()).floatValue());
        }

        this.state = newState;
    }

    public float getProgress() {
        if (this.state) {
            return (float)((Easing)this.easing.get()).apply(Math.min(1.0, Math.max(0.0, (double)(System.currentTimeMillis() - this.startTime) / ((Float)this.duration.get()).floatValue())));
        } else {
            return (float)((Easing)this.easing.get()).apply(Math.min(1.0, Math.max(0.0, 1.0 - (double)(System.currentTimeMillis() - this.startTime) / ((Float)this.duration.get()).floatValue())));
        }
    }

    public void reset() {
        this.state = this.initialState;
        if (this.initialState) {
            this.startTime = System.currentTimeMillis() - (long)((1.0 - this.getLinearProgress()) * ((Float)this.duration.get()).floatValue());
        } else {
            this.startTime = System.currentTimeMillis() - (long)(this.getLinearProgress() * ((Float)this.duration.get()).floatValue());
        }
    }

    private double getLinearProgress() {
        return !this.state
            ? Math.min(1.0, Math.max(0.0, 1.0 - (double)(System.currentTimeMillis() - this.startTime) / ((Float)this.duration.get()).floatValue()))
            : Math.min(1.0, Math.max(0.0, (double)(System.currentTimeMillis() - this.startTime) / ((Float)this.duration.get()).floatValue()));
    }
}

package dev.zprestige.prestige.render;

public class ScheduledClick {
    private final int button;
    private final long scheduledAt;
    private final float pressDelay;
    private final float holdDuration;
    private boolean pressed;

    public ScheduledClick(int mouseButton, long createdAt, float delayMs, float holdMs, boolean initiallyPressed) {
        this.button = mouseButton;
        this.scheduledAt = createdAt;
        this.pressDelay = delayMs;
        this.holdDuration = holdMs;
        this.pressed = initiallyPressed;
    }

    public int getButton() {
        return this.button;
    }

    public long getScheduledAt() {
        return this.scheduledAt;
    }

    public float getPressDelay() {
        return this.pressDelay;
    }

    public float getHoldDuration() {
        return this.holdDuration;
    }

    public boolean isPressed() {
        return this.pressed;
    }

    public void setPressed(boolean isDown) {
        this.pressed = isDown;
    }
}

package dev.zprestige.prestige.event;

public class FloatingItemTickEvent extends Event {
    private int extraTicks;

    public FloatingItemTickEvent(int initialExtraTicks) {
        this.extraTicks = initialExtraTicks;
    }

    public int getExtraTicks() {
        return this.extraTicks;
    }

    public void setExtraTicks(int value) {
        this.extraTicks = value;
    }
}

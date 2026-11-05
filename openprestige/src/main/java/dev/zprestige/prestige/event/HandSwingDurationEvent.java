package dev.zprestige.prestige.event;

public class HandSwingDurationEvent extends Event {
    public int duration;

    public HandSwingDurationEvent(int initialDuration) {
        this.duration = initialDuration;
    }
}

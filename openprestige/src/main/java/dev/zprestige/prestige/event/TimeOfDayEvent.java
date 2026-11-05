package dev.zprestige.prestige.event;

public class TimeOfDayEvent extends Event {
    private long time;

    public TimeOfDayEvent(long initialTime) {
        this.time = initialTime;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long value) {
        this.time = value;
    }
}

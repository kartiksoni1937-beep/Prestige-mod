package dev.zprestige.prestige.event;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.type.EventPhase;

public class Event {
    private final EventPhase phase;
    private boolean cancelled = false;

    public Event(EventPhase eventPhase) {
        this.phase = eventPhase;
    }

    public Event() {
        this(EventPhase.NONE);
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public EventPhase getPhase() {
        return this.phase;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean post() {
        return Prestige.eventBus == null ? false : Prestige.eventBus.post(this);
    }
}

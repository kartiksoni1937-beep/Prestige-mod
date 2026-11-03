package dev.zprestige.prestige.event;

import dev.zprestige.prestige.type.EventPhase;

public class SwingHandEvent extends Event {
    private boolean skipPost;

    public SwingHandEvent(EventPhase eventPhase) {
        super(eventPhase);
    }

    public boolean isSkipPost() {
        return this.skipPost;
    }

    public void setSkipPost(boolean value) {
        this.skipPost = value;
    }
}

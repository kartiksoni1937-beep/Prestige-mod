package dev.zprestige.prestige.event;

public class KeyEvent extends Event {
    private final int key;
    private final int action;

    public KeyEvent(int keyCode, int keyAction) {
        this.key = keyCode;
        this.action = keyAction;
    }

    public int getKey() {
        return this.key;
    }

    public int getAction() {
        return this.action;
    }
}

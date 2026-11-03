package dev.zprestige.prestige.event;

public class MouseButtonEvent extends Event {
    private final int button;
    private final int action;

    public MouseButtonEvent(int mouseButton, int buttonAction) {
        this.button = mouseButton;
        this.action = buttonAction;
    }

    public int getButton() {
        return this.button;
    }

    public int getAction() {
        return this.action;
    }
}

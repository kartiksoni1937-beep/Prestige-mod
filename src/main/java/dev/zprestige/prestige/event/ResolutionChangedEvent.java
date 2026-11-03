package dev.zprestige.prestige.event;

import net.minecraft.client.util.Window;

public class ResolutionChangedEvent extends Event {
    private final Window window;

    public ResolutionChangedEvent(Window gameWindow) {
        this.window = gameWindow;
    }

    public Window getWindow() {
        return this.window;
    }
}

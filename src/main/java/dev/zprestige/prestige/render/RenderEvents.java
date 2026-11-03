package dev.zprestige.prestige.render;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface RenderEvents {
    Logger LOGGER = LoggerFactory.getLogger(RenderEvents.class);
    Event HUD_RENDER = createEvent();
    Event WORLD_RENDER = createEvent();
    Event AFTER_HUD_RENDER = createEvent();

    @NotNull
    private static Event createEvent() {
        return EventFactory.createArrayBacked(RenderCallback.class, listeners -> (buffers, context) -> {
            for (RenderCallback listener : (listeners)) {
                try {
                    listener.onRender(buffers, context);
                } catch (Throwable throwable) {
                    LOGGER.error("Render listener failed", throwable);
                }
            }
        });
    }
}

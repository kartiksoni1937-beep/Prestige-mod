package dev.zprestige.prestige.render;

import dev.zprestige.prestige.util.Log;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.NotNull;

public interface RenderEvents {
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
                    Log.error("Render listener failed", throwable);
                }
            }
        });
    }
}

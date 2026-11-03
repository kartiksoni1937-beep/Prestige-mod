package dev.zprestige.prestige.event;

import dev.zprestige.prestige.type.EventPhase;
import net.minecraft.entity.Entity;

public class PlayerRenderStateEvent extends Event {
    public final Entity entity;

    public PlayerRenderStateEvent(Entity renderedEntity, EventPhase eventPhase) {
        super(eventPhase);
        this.entity = renderedEntity;
    }
}

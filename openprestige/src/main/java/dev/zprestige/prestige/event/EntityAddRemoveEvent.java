package dev.zprestige.prestige.event;

import dev.zprestige.prestige.type.EventPhase;
import net.minecraft.entity.Entity;

public class EntityAddRemoveEvent extends Event {
    private final Entity entity;

    public EntityAddRemoveEvent(Entity changedEntity, EventPhase eventPhase) {
        super(eventPhase);
        this.entity = changedEntity;
    }

    public Entity getEntity() {
        return this.entity;
    }
}

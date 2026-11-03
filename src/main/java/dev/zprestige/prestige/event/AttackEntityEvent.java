package dev.zprestige.prestige.event;

import net.minecraft.entity.Entity;

public class AttackEntityEvent extends Event {
    private final Entity target;

    public AttackEntityEvent(Entity attacked) {
        this.target = attacked;
    }

    public Entity getTarget() {
        return this.target;
    }
}

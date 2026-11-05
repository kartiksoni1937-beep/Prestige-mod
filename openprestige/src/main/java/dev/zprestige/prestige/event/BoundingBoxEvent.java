package dev.zprestige.prestige.event;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

public class BoundingBoxEvent extends Event {
    private final Entity entity;
    private Box box;

    public BoundingBoxEvent(Entity owner, Box originalBox) {
        this.entity = owner;
        this.box = originalBox;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public Box getBox() {
        return this.box;
    }

    public void setBox(Box newBox) {
        this.box = newBox;
    }
}

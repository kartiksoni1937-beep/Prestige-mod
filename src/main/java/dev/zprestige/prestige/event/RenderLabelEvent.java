package dev.zprestige.prestige.event;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

public class RenderLabelEvent extends Event {
    private Text originalText;
    private Text displayText;
    private Entity entity;

    public RenderLabelEvent(Text original, Text display) {
        this(original, display, null);
    }

    public RenderLabelEvent(Text original, Text display, Entity labelledEntity) {
        this.originalText = original;
        this.displayText = display;
        this.entity = labelledEntity;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public void setEntity(Entity newEntity) {
        this.entity = newEntity;
    }

    public Text getOriginalText() {
        return this.originalText;
    }

    public void setOriginalText(Text newText) {
        this.originalText = newText;
    }

    public Text getDisplayText() {
        return this.displayText;
    }

    public void setDisplayText(Text newText) {
        this.displayText = newText;
    }
}

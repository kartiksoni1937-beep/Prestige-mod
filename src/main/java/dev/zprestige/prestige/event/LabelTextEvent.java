package dev.zprestige.prestige.event;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

public class LabelTextEvent extends Event {
    private final Entity entity;
    private Text text;

    public LabelTextEvent(Entity labelledEntity, Text initialText) {
        this.entity = labelledEntity;
        this.text = initialText;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public Text getText() {
        return this.text;
    }

    public void setText(Text newText) {
        this.text = newText;
    }
}

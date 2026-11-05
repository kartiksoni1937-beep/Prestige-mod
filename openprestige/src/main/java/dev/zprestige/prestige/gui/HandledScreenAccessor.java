package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public class HandledScreenAccessor {
    private final HandledScreen screen;
    private static final String FOCUSED_SLOT_FIELD = "focusedSlot";

    public HandledScreenAccessor(HandledScreen handledScreen) {
        this.screen = handledScreen;
    }

    public Slot getFocusedSlot() {
        return (Slot)FieldAccessor.bindMapped(this.screen, FOCUSED_SLOT_FIELD, HandledScreen.class, Slot.class).get();
    }
}

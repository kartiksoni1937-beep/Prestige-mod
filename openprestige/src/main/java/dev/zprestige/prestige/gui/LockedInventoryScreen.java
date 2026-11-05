package dev.zprestige.prestige.gui;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;

public class LockedInventoryScreen extends InventoryScreen {
    public LockedInventoryScreen(PlayerEntity player) {
        super(player);
    }

    public boolean handleHotbarKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}

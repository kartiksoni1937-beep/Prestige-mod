package dev.zprestige.prestige.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerEntity;

public class LockedInventoryScreen extends InventoryScreen {
    public LockedInventoryScreen(PlayerEntity player) {
        super(player);
    }

    @Override
    public boolean handleHotbarKeyPressed(KeyInput input) {
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return false;
    }
}

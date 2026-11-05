package dev.zprestige.prestige.event;

import net.minecraft.entity.player.PlayerEntity;

public class BotCheckEvent extends Event {
    private final PlayerEntity player;

    public BotCheckEvent(PlayerEntity candidate) {
        this.player = candidate;
    }

    public PlayerEntity getPlayer() {
        return this.player;
    }
}

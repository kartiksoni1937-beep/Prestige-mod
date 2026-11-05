package dev.zprestige.prestige.event;

import net.minecraft.entity.player.PlayerEntity;

public class HealthIndicatorEvent extends Event {
    private final PlayerEntity player;
    private String suffix;

    public HealthIndicatorEvent(PlayerEntity labelledPlayer, String initialSuffix) {
        this.player = labelledPlayer;
        this.suffix = initialSuffix;
    }

    public PlayerEntity getPlayer() {
        return this.player;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public void setSuffix(String value) {
        this.suffix = value;
    }
}

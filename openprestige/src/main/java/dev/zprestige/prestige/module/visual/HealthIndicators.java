package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.HealthIndicatorEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;

public class HealthIndicators extends Module {
    public HealthIndicators() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Subscribe
    public void onHealthIndicator(HealthIndicatorEvent event) {
        float health = (float)Math.ceil(this.getTotalHealth(event.getPlayer()));
        event.setSuffix(" | " + String.valueOf(this.getHealthColor(health)) + health + String.valueOf(Formatting.RESET) + " | ");
        event.cancel();
    }

    private Formatting getHealthColor(float health) {
        if (health <= 5.0F) {
            return Formatting.RED;
        } else if (health <= 10.0F) {
            return Formatting.GOLD;
        } else if (health <= 15.0F) {
            return Formatting.YELLOW;
        } else {
            return health <= 20.0F ? Formatting.GREEN : Formatting.DARK_GREEN;
        }
    }

    private float getTotalHealth(PlayerEntity player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }
}

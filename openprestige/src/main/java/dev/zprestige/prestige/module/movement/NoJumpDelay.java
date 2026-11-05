package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.JumpCooldownAccessor;
import dev.zprestige.prestige.event.ClientTickEvent;
import java.util.Set;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null) {
            return;
        }

        new JumpCooldownAccessor(mc.player).setJumpingCooldown(0);
    }
}

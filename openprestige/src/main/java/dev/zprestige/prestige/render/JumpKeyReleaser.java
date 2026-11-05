package dev.zprestige.prestige.render;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;

public final class JumpKeyReleaser {
    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (JumpKeyPresser.ticksRemaining > 0 && --JumpKeyPresser.ticksRemaining == 0) {
            JumpKeyPresser.releaseJumpKey();
        }
    }
}

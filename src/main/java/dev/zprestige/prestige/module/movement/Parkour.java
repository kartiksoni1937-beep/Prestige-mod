package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.JumpKeyPresser;
import net.minecraft.util.math.Box;

public class Parkour extends Module {
    public Parkour() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting distance;

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
            if (!mc.player.isSneaking() && !mc.options.sneakKey.isPressed()) {
                Box edgeBox = mc.player.getBoundingBox().offset(0.0, -0.5, 0.0)
                    .expand(-this.distance.randomInRange() / 100.0, 0.0, -this.distance.randomInRange() / 100.0);
                if (!mc.world.getBlockCollisions(mc.player, edgeBox).iterator().hasNext()) {
                    JumpKeyPresser.pressJump();
                }
            }
        }
    }
}

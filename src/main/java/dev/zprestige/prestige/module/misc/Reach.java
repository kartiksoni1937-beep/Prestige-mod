package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ReachEvent;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;

public class Reach extends Module {
    public Reach() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting chance;
    private RangeSetting extraReach;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC, Gamemode.Mace);
    }

    @Subscribe
    public void onReach(ReachEvent event) {
        if (RandomUtil.RANDOM.nextFloat(100.0F) <= this.chance.getValue()) {
            event.setExtraReach(this.extraReach.randomInRange());
            event.cancel();
        }
    }
}

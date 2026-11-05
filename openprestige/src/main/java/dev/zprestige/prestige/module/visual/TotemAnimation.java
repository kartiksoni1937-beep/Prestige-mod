package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FloatingItemTickEvent;

public class TotemAnimation extends Module {
    public TotemAnimation() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private IntSetting speed;

    @Subscribe
    public void onFloatingItemTick(FloatingItemTickEvent event) {
        event.setExtraTicks(this.speed.getValue());
    }
}

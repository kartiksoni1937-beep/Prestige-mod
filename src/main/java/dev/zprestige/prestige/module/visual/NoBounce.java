package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ItemBobbingEvent;

public class NoBounce extends Module {
    public NoBounce() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Subscribe
    public void onItemBobbing(ItemBobbingEvent event) {
        event.cancel();
    }
}

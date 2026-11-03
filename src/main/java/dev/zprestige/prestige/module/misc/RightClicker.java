package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;

public class RightClicker extends Module {
    public RightClicker() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting minCps;
    private FloatSetting maxCps;
    private StopWatch clickTimer = new StopWatch();
    private float nextClickDelay = 200.0F;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Override
    public void onEnable() {
        this.rollClickDelay();
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen == null && mc.player != null && !mc.player.isUsingItem() && !Prestige.clickScheduler.processPendingClick()) {
            if (this.clickTimer.hasElapsed(this.nextClickDelay)) {
                Prestige.clickScheduler.scheduleClick(1, 0.0F);
                this.rollClickDelay();
                this.clickTimer.reset();
            }
        }
    }

    private void rollClickDelay() {
        this.nextClickDelay = 1000.0F / RandomUtil.randomFloat(Math.max(this.minCps.getValue(), 1.0F), this.maxCps.getValue());
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FireOverlayEvent;
import dev.zprestige.prestige.event.LoadingScreenEvent;
import dev.zprestige.prestige.event.FullBrightEvent;
import dev.zprestige.prestige.event.HurtCamEvent;
import dev.zprestige.prestige.event.AddParticleEvent;
import dev.zprestige.prestige.event.RainGradientEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.OverlayMessageEvent;
import dev.zprestige.prestige.event.RenderScoreboardEvent;
import dev.zprestige.prestige.event.ScreenBackgroundEvent;

public class RenderOptimizer extends Module {
    public RenderOptimizer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting hurtCam;
    private BoolSetting fire;
    private BoolSetting particles;
    private BoolSetting backgroundTint;
    private BoolSetting fullBright;
    private BoolSetting removeWeather;
    private BoolSetting shortenLoadingScreen;
    private BoolSetting noScoreboard;
    private BoolSetting noTitle;
    private BoolSetting noHotbarText;

    @Subscribe
    public void onHurtCam(HurtCamEvent event) {
        if (this.hurtCam.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onFireOverlay(FireOverlayEvent event) {
        if (this.fire.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onAddParticle(AddParticleEvent event) {
        if (this.particles.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onScreenBackground(ScreenBackgroundEvent event) {
        if (this.backgroundTint.getValue() && mc.world != null) {
            event.cancel();
        }
    }

    @Subscribe
    public void onFullBright(FullBrightEvent event) {
        if (this.fullBright.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onRainGradient(RainGradientEvent event) {
        if (this.removeWeather.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onLoadingScreen(LoadingScreenEvent event) {
        if (this.shortenLoadingScreen.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onRenderScoreboard(RenderScoreboardEvent event) {
        if (this.noScoreboard.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (this.noTitle.getValue()) {
            mc.inGameHud.clearTitle();
        }
    }

    @Subscribe
    public void onOverlayMessage(OverlayMessageEvent event) {
        if (this.noHotbarText.getValue()) {
            event.cancel();
        }
    }
}

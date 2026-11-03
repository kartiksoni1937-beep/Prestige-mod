package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.SkyColorEvent;
import dev.zprestige.prestige.event.FogEvent;
import dev.zprestige.prestige.event.TimeOfDayEvent;
import java.awt.Color;
import java.util.function.Predicate;

public class Ambience extends Module {
    private BoolSetting fog;
    private FloatSetting start;
    private FloatSetting end;
    private ColorSetting color;
    private BoolSetting coverHorizonBand;
    private BoolSetting sky;
    private ColorSetting skyColor;
    private BoolSetting customTime;
    private IntSetting time;

    public Ambience() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.skyColor.visibleWhen((Predicate<Color>)ignored -> this.sky.getValue());
        this.color.visibleWhen((Predicate<Color>)ignored -> this.fog.getValue());
        this.time.visibleWhen((Predicate<Integer>)ignored -> this.customTime.getValue());
        this.coverHorizonBand.visibleWhen((Predicate<Boolean>)ignored -> this.fog.getValue());
        this.start.visibleWhen((Predicate<Float>)ignored -> this.fog.getValue());
        this.end.visibleWhen((Predicate<Float>)ignored -> this.fog.getValue());
    }

    @Subscribe
    public void onFog(FogEvent event) {
        if (this.fog.getValue()) {
            float fogStart = Math.min(this.start.getValue(), this.end.getValue());
            float fogEnd = Math.max(this.start.getValue(), this.end.getValue());
            event.setStart(fogStart);
            event.setEnd(fogEnd);
            event.setColor(this.color.getValue());
            if (this.coverHorizonBand.getValue()) {
                event.setSkyEnd(fogEnd);
            }

            event.cancel();
        }
    }

    @Subscribe
    public void onSkyColor(SkyColorEvent event) {
        if (this.sky.getValue()) {
            Color newSkyColor = this.skyColor.getValue();
            event.setSkyColor(newSkyColor);
            event.setHorizonColor(newSkyColor);
            event.cancel();
        }
    }

    @Subscribe
    public void onTimeOfDay(TimeOfDayEvent event) {
        if (this.customTime.getValue()) {
            event.setTime((long)this.time.getValue().intValue());
            event.cancel();
        }
    }
}

package dev.zprestige.prestige.event;

import java.awt.Color;

public class SkyColorEvent extends Event {
    private Color skyColor;
    private Color horizonColor;

    public SkyColorEvent(Color initialSky) {
        this.skyColor = initialSky;
        this.horizonColor = null;
    }

    public SkyColorEvent(Color initialSky, Color initialHorizon) {
        this.skyColor = initialSky;
        this.horizonColor = initialHorizon;
    }

    public Color getSkyColor() {
        return this.skyColor;
    }

    public void setSkyColor(Color color) {
        this.skyColor = color;
    }

    public Color getHorizonColor() {
        return this.horizonColor;
    }

    public void setHorizonColor(Color color) {
        this.horizonColor = color;
    }
}

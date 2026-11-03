package dev.zprestige.prestige.event;

import java.awt.Color;

public class FogEvent extends Event {
    private float start;
    private float end;
    private Color color;
    private Float skyEnd;

    public FogEvent(float fogStart, float fogEnd, Color fogColor) {
        this.start = fogStart;
        this.end = fogEnd;
        this.color = fogColor;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color newColor) {
        this.color = newColor;
    }

    public float getStart() {
        return this.start;
    }

    public void setStart(float value) {
        this.start = value;
    }

    public float getEnd() {
        return this.end;
    }

    public void setEnd(float value) {
        this.end = value;
    }

    public Float getSkyEnd() {
        return this.skyEnd;
    }

    public void setSkyEnd(Float value) {
        this.skyEnd = value;
    }
}

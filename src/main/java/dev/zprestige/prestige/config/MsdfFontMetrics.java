package dev.zprestige.prestige.config;

import com.google.gson.JsonObject;

public class MsdfFontMetrics {
    private int emSize;
    private float lineHeight;
    private float ascender;
    private float descender;
    private float underlineY;
    private float underlineThickness;

    public MsdfFontMetrics() {
    }

    public MsdfFontMetrics(int size, float lineSpacing, float ascent, float descent, float underlineOffset, float underlineHeight) {
        this.emSize = size;
        this.lineHeight = lineSpacing;
        this.ascender = ascent;
        this.descender = descent;
        this.underlineY = underlineOffset;
        this.underlineThickness = underlineHeight;
    }

    public static MsdfFontMetrics fromJson(JsonObject json) {
        MsdfFontMetrics metrics = new MsdfFontMetrics();
        metrics.emSize = json.get("emSize").getAsInt();
        metrics.lineHeight = json.get("lineHeight").getAsFloat();
        metrics.ascender = json.get("ascender").getAsFloat();
        metrics.descender = json.get("descender").getAsFloat();
        metrics.underlineY = json.get("underlineY").getAsFloat();
        metrics.underlineThickness = json.get("underlineThickness").getAsFloat();
        return metrics;
    }

    public int getEmSize() {
        return this.emSize;
    }

    public float getLineHeight() {
        return this.lineHeight;
    }

    public float getAscender() {
        return this.ascender;
    }

    public float getDescender() {
        return this.descender;
    }

    public float getUnderlineY() {
        return this.underlineY;
    }

    public float getUnderlineThickness() {
        return this.underlineThickness;
    }
}

package dev.zprestige.prestige.config;

import com.google.gson.JsonObject;

public class MsdfGlyph {
    private int codePoint;
    private float advance;
    private float planeLeft;
    private float planeBottom;
    private float planeRight;
    private float planeTop;
    private float atlasLeft;
    private float atlasBottom;
    private float atlasRight;
    private float atlasTop;

    public static MsdfGlyph fromJson(JsonObject json) {
        MsdfGlyph glyph = new MsdfGlyph();
        glyph.codePoint = json.get("unicode").getAsInt();
        glyph.advance = json.get("advance").getAsFloat();
        if (json.has("planeBounds")) {
            JsonObject planeBounds = json.get("planeBounds").getAsJsonObject();
            glyph.planeLeft = planeBounds.get("left").getAsFloat();
            glyph.planeBottom = planeBounds.get("bottom").getAsFloat();
            glyph.planeRight = planeBounds.get("right").getAsFloat();
            glyph.planeTop = planeBounds.get("top").getAsFloat();
        }

        if (json.has("atlasBounds")) {
            JsonObject atlasBounds = json.get("atlasBounds").getAsJsonObject();
            glyph.atlasLeft = atlasBounds.get("left").getAsFloat();
            glyph.atlasBottom = atlasBounds.get("bottom").getAsFloat();
            glyph.atlasRight = atlasBounds.get("right").getAsFloat();
            glyph.atlasTop = atlasBounds.get("top").getAsFloat();
        }

        return glyph;
    }

    public int getCodePoint() {
        return this.codePoint;
    }

    public float getAdvance() {
        return this.advance;
    }

    public float getPlaneLeft() {
        return this.planeLeft;
    }

    public float getPlaneBottom() {
        return this.planeBottom;
    }

    public float getPlaneRight() {
        return this.planeRight;
    }

    public float getPlaneTop() {
        return this.planeTop;
    }

    public float getAtlasLeft() {
        return this.atlasLeft;
    }

    public float getAtlasBottom() {
        return this.atlasBottom;
    }

    public float getAtlasRight() {
        return this.atlasRight;
    }

    public float getAtlasTop() {
        return this.atlasTop;
    }
}

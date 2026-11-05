package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;

// $VF: synthetic class
public class GlPrimitiveSwitchMap {
    public static final boolean ASSERTIONS_DISABLED;
    public static final int[] SWITCH_MAP = new int[GlPrimitive.values().length];

    static {
        try {
            SWITCH_MAP[GlPrimitive.TRIANGLES.ordinal()] = 1;
        } catch (NoSuchFieldError missingTriangles) {
        }

        try {
            SWITCH_MAP[GlPrimitive.QUADS.ordinal()] = 2;
        } catch (NoSuchFieldError missingQuads) {
        }

        try {
            SWITCH_MAP[GlPrimitive.TRIANGLES_FAN.ordinal()] = 3;
        } catch (NoSuchFieldError missingTriangleFan) {
        }

        try {
            SWITCH_MAP[GlPrimitive.DEBUG_LINE_STRIP.ordinal()] = 4;
        } catch (NoSuchFieldError missingLineStrip) {
        }

        try {
            SWITCH_MAP[GlPrimitive.QUAD_STRIP.ordinal()] = 5;
        } catch (NoSuchFieldError missingQuadStrip) {
        }

        try {
            SWITCH_MAP[GlPrimitive.LINES.ordinal()] = 0;
        } catch (NoSuchFieldError missingLines) {
        }

        ASSERTIONS_DISABLED = !MeshBuilder.class.desiredAssertionStatus();
    }
}

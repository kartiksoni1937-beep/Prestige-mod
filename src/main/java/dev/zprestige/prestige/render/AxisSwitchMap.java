package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.GuideAxis;

// $VF: synthetic class
public class AxisSwitchMap {
    public static final int[] SWITCH_MAP = new int[GuideAxis.values().length];

    static {
        try {
            SWITCH_MAP[GuideAxis.X.ordinal()] = 1;
        } catch (NoSuchFieldError missingX) {
        }

        try {
            SWITCH_MAP[GuideAxis.Y.ordinal()] = 2;
        } catch (NoSuchFieldError missingY) {
        }
    }
}

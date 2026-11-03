package dev.zprestige.prestige.world;

import net.minecraft.world.Difficulty;

// $VF: synthetic class
public class DifficultySwitchMap {
    public static final int[] CASE_BY_ORDINAL = new int[Difficulty.values().length];

    static {
        try {
            CASE_BY_ORDINAL[Difficulty.PEACEFUL.ordinal()] = 1;
        } catch (NoSuchFieldError ignoredPeaceful) {
        }

        try {
            CASE_BY_ORDINAL[Difficulty.EASY.ordinal()] = 2;
        } catch (NoSuchFieldError ignoredEasy) {
        }

        try {
            CASE_BY_ORDINAL[Difficulty.HARD.ordinal()] = 3;
        } catch (NoSuchFieldError ignoredHard) {
        }
    }
}

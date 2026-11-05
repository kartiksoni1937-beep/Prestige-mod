package dev.zprestige.prestige.combat;

import java.awt.Color;

public final class HudStyle {
    public static final float SCALE_SMALL = 0.65F;
    public static final float SCALE_MEDIUM = 0.75F;
    public static final float SCALE_ROW = 0.9F;
    public static final float SCALE_TITLE = 1.0F;
    public static final float SCALE_VALUE = 1.0F;
    public static final float SCALE_NORMAL = 1.0F;
    public static final float CORNER_RADIUS = 4.0F;
    public static final float PADDING_Y = 3.0F;
    public static final float BADGE_PADDING = 1.5F;
    public static final int BLUR_RADIUS = 5;
    public static final float PADDING_X = 5.5F;
    public static final float DIVIDER_INSET = 3.0F;
    public static final float ROW_SPACING = 3.0F;
    public static final float LINE_GAP = 1.0F;
    public static final float DIVIDER_WIDTH = 0.5F;
    public static final float BAR_HEIGHT = 4.0F;
    public static final float BAR_GAP = 1.0F;
    public static final Color BACKGROUND_COLOR = new Color(14, 14, 14, 200);
    public static final Color TEXT_COLOR = Color.WHITE;
    public static final Color SECONDARY_TEXT_COLOR = new Color(200, 200, 200, 220);
    public static final Color MUTED_TEXT_COLOR = new Color(160, 160, 168, 200);
    public static final Color DIVIDER_COLOR = new Color(255, 255, 255, 40);

    private HudStyle() {
    }

    public static float getRowHeight(float fontHeight, float scale) {
        return fontHeight * scale + 6.0F;
    }

    public static float getCardHeight(float fontHeight) {
        return 3.0F + fontHeight * 1.0F + 1.0F + 4.0F + 3.0F;
    }
}

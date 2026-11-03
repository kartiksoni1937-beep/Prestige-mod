package dev.zprestige.prestige.math;

import dev.zprestige.prestige.client.Prestige;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

public class UiMath {
    public float x;
    public float y;
    public float width;
    public float height;
    public String label;

    public UiMath(float initialX, float initialY, float initialWidth, float initialHeight, String initialLabel) {
        this.x = initialX;
        this.y = initialY;
        this.width = initialWidth;
        this.height = initialHeight;
        this.label = initialLabel;
    }

    public static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, net.minecraft.util.math.MathHelper.clamp(alpha, 0.0F, 1.0F));
    }

    public static Color getAccentColor() {
        return Prestige.moduleManager.getMenu().getMenuColor().getValue();
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float approach(float current, float target, float speed) {
        return current - (current - target) * clamp(speed, 0.0F, 1.0F);
    }

    public static float floorToDecimals(float value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException();
        } else {
            return BigDecimal.valueOf((double)value).setScale(decimals, RoundingMode.FLOOR).floatValue();
        }
    }

    public static String getSpecialKeyName(int keyCode) {
        HashMap names = new HashMap();
        names.put(9999, "LMB");
        names.put(9998, "RMB");
        names.put(9997, "MMB");
        names.put(9996, "MB4");
        names.put(9995, "MM5");
        names.put(345, "R-Ctrl");
        names.put(344, "Right Shift");
        names.put(342, "L-Alt");
        names.put(341, "L-Ctrl");
        names.put(340, "Shift");
        names.put(258, "Tab");
        return (String)names.get(keyCode);
    }

    public void init() {
    }

    public void update() {
    }

    public void draw() {
    }

    public void onShow() {
    }

    public void onHide() {
    }

    public void clearLabel() {
        this.label = null;
    }

    public float getX() {
        return this.x;
    }

    public void setX(float newX) {
        this.x = newX;
    }

    public float getY() {
        return this.y;
    }

    public void setY(float newY) {
        this.y = newY;
    }

    public float getWidth() {
        return this.width;
    }

    public void setWidth(float newWidth) {
        this.width = newWidth;
    }

    public float getHeight() {
        return this.height;
    }

    public void setHeight(float newHeight) {
        this.height = newHeight;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String newLabel) {
        this.label = newLabel;
    }
}

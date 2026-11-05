package dev.zprestige.prestige.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FontManager {
    private final Map fonts = new HashMap();
    private MsdfFont defaultFont;

    public MsdfFont getDefaultFont() {
        MsdfFont font = this.defaultFont;
        if (font == null) {
            font = this.getFont("inter");
            this.defaultFont = font;
        }

        return font;
    }

    public MsdfFont getFont(String name) {
        return (MsdfFont)this.fonts.computeIfAbsent(name, key -> {
            try {
                return new MsdfFont(name);
            } catch (IOException exception) {
                throw new RuntimeException("Failed to load MSDF font: " + name, exception);
            }
        });
    }
}

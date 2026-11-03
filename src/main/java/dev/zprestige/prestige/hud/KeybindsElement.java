package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.KeybindListEntry;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;

public class KeybindsElement extends HudElement {
    private final Map moduleAnimations;
    private GlTextureHandle iconTexture;
    private float animatedWidth;
    private float animatedKeyWidth;
    private long lastFrameTime;
    private float lastHeight;
    private float lastWidth;
    private static final long UNSET_TIME = 0L;

    public KeybindsElement() {
        super("Keybinds", "Show the client's keybinds");
        this.moduleAnimations = new HashMap();
        this.animatedWidth = 70.0F;
        this.animatedKeyWidth = 0.0F;
        this.lastFrameTime = UNSET_TIME;
        this.lastHeight = 0.0F;
        this.lastWidth = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 63.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.iconTexture == null) {
            this.iconTexture = TextureLoader.loadTexture("assets/prestige/icons/other/keyboard.png");
        }

        float frameDelta = (float)(System.currentTimeMillis() - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = System.currentTimeMillis();
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float headerHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float rowHeight = HudStyle.getRowHeight(fontHeight, 0.75F);
        ArrayList entries = new ArrayList();

        for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            float targetVisibility = module.isEnabled() && module.getKey() != -1 ? 1.0F : 0.0F;
            float visibility = UiMath.approach((float)this.moduleAnimations.getOrDefault(module, 0.0F), targetVisibility, frameDelta);
            this.moduleAnimations.put(module, visibility);
            if (!(visibility < 0.05F)) {
                String resolvedKeyName;
                if (KeyEvent.getKeyText(module.getKey()).contains("Unknown")) {
                    resolvedKeyName = UiMath.getSpecialKeyName(module.getKey());
                } else {
                    resolvedKeyName = KeyEvent.getKeyText(module.getKey());
                }

                String keyName = resolvedKeyName;
                if (keyName != null && !keyName.isEmpty()) {
                    entries.add(new KeybindListEntry(module, keyName, visibility));
                }
            }
        }

        float targetKeyWidth = 0.0F;
        float targetWidth = 70.0F;

        for (KeybindListEntry measuredEntry : (Iterable<KeybindListEntry>)(entries)) {
            float entryKeyWidth = Prestige.fontManager.getDefaultFont().getTextWidth(measuredEntry.keyName()) * 0.75F + 5.5F;
            float entryNameWidth = Prestige.fontManager.getDefaultFont().getTextWidth(measuredEntry.module().getName()) * 0.75F;
            targetKeyWidth = Math.max(targetKeyWidth, entryKeyWidth * measuredEntry.alpha());
            targetWidth = Math.max(targetWidth, (5.5F + entryKeyWidth + 3.0F + entryNameWidth + 5.5F) * measuredEntry.alpha());
        }

        this.animatedWidth = UiMath.approach(this.animatedWidth, targetWidth, frameDelta * 2.0F);
        this.animatedKeyWidth = UiMath.approach(this.animatedKeyWidth, targetKeyWidth, frameDelta * 2.0F);
        float totalHeight = headerHeight;

        for (KeybindListEntry heightEntry : (Iterable<KeybindListEntry>)(entries)) {
            totalHeight += (rowHeight + 1.5F) * heightEntry.alpha();
        }

        boolean rightAligned = this.isAnchoredRight();
        boolean bottomAligned = this.isAnchoredBottom();
        if (!(mc.currentScreen instanceof HudEditorScreen)) {
            if (bottomAligned && this.lastHeight > 0.0F) {
                this.y = this.y - (totalHeight - this.lastHeight);
            }

            if (rightAligned && this.lastWidth > 0.0F) {
                this.x = this.x - (this.animatedWidth - this.lastWidth);
            }
        }

        this.lastHeight = totalHeight;
        this.lastWidth = this.animatedWidth;
        float panelX = this.x;
        float panelY = this.y;
        this.setSize(this.animatedWidth, totalHeight);
        Color accent = UiMath.getAccentColor();
        float headerY = bottomAligned ? panelY + totalHeight - headerHeight : panelY;
        int rawAlpha = 1773;
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, headerY, this.animatedWidth, headerHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, headerY, this.animatedWidth, headerHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        String title = "Keybinds";
        float iconInset = (headerHeight - 7.5F) / 2.0F;
        float titleY = headerY + (headerHeight - fontHeight) / 2.0F;
        if (rightAligned) {
            float mirroredIconX = panelX + this.animatedWidth - 5.5F - 7.5F;
            RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, mirroredIconX, headerY + iconInset, 7.5F, 7.5F, accent);
            float mirroredDividerX = mirroredIconX - 3.0F - 0.5F;
            ShapeRenderer.drawRect(matrix, mirroredDividerX, headerY + iconInset, mirroredDividerX + 0.5F, headerY + headerHeight - iconInset, HudStyle.DIVIDER_COLOR);
            float mirroredCharX = mirroredDividerX - 3.0F - Prestige.fontManager.getDefaultFont().getTextWidth(title);

            for (int mirroredCharIndex = 0; mirroredCharIndex < title.length(); mirroredCharIndex++) {
                String mirroredCharText = String.valueOf(title.charAt(mirroredCharIndex));
                Prestige.fontManager.getDefaultFont().drawText(matrix, mirroredCharText, mirroredCharX, titleY, Render2D.getWaveColor(accent, 10, 10 + (title.length() - 1 - mirroredCharIndex)));
                mirroredCharX += Prestige.fontManager.getDefaultFont().getTextWidth(mirroredCharText);
            }
        } else {
            float iconX = panelX + 5.5F;
            RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, iconX, headerY + iconInset, 7.5F, 7.5F, accent);
            float dividerX = iconX + 7.5F + 3.0F;
            ShapeRenderer.drawRect(matrix, dividerX, headerY + iconInset, dividerX + 0.5F, headerY + headerHeight - iconInset, HudStyle.DIVIDER_COLOR);
            float charX = dividerX + 0.5F + 3.0F;

            for (int charIndex = 0; charIndex < title.length(); charIndex++) {
                String charText = String.valueOf(title.charAt(charIndex));
                Prestige.fontManager.getDefaultFont().drawText(matrix, charText, charX, titleY, Render2D.getWaveColor(accent, 10, 10 + charIndex));
                charX += Prestige.fontManager.getDefaultFont().getTextWidth(charText);
            }
        }

        float rowY = bottomAligned ? headerY - 1.5F - rowHeight : panelY + headerHeight + 1.5F;

        for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++) {
            KeybindListEntry rowEntry = (KeybindListEntry)entries.get(rowIndex);
            float rowVisibility = rowEntry.alpha();
            rawAlpha = (int)(255.0F * rowVisibility);
            int rowAlpha = clampAlpha(rawAlpha);
            if (rowVisibility > 0.7F) {
                BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, rowY, this.animatedWidth, rowHeight, 5, 4.0F);
            }

            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, rowY, this.animatedWidth, rowHeight, new Color(HudStyle.BACKGROUND_COLOR.getRed(), HudStyle.BACKGROUND_COLOR.getGreen(), HudStyle.BACKGROUND_COLOR.getBlue(), clampAlpha((int)(HudStyle.BACKGROUND_COLOR.getAlpha() * rowVisibility))), 4.0F);
            float keyBoxX = rightAligned ? panelX + this.animatedWidth - 5.5F - this.animatedKeyWidth : panelX + 5.5F;
            float keyBoxY = rowY + (rowHeight - 8.0F) / 2.0F;
            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, keyBoxX, keyBoxY, this.animatedKeyWidth, 8.0F, new Color(255, 255, 255, clampAlpha((int)(25.0F * rowVisibility))), 3.0F);
            Color waveColor = Render2D.getWaveColor(accent, 10, 10 + rowIndex);
            Color keyTextColor = new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), rowAlpha);
            float keyTextWidth = Prestige.fontManager.getDefaultFont().getTextWidth(rowEntry.keyName()) * 0.75F;
            float keyTextX = keyBoxX + (this.animatedKeyWidth - keyTextWidth) / 2.0F;
            float keyTextY = keyBoxY + (8.0F - fontHeight * 0.75F) / 2.0F;
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, rowEntry.keyName(), keyTextX, keyTextY, 0.75F, keyTextColor);
            String moduleName = rowEntry.module().getName();
            float moduleNameWidth = Prestige.fontManager.getDefaultFont().getTextWidth(moduleName) * 0.75F;
            float moduleNameX = rightAligned ? keyBoxX - 3.0F - moduleNameWidth : keyBoxX + this.animatedKeyWidth + 3.0F;
            float moduleNameY = rowY + (rowHeight - fontHeight * 0.75F) / 2.0F;
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, moduleName, moduleNameX, moduleNameY, 0.75F, new Color(255, 255, 255, clampAlpha((int)(220.0F * rowVisibility))));
            if (bottomAligned) {
                rowY -= (rowHeight + 1.5F) * rowVisibility;
            } else {
                rowY += (rowHeight + 1.5F) * rowVisibility;
            }
        }
    }

    private static int clampAlpha(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}

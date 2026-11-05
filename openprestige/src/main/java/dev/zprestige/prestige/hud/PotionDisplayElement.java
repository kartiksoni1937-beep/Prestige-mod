package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.PotionDisplayEntry;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class PotionDisplayElement extends HudElement {
    private final Map effectEntries;
    private GlTextureHandle iconTexture;
    private float animatedWidth;
    private float lastHeight;
    private float lastWidth;

    public PotionDisplayElement() {
        super("Potion Display", "Show active potion effects with duration and level");
        this.effectEntries = new LinkedHashMap();
        this.animatedWidth = 70.0F;
        this.lastHeight = 0.0F;
        this.lastWidth = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 100.0F);
    }

    public String toRomanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> level > 0 ? String.valueOf(level) : "I";
        };
    }

    public String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes > 0 ? String.format("%d:%02d", minutes, seconds) : String.format("0:%02d", seconds);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.iconTexture == null) {
            this.iconTexture = TextureLoader.loadTexture("assets/prestige/icons/hud/potion.png");
        }

        float frameDelta = (float)(System.currentTimeMillis() - 0L) * 0.005F;
        System.currentTimeMillis();
        HashSet activeEffects = new HashSet();
        if (mc.player != null) {
            for (Entry effectEntry : mc.player.getActiveStatusEffects().entrySet()) {
                StatusEffect effect = (StatusEffect)((RegistryEntry)effectEntry.getKey()).value();
                StatusEffectInstance effectInstance = (StatusEffectInstance)effectEntry.getValue();
                if (effect != null && effectInstance != null && (effectInstance.isInfinite() || effectInstance.getDuration() > 0)) {
                    activeEffects.add(effect);
                    PotionDisplayEntry display = (PotionDisplayEntry)this.effectEntries.computeIfAbsent(effect, newEffect -> new PotionDisplayEntry());
                    display.name = effect.getName().getString();
                    String levelText;
                    if (effectInstance.getAmplifier() > 0) {
                        levelText = this.toRomanNumeral(effectInstance.getAmplifier() + 1);
                    } else {
                        levelText = null;
                    }

                    display.levelText = levelText;
                    display.color = brightenIfDark(new Color(effect.getColor()));
                    display.infinite = effectInstance.isInfinite();
                    display.duration = effectInstance.getDuration();
                    if (display.duration > display.maxDuration) {
                        display.maxDuration = display.duration;
                    }
                }
            }
        }

        Iterator entryIterator = this.effectEntries.entrySet().iterator();

        while (entryIterator.hasNext()) {
            Entry mapEntry = (Entry)entryIterator.next();
            PotionDisplayEntry fadingDisplay = (PotionDisplayEntry)mapEntry.getValue();
            float targetVisibility = activeEffects.contains(mapEntry.getKey()) ? 1.0F : 0.0F;
            fadingDisplay.visibility = UiMath.approach(fadingDisplay.visibility, targetVisibility, frameDelta);
            if (targetVisibility == 0.0F && fadingDisplay.visibility < 0.02F) {
                entryIterator.remove();
            }
        }

        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float headerHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float rowHeight = HudStyle.getRowHeight(fontHeight, 0.9F);
        float titleOffset = 23.0F;
        float targetWidth = Math.max(70.0F, titleOffset + Prestige.fontManager.getDefaultFont().getTextWidth("Potions") + 5.5F);
        float totalHeight = headerHeight;

        for (PotionDisplayEntry measuredDisplay : (Iterable<PotionDisplayEntry>)(this.effectEntries.values())) {
            if (!(measuredDisplay.visibility < 0.02F)) {
                float rowWidth = 5.5F
                    + Prestige.fontManager.getDefaultFont().getTextWidth(measuredDisplay.name) * 0.9F
                    + (measuredDisplay.levelText != null ? 3.0F + this.getLevelBadgeWidth(measuredDisplay.levelText) : 0.0F)
                    + 8.0F
                    + Prestige.fontManager.getDefaultFont().getTextWidth(this.getDurationText(measuredDisplay)) * 0.9F
                    + 5.5F;
                if (rowWidth > targetWidth) {
                    targetWidth = rowWidth;
                }

                totalHeight += (rowHeight + 3.0F) * measuredDisplay.visibility;
            }
        }

        this.animatedWidth = UiMath.approach(this.animatedWidth, targetWidth, frameDelta * 2.0F);
        boolean bottomAligned = this.isAnchoredBottom();
        boolean rightAligned = this.isAnchoredRight();
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
        this.setSize(this.animatedWidth, totalHeight);
        float panelX = this.x;
        float headerY = bottomAligned ? this.y + totalHeight - headerHeight : this.y;
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, headerY, this.animatedWidth, headerHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, headerY, this.animatedWidth, headerHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        String title = "Potions";
        float iconY = headerY + (headerHeight - 7.5F) / 2.0F;
        float titleY = headerY + 3.0F;
        if (rightAligned) {
            RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, panelX + this.animatedWidth - 5.5F - 7.5F, iconY, 7.5F, 7.5F, UiMath.getAccentColor());
            float mirroredDividerX = panelX + this.animatedWidth - 18.0F;
            ShapeRenderer.drawRect(matrix, mirroredDividerX - 0.5F, headerY + 3.0F, mirroredDividerX, headerY + headerHeight - 3.0F, HudStyle.DIVIDER_COLOR);
            float mirroredCharX = panelX + this.animatedWidth - titleOffset - Prestige.fontManager.getDefaultFont().getTextWidth(title);

            for (int mirroredCharIndex = 0; mirroredCharIndex < title.length(); mirroredCharIndex++) {
                String mirroredCharText = String.valueOf(title.charAt(mirroredCharIndex));
                Prestige.fontManager.getDefaultFont().drawText(matrix, mirroredCharText, mirroredCharX, titleY, Render2D.getWaveColor(UiMath.getAccentColor(), 10, 10 + (title.length() - 1 - mirroredCharIndex)));
                mirroredCharX += Prestige.fontManager.getDefaultFont().getTextWidth(mirroredCharText);
            }
        } else {
            RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, panelX + 5.5F, iconY, 7.5F, 7.5F, UiMath.getAccentColor());
            float dividerX = panelX + 5.5F + 7.5F + 5.0F;
            ShapeRenderer.drawRect(matrix, dividerX, headerY + 3.0F, dividerX + 0.5F, headerY + headerHeight - 3.0F, HudStyle.DIVIDER_COLOR);
            float charX = panelX + titleOffset;

            for (int charIndex = 0; charIndex < title.length(); charIndex++) {
                String charText = String.valueOf(title.charAt(charIndex));
                Prestige.fontManager.getDefaultFont().drawText(matrix, charText, charX, titleY, Render2D.getWaveColor(UiMath.getAccentColor(), 10, 10 + charIndex));
                charX += Prestige.fontManager.getDefaultFont().getTextWidth(charText);
            }
        }

        float rowY = bottomAligned ? headerY - 3.0F - rowHeight : headerY + headerHeight + 3.0F;

        for (PotionDisplayEntry rowDisplay : (Iterable<PotionDisplayEntry>)(this.effectEntries.values())) {
            if (!(rowDisplay.visibility < 0.02F)) {
                float rowVisibility = rowDisplay.visibility;
                int rowAlpha = (int)(255.0F * rowVisibility);
                float slideOffset = (1.0F - rowVisibility) * 6.0F;
                if (rowVisibility > 0.7F) {
                    BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, rowY, this.animatedWidth, rowHeight, 5, 4.0F);
                }

                RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, rowY, this.animatedWidth, rowHeight, withAlpha(HudStyle.BACKGROUND_COLOR, (int)(HudStyle.BACKGROUND_COLOR.getAlpha() * rowVisibility)), 4.0F);
                String durationText = this.getDurationText(rowDisplay);
                float nameWidth = Prestige.fontManager.getDefaultFont().getTextWidth(rowDisplay.name) * 0.9F;
                float durationWidth = Prestige.fontManager.getDefaultFont().getTextWidth(durationText) * 0.9F;
                float textY = rowY + (rowHeight - 2.0F - fontHeight * 0.9F) / 2.0F;
                float nameX;
                float durationX;
                if (rightAligned) {
                    nameX = panelX + this.animatedWidth - 5.5F - nameWidth - slideOffset;
                    durationX = panelX + 5.5F;
                } else {
                    nameX = panelX + 5.5F + slideOffset;
                    durationX = panelX + this.animatedWidth - 5.5F - durationWidth;
                }

                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, rowDisplay.name, nameX, textY, 0.9F, withAlpha(HudStyle.TEXT_COLOR, rowAlpha));
                if (rowDisplay.levelText != null) {
                    float badgeWidth = this.getLevelBadgeWidth(rowDisplay.levelText);
                    float badgeHeight = fontHeight * 0.65F + 1.5F;
                    float badgeX = rightAligned ? nameX - 3.0F - badgeWidth : nameX + nameWidth + 3.0F;
                    float badgeY = rowY + (rowHeight - 2.0F - badgeHeight) / 2.0F;
                    ShapeRenderer.drawRoundedRect(matrix, badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, withAlpha(rowDisplay.color, (int)(70.0F * rowVisibility)), new Vector4f(3.0F));
                    Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, rowDisplay.levelText, badgeX + 2.0F, badgeY + 0.75F, 0.65F, withAlpha(rowDisplay.color, rowAlpha));
                }

                int secondsLeft = rowDisplay.infinite ? Integer.MAX_VALUE : rowDisplay.duration / 20;
                Color durationColor;
                if (secondsLeft <= 5) {
                    durationColor = new Color(235, 64, 64, (int)(Math.min((int)(170.0 + 85.0 * Math.sin(System.currentTimeMillis() * 0.012)), 255) * rowVisibility));
                } else if (secondsLeft <= 10) {
                    durationColor = new Color(255, 190, 80, rowAlpha);
                } else {
                    durationColor = new Color(235, 235, 235, rowAlpha);
                }

                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, durationText, durationX, textY, 0.9F, durationColor);
                float computedProgress;
                if (rowDisplay.infinite) {
                    computedProgress = 1.0F;
                } else if (rowDisplay.maxDuration > 0) {
                    computedProgress = UiMath.clamp((float)rowDisplay.duration / rowDisplay.maxDuration, 0.0F, 1.0F);
                } else {
                    computedProgress = 0.0F;
                }

                float durationProgress = computedProgress;
                float barTop = rowY + rowHeight - 1.9F;
                float barBottom = rowY + rowHeight - 1.15F;
                float barFillWidth = (this.animatedWidth - 4.0F) * durationProgress;
                Color chosenBarColor;
                if (secondsLeft <= 5) {
                    chosenBarColor = withAlpha(new Color(235, 64, 64), (int)(200.0F * rowVisibility));
                } else {
                    chosenBarColor = withAlpha(rowDisplay.color, (int)(160.0F * rowVisibility));
                }

                Color barColor = chosenBarColor;
                ShapeRenderer.drawRect(matrix, panelX + 2.0F, barTop, panelX + this.animatedWidth - 2.0F, barBottom, new Color(255, 255, 255, (int)(18.0F * rowVisibility)));
                if (rightAligned) {
                    ShapeRenderer.drawRect(matrix, panelX + this.animatedWidth - 2.0F - barFillWidth, barTop, panelX + this.animatedWidth - 2.0F, barBottom, barColor);
                } else {
                    ShapeRenderer.drawRect(matrix, panelX + 2.0F, barTop, panelX + 2.0F + barFillWidth, barBottom, barColor);
                }

                rowY += (bottomAligned ? -1.0F : 1.0F) * (rowHeight + 3.0F) * rowVisibility;
            }
        }
    }

    private float getLevelBadgeWidth(String levelText) {
        return Prestige.fontManager.getDefaultFont().getTextWidth(levelText) * 0.65F + 4.0F;
    }

    private String getDurationText(PotionDisplayEntry display) {
        String text;
        if (display.infinite) {
            text = "INF";
        } else {
            text = this.formatDuration(display.duration);
        }

        return text;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(alpha, 255)));
    }

    private static Color brightenIfDark(Color color) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int luminance = red * 3 + green * 4 + blue >> 3;
        if (luminance < 90) {
            float lightenAmount = (90 - luminance) / 90.0F * 0.6F;
            red += (int)((255 - red) * lightenAmount);
            green += (int)((255 - green) * lightenAmount);
            blue += (int)((255 - blue) * lightenAmount);
        }

        return new Color(red, green, blue);
    }
}

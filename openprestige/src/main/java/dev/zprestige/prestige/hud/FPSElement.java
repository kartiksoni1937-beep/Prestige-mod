package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import java.awt.Color;
import org.joml.Matrix4f;

public class FPSElement extends HudElement {
    private GlTextureHandle iconTexture;
    private final float[] fpsHistory;
    private int historyIndex;
    private long lastSampleTime;
    private long lastFrameTime;
    private float smoothedFps;
    private float colorRed;
    private float colorGreen;
    private float colorBlue;
    private float lastWidth;

    public FPSElement() {
        super("FPS", "Show the client's fps");
        this.fpsHistory = new float[24];
        this.historyIndex = 0;
        this.lastSampleTime = 0L;
        this.lastFrameTime = 0L;
        this.smoothedFps = -1.0F;
        this.colorRed = -1.0F;
        this.lastWidth = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 23.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.iconTexture == null) {
            this.iconTexture = TextureLoader.loadTexture("assets/prestige/icons/hud/fps.png");
        }

        int currentFps = mc.getCurrentFps();
        long now = System.currentTimeMillis();
        float frameDelta = (float)(now - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = now;
        if (now - this.lastSampleTime >= 100L) {
            this.fpsHistory[this.historyIndex] = currentFps;
            this.historyIndex = (this.historyIndex + 1) % 24;
            this.lastSampleTime = now;
        }

        float lerpValue;
        if (this.smoothedFps < 0.0F) {
            lerpValue = currentFps;
        } else {
            lerpValue = this.smoothedFps;
            float lerpTarget = currentFps;
            lerpValue = UiMath.approach(lerpValue, lerpTarget, frameDelta * 0.5F);
        }

        this.smoothedFps = lerpValue;
        Color targetColor = getFpsColor(this.smoothedFps);
        if (this.colorRed < 0.0F) {
            this.colorRed = targetColor.getRed();
            this.colorGreen = targetColor.getGreen();
            this.colorBlue = targetColor.getBlue();
        } else {
            lerpValue = this.colorRed;
            float channelTarget = targetColor.getRed();
            this.colorRed = UiMath.approach(lerpValue, channelTarget, frameDelta);
            lerpValue = this.colorGreen;
            channelTarget = targetColor.getGreen();
            this.colorGreen = UiMath.approach(lerpValue, channelTarget, frameDelta);
            lerpValue = this.colorBlue;
            channelTarget = targetColor.getBlue();
            this.colorBlue = UiMath.approach(lerpValue, channelTarget, frameDelta);
        }

        Color fpsColor = new Color((int)this.colorRed, (int)this.colorGreen, (int)this.colorBlue);
        String fpsText = String.valueOf(currentFps);
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float valueWidth = Prestige.fontManager.getDefaultFont().getTextWidth(fpsText) * 1.0F;
        float unitWidth = Prestige.fontManager.getDefaultFont().getTextWidth("fps") * 0.65F;
        float valueHeight = fontHeight * 1.0F;
        float unitHeight = fontHeight * 0.65F;
        float iconSize = 7.5F;
        float iconSectionWidth = 5.5F + iconSize + 5.5F;
        float contentOffset = iconSectionWidth + 0.5F + 5.5F;
        float panelWidth = contentOffset + Math.max(valueWidth + 3.0F + unitWidth, 28.0F) + 5.5F;
        float panelHeight = HudStyle.getCardHeight(fontHeight);
        boolean rightAligned = this.isAnchoredRight();
        boolean inEditor = mc.currentScreen instanceof HudEditorScreen;
        if (rightAligned && this.lastWidth > 0.0F && !inEditor) {
            this.x = this.x - (panelWidth - this.lastWidth);
        }

        this.lastWidth = panelWidth;
        float panelX = this.x;
        float panelY = this.y;
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        Color accent = UiMath.getAccentColor();
        float iconX = rightAligned ? panelX + panelWidth - 5.5F - iconSize : panelX + 5.5F;
        float dividerX = rightAligned ? panelX + panelWidth - iconSectionWidth - 0.5F : panelX + iconSectionWidth;
        RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, iconX, panelY + panelHeight / 2.0F - iconSize / 2.0F, iconSize, iconSize, accent);
        ShapeRenderer.drawRect(matrix, dividerX, panelY + 3.0F, dividerX + 0.5F, panelY + panelHeight - 3.0F, HudStyle.DIVIDER_COLOR);
        float textY = panelY + 3.0F;
        float valueX;
        float unitX;
        if (rightAligned) {
            unitX = (panelX + panelWidth - contentOffset) - unitWidth;
            valueX = unitX - 3.0F - valueWidth;
        } else {
            valueX = panelX + contentOffset;
            unitX = valueX + valueWidth + 3.0F;
        }

        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, fpsText, valueX, textY, 1.0F, fpsColor);
        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, "fps", unitX, textY + (valueHeight - unitHeight) - 0.5F, 0.65F, HudStyle.SECONDARY_TEXT_COLOR);
        float graphBaseY = textY + valueHeight + 1.0F + 4.0F;
        float graphX = rightAligned ? panelX + 5.5F : panelX + contentOffset;
        float barWidth = ((rightAligned ? panelX + panelWidth - contentOffset : panelX + panelWidth - 5.5F) - graphX) / 24.0F;
        float graphMax = 60.0F;

        for (float sample : this.fpsHistory) {
            if (sample > graphMax) {
                graphMax = sample;
            }
        }

        for (int barIndex = 0; barIndex < 24; barIndex++) {
            float barSample = this.fpsHistory[(this.historyIndex + barIndex) % 24];
            if (!(barSample <= 0.0F)) {
                float barHeight = 0.5F + barSample / graphMax * 3.5F;
                float barX = graphX + barIndex * barWidth;
                Color barColor = getFpsColor(barSample);
                int barAlpha = barIndex == 23 ? 220 : 110;
                lerpValue = barX + 0.2F;
                ShapeRenderer.drawRect(matrix, lerpValue, graphBaseY - barHeight, barX + barWidth - 0.2F, graphBaseY, new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), barAlpha));
            }
        }

        this.setSize(panelWidth, panelHeight);
    }

    private static Color getFpsColor(float fps) {
        if (fps >= 120.0F) {
            return new Color(96, 220, 130);
        } else if (fps >= 60.0F) {
            return new Color(240, 200, 80);
        } else {
            return fps >= 30.0F ? new Color(245, 145, 60) : new Color(235, 75, 70);
        }
    }
}

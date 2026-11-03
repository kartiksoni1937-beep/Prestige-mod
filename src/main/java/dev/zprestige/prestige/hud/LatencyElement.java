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
import net.minecraft.client.network.PlayerListEntry;
import org.joml.Matrix4f;

public class LatencyElement extends HudElement {
    private GlTextureHandle iconTexture;
    private final float[] pingHistory;
    private int historyIndex;
    private long lastSampleTime;
    private long lastFrameTime;
    private float smoothedPing;
    private float colorRed;
    private float colorGreen;
    private float colorBlue;
    private float lastWidth;

    public LatencyElement() {
        super("Latency", "Show the client's latency");
        this.pingHistory = new float[24];
        this.historyIndex = 0;
        this.lastSampleTime = 0L;
        this.lastFrameTime = 0L;
        this.smoothedPing = -1.0F;
        this.colorRed = 96.0F;
        this.colorGreen = 220.0F;
        this.colorBlue = 130.0F;
        this.lastWidth = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(65.0F, 23.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.iconTexture == null) {
            this.iconTexture = TextureLoader.loadTexture("assets/prestige/icons/hud/latency.png");
        }

        long now = System.currentTimeMillis();
        float frameDelta = this.lastFrameTime == 0L ? 1.0F : (float)(now - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = now;
        int currentPing = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            PlayerListEntry playerEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (playerEntry != null) {
                currentPing = playerEntry.getLatency();
            }
        }

        if (now - this.lastSampleTime >= 100L) {
            this.pingHistory[this.historyIndex] = currentPing;
            this.historyIndex = (this.historyIndex + 1) % 24;
            this.lastSampleTime = now;
        }

        float lerpValue;
        if (this.smoothedPing < 0.0F) {
            lerpValue = currentPing;
        } else {
            lerpValue = this.smoothedPing;
            float lerpTarget = currentPing;
            lerpValue = UiMath.approach(lerpValue, lerpTarget, frameDelta);
        }

        this.smoothedPing = lerpValue;
        int displayedPing = Math.max(0, Math.round(this.smoothedPing));
        float pingValue = displayedPing;
        Color targetColor = getPingColor(pingValue);
        lerpValue = this.colorRed;
        float scratch = targetColor.getRed();
        this.colorRed = UiMath.approach(lerpValue, scratch, frameDelta);
        lerpValue = this.colorGreen;
        scratch = targetColor.getGreen();
        this.colorGreen = UiMath.approach(lerpValue, scratch, frameDelta);
        lerpValue = this.colorBlue;
        scratch = targetColor.getBlue();
        this.colorBlue = UiMath.approach(lerpValue, scratch, frameDelta);
        scratch = this.colorRed;
        Color pingColor = new Color(toColorChannel(scratch), toColorChannel(this.colorGreen), toColorChannel(this.colorBlue));
        String pingText = String.valueOf(displayedPing);
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float valueWidth = Prestige.fontManager.getDefaultFont().getTextWidth(pingText) * 1.0F;
        float unitWidth = Prestige.fontManager.getDefaultFont().getTextWidth("ms") * 0.65F;
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
        float iconX = rightAligned ? panelX + panelWidth - 5.5F - iconSize : panelX + 5.5F;
        float dividerX = rightAligned ? panelX + panelWidth - iconSectionWidth - 0.5F : panelX + iconSectionWidth;
        RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, iconX, panelY + panelHeight / 2.0F - iconSize / 2.0F, iconSize, iconSize, UiMath.getAccentColor());
        scratch = panelY + 3.0F;
        ShapeRenderer.drawRect(matrix, dividerX, scratch, dividerX + 0.5F, panelY + panelHeight - 3.0F, HudStyle.DIVIDER_COLOR);
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

        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, pingText, valueX, textY, 1.0F, pingColor);
        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, "ms", unitX, textY + (valueHeight - unitHeight) - 0.5F, 0.65F, HudStyle.MUTED_TEXT_COLOR);
        float graphBaseY = textY + valueHeight + 1.0F + 4.0F;
        float graphX = rightAligned ? panelX + 5.5F : panelX + contentOffset;
        float barWidth = ((rightAligned ? panelX + panelWidth - contentOffset : panelX + panelWidth - 5.5F) - graphX) / 24.0F;
        float graphMax = 100.0F;

        for (float sample : this.pingHistory) {
            if (sample > graphMax) {
                graphMax = sample;
            }
        }

        for (int barIndex = 0; barIndex < 24; barIndex++) {
            float barSample = this.pingHistory[(this.historyIndex + barIndex) % 24];
            if (!(barSample <= 0.0F)) {
                float barHeight = 0.5F + barSample / graphMax * 3.5F;
                float barX = graphX + barIndex * barWidth;
                Color barColor = getPingColor(barSample);
                int barAlpha = barIndex == 23 ? 220 : 110;
                lerpValue = barX + 0.2F;
                scratch = graphBaseY - barHeight;
                ShapeRenderer.drawRect(matrix, lerpValue, scratch, barX + barWidth - 0.2F, graphBaseY, new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), barAlpha));
            }
        }

        this.setSize(panelWidth, panelHeight);
    }

    private static Color getPingColor(float ping) {
        if (ping < 60.0F) {
            return new Color(96, 220, 130);
        } else if (ping < 120.0F) {
            return new Color(245, 205, 90);
        } else {
            return ping <= 200.0F ? new Color(245, 150, 70) : new Color(240, 85, 85);
        }
    }

    private static int toColorChannel(float value) {
        return value < 0.0F ? 0 : Math.min((int)value, 255);
    }
}

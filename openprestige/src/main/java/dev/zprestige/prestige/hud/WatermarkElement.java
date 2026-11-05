package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
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

public class WatermarkElement extends HudElement {
    private GlTextureHandle logoTexture;

    public WatermarkElement() {
        super("Watermark", "Show the client's watermark");
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 3.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.logoTexture == null) {
            this.logoTexture = TextureLoader.loadTexture("assets/prestige/icons/logo.png");
        }

        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float titleWidth = Prestige.fontManager.getDefaultFont().getTextWidth("Prestige Client") * 1.0F;
        float panelHeight = Math.max(HudStyle.getRowHeight(fontHeight, 1.0F), 18.0F);
        float panelWidth = 29.0F + titleWidth + 5.5F;
        float panelX = this.x;
        float panelY = this.y;
        boolean rightAligned = this.isAnchoredRight();
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        Color accent = UiMath.getAccentColor();
        int titleAlpha = 170 + (int)(50.0F * ((float)(Math.sin(System.currentTimeMillis() * 0.0031415926535897933) * 0.5 + 0.5)));
        Color titleColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), titleAlpha);
        float logoX = rightAligned ? panelX + panelWidth - 5.5F - 12.0F : panelX + 5.5F;
        float dividerX = rightAligned ? panelX + panelWidth - 23.0F - 0.5F : panelX + 23.0F;
        float logoY = panelY + (panelHeight - 12.0F) / 2.0F;
        RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.logoTexture, logoX, logoY, 12.0F, 12.0F, accent);
        ShapeRenderer.drawRect(matrix, dividerX, panelY + 3.0F, dividerX + 0.5F, panelY + panelHeight - 3.0F, HudStyle.DIVIDER_COLOR);
        float textX = rightAligned ? panelX + panelWidth - 29.0F - titleWidth : panelX + 29.0F;
        float textY = panelY + (panelHeight - fontHeight * 1.0F) / 2.0F;
        float firstWordWidth = Prestige.fontManager.getDefaultFont().getTextWidth("Prestige ") * 1.0F;
        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, "Prestige", textX, textY, 1.0F, titleColor);
        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, "Client", textX + firstWordWidth, textY, 1.0F, titleColor);
        this.setSize(panelWidth, panelHeight);
    }
}

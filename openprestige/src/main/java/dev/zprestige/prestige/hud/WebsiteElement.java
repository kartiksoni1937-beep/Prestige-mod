package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import org.joml.Matrix4f;

public class WebsiteElement extends HudElement {
    public WebsiteElement() {
        super("Website", "Display client website");
    }

    @Override
    public void resetPosition() {
        this.setPosition(150.0F, 3.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float panelWidth = 5.5F + (Prestige.fontManager.getDefaultFont().getTextWidth("prestigeclient.vip") * 1.0F) + 5.5F;
        float panelHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float panelX = this.x;
        float panelY = this.y;
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        Color accent = UiMath.getAccentColor();
        float pulse = (float)(Math.sin(System.currentTimeMillis() * 0.0031415926535897933) * 0.5 + 0.5);
        Color textColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150 + (int)(105.0F * pulse));
        float textX = panelX + 5.5F;
        float textY = panelY + (panelHeight - fontHeight * 1.0F) / 2.0F;
        Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, "prestigeclient.vip", textX, textY, 1.0F, textColor);
        this.setSize(panelWidth, panelHeight);
    }
}

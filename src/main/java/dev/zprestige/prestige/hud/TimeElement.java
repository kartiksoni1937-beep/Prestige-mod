package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;
import org.joml.Matrix4f;

public class TimeElement extends HudElement {
    private GlTextureHandle iconTexture;
    private static final long BLINK_PERIOD_MS = 1000L;

    public TimeElement() {
        super("Time", "Show the client's time");
    }

    @Override
    public void resetPosition() {
        this.setPosition(85.0F, 3.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (this.iconTexture == null) {
            this.iconTexture = TextureLoader.loadTexture("assets/prestige/icons/hud/clock.png");
        }

        MsdfFont font = Prestige.fontManager.getDefaultFont();
        LocalTime time = LocalTime.now();
        String hours = String.format("%02d", time.getHour());
        String minutes = String.format("%02d", time.getMinute());
        String seconds = String.format("%02d", time.getSecond());
        String dayName = LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String dayLabel = dayName.substring(0, 1).toUpperCase(Locale.ENGLISH) + dayName.substring(1).toLowerCase(Locale.ENGLISH);
        float maxDigitWidth = 0.0F;

        for (char digit = '0'; digit <= '9'; digit++) {
            maxDigitWidth = Math.max(maxDigitWidth, font.getTextWidth(String.valueOf(digit)));
        }

        float digitWidth = maxDigitWidth * 1.0F;
        float smallDigitWidth = maxDigitWidth * 0.65F;
        float colonWidth = font.getTextWidth(":") * 1.0F + 1.6F;
        float fontHeight = font.getLineHeight();
        float dayTextHeight = fontHeight * 0.65F;
        float clockTextHeight = fontHeight * 1.0F;
        float secondsTextHeight = fontHeight * 0.65F;
        float clockWidth = digitWidth * 2.0F + colonWidth + digitWidth * 2.0F + 3.0F + smallDigitWidth * 2.0F;
        float dayWidth = font.getTextWidth(dayLabel) * 0.65F;
        float contentWidth = Math.max(clockWidth, dayWidth);
        float iconSize = 7.5F;
        float iconSectionWidth = 5.5F + iconSize + 5.5F;
        float contentOffset = iconSectionWidth + 0.5F + 5.5F;
        float panelWidth = contentOffset + contentWidth + 5.5F;
        float panelHeight = 3.0F + dayTextHeight + 1.0F + clockTextHeight + 1.0F + 4.0F + 3.0F;
        float panelX = this.x;
        float panelY = this.y;
        boolean rightAligned = this.isAnchoredRight();
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        Color accent = UiMath.getAccentColor();
        float iconX = rightAligned ? panelX + panelWidth - 5.5F - iconSize : panelX + 5.5F;
        float dividerX = rightAligned ? panelX + panelWidth - iconSectionWidth - 0.5F : panelX + iconSectionWidth;
        RoundedTextureShader.drawTexture(batch, cameraData, matrix, this.iconTexture, iconX, panelY + panelHeight / 2.0F - iconSize / 2.0F, iconSize, iconSize, accent);
        ShapeRenderer.drawRect(matrix, dividerX, panelY + 3.0F, dividerX + 0.5F, panelY + panelHeight - 3.0F, HudStyle.DIVIDER_COLOR);
        float dayY = panelY + 3.0F;
        font.drawTextScaled(matrix, dayLabel, rightAligned ? panelX + panelWidth - contentOffset - dayWidth : panelX + contentOffset, dayY, 0.65F, accent);
        float clockY = dayY + dayTextHeight + 1.0F;
        float secondsY = clockY + (clockTextHeight - secondsTextHeight) - 0.5F;
        float clockX = rightAligned ? panelX + panelWidth - contentOffset - clockWidth : panelX + contentOffset;
        Color colonColor = new Color(255, 255, 255, 70 + (int)(170.0F * (0.5F + 0.5F * (float)Math.sin(((float)(System.currentTimeMillis() % BLINK_PERIOD_MS) / 1000.0F) * (float) (Math.PI * 2)))));
        drawCentered(font, matrix, hours, clockX, digitWidth * 2.0F, clockY, 1.0F, HudStyle.TEXT_COLOR);
        float cursorX = clockX + digitWidth * 2.0F;
        drawCentered(font, matrix, ":", cursorX, colonWidth, clockY, 1.0F, colonColor);
        cursorX += colonWidth;
        drawCentered(font, matrix, minutes, cursorX, digitWidth * 2.0F, clockY, 1.0F, HudStyle.TEXT_COLOR);
        cursorX += digitWidth * 2.0F + 3.0F;
        drawCentered(font, matrix, seconds, cursorX, smallDigitWidth * 2.0F, secondsY, 0.65F, HudStyle.MUTED_TEXT_COLOR);
        float progressTop = clockY + clockTextHeight + 1.0F;
        float progressBottom = progressTop + 1.5F;
        float progressLeft = rightAligned ? panelX + 5.5F : panelX + contentOffset;
        float progressRight = rightAligned ? panelX + panelWidth - contentOffset : panelX + panelWidth - 5.5F;
        ShapeRenderer.drawRect(matrix, progressLeft, progressTop, progressRight, progressBottom, HudStyle.DIVIDER_COLOR);
        float minuteProgress = (time.getSecond() + time.getNano() / 1.0E9F) / 60.0F;
        float pulse = (float)(Math.sin(System.currentTimeMillis() * 0.0031415926535897933) * 0.5 + 0.5);
        ShapeRenderer.drawRect(matrix, progressLeft, progressTop, progressLeft + (progressRight - progressLeft) * minuteProgress, progressBottom, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150 + (int)(70.0F * pulse)));
        this.setSize(panelWidth, panelHeight);
    }

    private static void drawCentered(MsdfFont font, Matrix4f matrix, String text, float slotX, float slotWidth, float textY, float scale, Color color) {
        font.drawTextScaled(matrix, text, slotX + (slotWidth - (font.getTextWidth(text) * scale)) / 2.0F, textY, scale, color);
    }
}

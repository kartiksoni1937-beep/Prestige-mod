package dev.zprestige.prestige.render;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.util.NotificationManager;
import java.awt.Color;
import org.joml.Matrix4f;

public class Notification {
    private static final java.util.Map<String, GlTextureHandle> ICON_CACHE = new java.util.HashMap<>();
    public final String title;
    public final String message;
    private final long durationMs;
    public final long createdAt;
    public float animation;
    public boolean appearing;
    public boolean dismissing;
    public boolean finished;
    private GlTextureHandle iconTexture;
    private final String iconPath;
    private final Color accentColor;

    public Notification(String heading, String body, long duration, NotificationType type) {
        this.title = heading;
        this.message = body;
        this.durationMs = duration;
        this.createdAt = System.currentTimeMillis();
        this.animation = 0.0F;
        this.appearing = true;
        this.dismissing = false;
        this.finished = false;
        this.iconPath = "assets/prestige/icons/notifications/" + type.name().toLowerCase() + ".png";
        this.accentColor = type.color;
    }

    public float render(Matrix4f matrix, BufferSource buffers, CameraMatrices context, float posX, float posY, float width, float height, boolean visible) {
        if (this.iconTexture == null) {
            this.iconTexture = ICON_CACHE.computeIfAbsent(this.iconPath, TextureLoader::loadTexture);
        }

        posX += (width + 30.0F) * (1.0F - this.animation);
        if (this.appearing) {
            this.animation = UiMath.approach(this.animation, 1.0F, NotificationManager.animationDelta);
            if (this.animation >= 0.99F) {
                this.animation = 1.0F;
                this.appearing = false;
            }
        }

        if (this.dismissing) {
            this.animation = UiMath.approach(this.animation, 0.0F, NotificationManager.animationDelta * 2.0F);
            if (this.animation <= 0.01F) {
                this.animation = 0.0F;
                this.finished = true;
                return this.animation * (height + 10.0F);
            }
        }

        if (System.currentTimeMillis() - this.createdAt >= this.durationMs && !this.dismissing) {
            this.dismissing = true;
        }

        if (!visible) {
            return this.animation * (height + 10.0F);
        } else {
            BlurredRoundedRectShader.drawBlurredRect(buffers, context, matrix, posX, posY, width, height, 5, 5.0F);
            RoundedRectShader.drawRoundedRect(buffers, context, matrix, posX, posY, width, height, new Color(0.05490196F, 0.05490196F, 0.05490196F, 150.0F * this.animation / 255.0F), 5.0F);
            Render2D.drawTexture(posX + 5.0F, posY + 6.0F, 16.0F, 16.0F, matrix, this.iconTexture, UiMath.withAlpha(this.accentColor, this.animation));
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, this.title, posX + 30.0F, posY + 2.0F, 0.9F, UiMath.withAlpha(Color.WHITE, this.animation));
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, this.message, posX + 30.0F, posY + 12.0F, 0.8F, UiMath.withAlpha(new Color(150, 150, 150), this.animation));
            float timeProgress = 1.0F - Math.max(0.0F, (float)(this.durationMs - (System.currentTimeMillis() - this.createdAt))) / (float)this.durationMs;
            if (timeProgress < 1.0F) {
                Render2D.drawRoundedRect(matrix, posX + 2.0F, posY + height - 6.0F, posX + 8.0F + (width - 4.0F - (width - 4.0F) * timeProgress - 4.0F), posY + height - 1.0F, this.accentColor, 3.0F);
            }

            return this.animation * (height + 10.0F);
        }
    }
}

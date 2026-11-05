package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.RenderCrosshairEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import java.awt.Color;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.joml.Matrix4f;

public class CrosshairElement extends HudElement {
    private long lastFrameTime;
    private float hitAnimation;
    private static final long UNSET_TIME = 0L;

    public CrosshairElement() {
        super("Crosshair", "Modifies the crosshair to a custom one");
        this.lastFrameTime = UNSET_TIME;
        this.hitAnimation = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(mc.getWindow().getScaledWidth() / 2.0F, mc.getWindow().getScaledHeight() / 2.0F);
    }

    @Override
    public void render(dev.zprestige.prestige.render.BufferSource batch, dev.zprestige.prestige.data.CameraMatrices cameraData, Matrix4f matrix) {
        this.setSize(0.0F, 0.0F);
        float matrixScale = (float)Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02());
        this.setPosition(mc.getWindow().getScaledWidth() / 2.0F / matrixScale, mc.getWindow().getScaledHeight() / 2.0F / matrixScale);
        float frameDelta = (float)(System.currentTimeMillis() - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = System.currentTimeMillis();
        boolean targetHurt = mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget && livingTarget.hurtTime > 0;
        this.hitAnimation = UiMath.approach(this.hitAnimation, targetHurt ? 1.0F : 0.0F, targetHurt ? frameDelta * 3.0F : frameDelta);
        float pulse = ((float)Math.sin(System.currentTimeMillis() * 0.003) + 1.0F) * 0.5F;
        Color accent = UiMath.getAccentColor();
        Color crosshairColor = new Color(
            accent.getRed() + (int)((255 - accent.getRed()) * this.hitAnimation),
            accent.getGreen() + (int)((255 - accent.getGreen()) * this.hitAnimation),
            accent.getBlue() + (int)((255 - accent.getBlue()) * this.hitAnimation)
        );
        float centerX = this.x - 0.5F;
        float centerY = this.y - 0.5F;
        float ringRadius = 1.5F + pulse * 0.3F + this.hitAnimation * 1.25F;
        int ringAlpha = Math.min(255, 190 + (int)(65.0F * Math.max(pulse * 0.5F, this.hitAnimation)));
        Render2D.drawCircle(matrix, centerX, centerY, 0.9F, new Color(0, 0, 0, 90));
        Render2D.drawCircle(matrix, centerX, centerY, 0.5F, crosshairColor);
        Render2D.drawCircleOutline(matrix, centerX, centerY, ringRadius - 0.5F, new Color(0, 0, 0, 60));
        Render2D.drawCircleOutline(matrix, centerX, centerY, ringRadius + 0.5F, new Color(0, 0, 0, 60));
        Render2D.drawCircleOutline(matrix, centerX, centerY, ringRadius, new Color(crosshairColor.getRed(), crosshairColor.getGreen(), crosshairColor.getBlue(), ringAlpha));
    }

    @Subscribe
    public void onRenderCrosshair(RenderCrosshairEvent event) {
        event.cancel();
    }
}

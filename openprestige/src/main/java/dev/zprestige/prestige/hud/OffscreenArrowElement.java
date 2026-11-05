package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.ShapeRenderer;
import java.awt.Color;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class OffscreenArrowElement extends HudElement {
    private float arrowAngle;
    private float arrowVisibility;
    private boolean angleInitialized;
    private long lastFrameTime;

    public OffscreenArrowElement() {
        super("Offscreen Arrow", "Points to the closest player when they're off-screen");
        this.arrowAngle = 0.0F;
        this.arrowVisibility = 0.0F;
        this.angleInitialized = false;
        this.lastFrameTime = 0L;
    }

    @Override
    public void render(dev.zprestige.prestige.render.BufferSource batch, dev.zprestige.prestige.data.CameraMatrices cameraData, Matrix4f matrix) {
        this.setSize(0.0F, 0.0F);
        if (mc.player != null && mc.world != null) {
            long now = System.currentTimeMillis();
            float deltaSeconds = this.lastFrameTime == 0L ? 0.016F : (float)(now - this.lastFrameTime) / 1000.0F;
            this.lastFrameTime = now;
            deltaSeconds = Math.min(deltaSeconds, 0.05F);
            PlayerEntity targetPlayer = TargetSelector.getClosestEnemy();
            boolean offscreen = false;
            float targetAngle = 0.0F;
            if (targetPlayer != null) {
                Vec3d targetEyePos = targetPlayer.getEyePos();
                Vec3d selfEyePos = mc.player.getEyePos();
                double deltaX = targetEyePos.x - selfEyePos.x;
                double deltaY = targetEyePos.y - selfEyePos.y;
                double deltaZ = targetEyePos.z - selfEyePos.z;
                double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                double yawToTarget = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                double pitchToTarget = Math.toDegrees(-Math.atan2(deltaY, horizontalDistance));
                double relativeYaw = MathHelper.wrapDegrees(yawToTarget - mc.player.getYaw());
                double relativePitch = pitchToTarget - mc.player.getPitch();
                offscreen = !(Math.abs(relativeYaw) < 45.0 && Math.abs(relativePitch) < 45.0);
                targetAngle = (float)relativeYaw;
            }

            if (!this.angleInitialized && offscreen) {
                this.arrowAngle = targetAngle;
                this.angleInitialized = true;
            }

            float angleSmoothing = 1.0F - (float)Math.exp(-5.0F * deltaSeconds);
            float fadeSmoothing = 1.0F - (float)Math.exp(-(offscreen ? 10.0F : 28.0F) * deltaSeconds);
            float angleDelta = MathHelper.wrapDegrees(targetAngle - this.arrowAngle);
            this.arrowAngle = MathHelper.wrapDegrees(this.arrowAngle + angleDelta * angleSmoothing);
            this.arrowVisibility = this.arrowVisibility + ((offscreen ? 1.0F : 0.0F) - this.arrowVisibility) * fadeSmoothing;
            if (this.arrowVisibility < 0.01F) {
                this.angleInitialized = false;
            } else {
                float matrixScale = (float)Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02());
                float centerX = mc.getWindow().getScaledWidth() / 2.0F / matrixScale;
                float centerY = mc.getWindow().getScaledHeight() / 2.0F / matrixScale;
                double angleRadians = Math.toRadians(this.arrowAngle);
                float directionX = (float)Math.sin(angleRadians);
                float directionY = -((float)Math.cos(angleRadians));
                float perpendicularX = -directionY;
                Color accent = UiMath.getAccentColor();
                float pulse = 1.0F + 0.18F * (float)Math.sin(now * 0.012);
                int outerGlowColor = toArgbWithAlpha(accent, 0.1F * this.arrowVisibility * pulse);
                int innerGlowColor = toArgbWithAlpha(accent, 0.22F * this.arrowVisibility * pulse);
                drawArrowHead(matrix, centerX, centerY, directionX, directionY, perpendicularX, directionX, 18.4F, 13.8F, 7.4F, 6.9F, outerGlowColor);
                drawArrowHead(matrix, centerX, centerY, directionX, directionY, perpendicularX, directionX, 17.2F, 11.4F, 6.2F, 5.7F, innerGlowColor);
                drawArrowHead(matrix, centerX, centerY, directionX, directionY, perpendicularX, directionX, 16.0F, 9.0F, 5.0F, 4.5F, toArgbWithAlpha(accent, this.arrowVisibility));
            }
        }
    }

    private static void drawArrowHead(Matrix4f matrix, float centerX, float centerY, float directionX, float directionY, float perpendicularX, float perpendicularY, float tipDistance, float arrowLength, float halfWidth, float notchDepth, int color) {
        float tipX = centerX + directionX * tipDistance;
        float tipY = centerY + directionY * tipDistance;
        float backX = -directionX * arrowLength;
        float backY = -directionY * arrowLength;
        float wingX = perpendicularX * halfWidth;
        float wingY = perpendicularY * halfWidth;
        float leftWingX = tipX + backX + wingX;
        float leftWingY = tipY + backY + wingY;
        float rightWingX = tipX + backX - wingX;
        float rightWingY = tipY + backY - wingY;
        float notchX = tipX - directionX * notchDepth;
        float notchY = tipY - directionY * notchDepth;
        ShapeRenderer.drawTriangle(matrix, tipX, tipY, notchX, notchY, leftWingX, leftWingY, color);
        ShapeRenderer.drawTriangle(matrix, tipX, tipY, rightWingX, rightWingY, notchX, notchY, color);
    }

    private static int toArgbWithAlpha(Color color, float alpha) {
        int alphaByte = Math.round(255.0F * clamp01(alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alphaByte).getRGB();
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class TargetRenderer extends Module {
    public TargetRenderer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private GlTextureHandle squareTexture;
    private GlTextureHandle glowTexture;
    private float fadeProgress;
    private Vec3d smoothedPos;
    private float targetWidth;
    private float targetHeight;
    private float spinAngle = 0.0F;
    private long animationStartTime = System.currentTimeMillis();
    private long lastFrameTime = 0L;
    private long transitionStartTime = 0L;
    private boolean hadTarget = false;
    private float transitionStartProgress = 0.0F;

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        long now = System.currentTimeMillis();
        float deltaSeconds = this.lastFrameTime == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrameTime) / 1000.0F);
        this.lastFrameTime = now;
        this.spinAngle = (float)(now % 3600L) * 0.1F;
        PlayerEntity target = Prestige.targetTracker.getTarget();
        boolean hasTarget = target != null;
        if (hasTarget != this.hadTarget) {
            this.transitionStartTime = now;
            this.transitionStartProgress = this.fadeProgress;
            this.hadTarget = hasTarget;
        }

        long transitionElapsed = now - this.transitionStartTime;
        if (hasTarget) {
            float easedIn = smootherStep(Math.min(1.0F, (float)transitionElapsed / 850.0F));
            this.fadeProgress = this.transitionStartProgress + (1.0F - this.transitionStartProgress) * easedIn;
        } else {
            float easedOut = smootherStep(Math.min(1.0F, (float)transitionElapsed / 1100.0F));
            this.fadeProgress = this.transitionStartProgress * (1.0F - easedOut);
        }

        this.fadeProgress = clamp01(this.fadeProgress);
        if (target != null) {
            this.targetHeight = target.getHeight();
            this.targetWidth = target.getWidth();
            Vec3d lastPos = PositionUtil.getLastPos(target);
            float tickDelta = Render2D.getTickDelta();
            double interpolatedX = this.lerp((float)lastPos.x, (float)target.getX(), tickDelta);
            double centerY = this.lerp((float)lastPos.y, (float)target.getY(), tickDelta) + this.targetHeight / 2.0F;
            Vec3d targetCenter = new Vec3d(interpolatedX, centerY, this.lerp((float)lastPos.z, (float)target.getZ(), tickDelta));
            if (this.smoothedPos != null && !(this.smoothedPos.distanceTo(targetCenter) > 50.0)) {
                float followFactor = 1.0F - (float)Math.exp(-deltaSeconds * 14.0F);
                this.smoothedPos = new Vec3d(this.smoothedPos.x + (targetCenter.x - this.smoothedPos.x) * followFactor, this.smoothedPos.y + (targetCenter.y - this.smoothedPos.y) * followFactor, this.smoothedPos.z + (targetCenter.z - this.smoothedPos.z) * followFactor);
            } else {
                this.smoothedPos = targetCenter;
            }
        }

        if (!(this.fadeProgress < 0.01F) && this.smoothedPos != null && this.targetHeight != -1.0F) {
            switch (this.mode.getValue()) {
                case "Ghost":
                    this.renderGhost(event);
                    break;
                case "Square":
                    this.renderSquare(event);
            }
        } else {
            this.spinAngle = 0.0F;
        }
    }

    private static float smootherStep(float progress) {
        progress = clamp01(progress);
        return progress * progress * progress * (progress * (progress * 6.0F - 15.0F) + 10.0F);
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    public void renderGhost(Render3DEvent event) {
        if (this.smoothedPos != null) {
            if (this.glowTexture == null) {
                this.glowTexture = TextureLoader.loadTexture("assets/prestige/particles/glow.png");
            }

            Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, this.smoothedPos.x, this.smoothedPos.y, this.smoothedPos.z);
            Matrix4f matrix = new Matrix4f();
            matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
            Quaternionf billboardRotation = new Quaternionf().identity();
            billboardRotation.rotateY(-event.context.camera().getYaw() * (float) Math.PI / 180.0F);
            billboardRotation.rotateX(event.context.camera().getPitch() * (float) Math.PI / 180.0F);
            matrix.rotate(billboardRotation);
            float orbitPhase = (float)(
                (float)(System.currentTimeMillis() - this.animationStartTime) / 3000.0F + Math.sin((float)(System.currentTimeMillis() - this.animationStartTime) / 2500.0F) / 10.0
            );
            float sizeBias = -0.05F;
            float ringY = this.targetHeight / 4.0F;
            boolean invertFade = true;
            Color menuColor = Prestige.moduleManager.getMenu().getMenuColor().getValue();
            int baseRed = menuColor.getRed();
            int baseGreen = menuColor.getGreen();
            int baseBlue = menuColor.getBlue();

            for (int ring = 0; ring < 4; ring++) {
                float startAngle = orbitPhase * 360.0F;
                float endAngle = startAngle + 150.0F;

                for (float angle = startAngle; angle < startAngle + 100.0F; angle++) {
                    float arcProgress = inverseLerp(angle, startAngle - 90.0F, endAngle);
                    float radius = this.targetWidth * this.fadeProgress;
                    double angleRad = Math.toRadians(angle);
                    double wave = Math.sin(angleRad * 1.5);
                    double cosAngle = Math.cos(angleRad);
                    double sinAngle = Math.sin(angleRad);
                    double waveY = ringY + wave * 0.25;
                    float baseSize = (0.15F * (Math.max(0.15F, invertFade ? arcProgress : (1.0F - arcProgress) * 1.5F) + 0.45F)) * (1.7F + (0.5F - sizeBias) * 2.0F);
                    if (invertFade) {
                        arcProgress = 1.0F - arcProgress;
                    }

                    float quadSize = baseSize * (1.0F - (float)Math.pow(arcProgress, 5.0F)) * this.fadeProgress;
                    float alpha = baseSize * (1.0F - (float)Math.pow(arcProgress, 0.5F)) * this.fadeProgress;
                    float offsetX = (float)(cosAngle * radius);
                    float offsetY = (float)((waveY - 0.9F) * this.fadeProgress);
                    float offsetZ = (float)(sinAngle * radius);
                    float whiteBlend = Math.min(alpha * 2.5F, 1.0F);
                    int red = (int)(baseRed * (1.0F - whiteBlend) + 255.0F * whiteBlend);
                    int green = (int)(baseGreen * (1.0F - whiteBlend) + 255.0F * whiteBlend);
                    int blue = (int)(baseBlue * (1.0F - whiteBlend) + 255.0F * whiteBlend);
                    Color particleColor = new Color(red / 255.0F, green / 255.0F, blue / 255.0F, alpha);
                    RoundedTextureShader.drawWorldTextureGradient(event.renderer, event.context, matrix, this.glowTexture, offsetX - quadSize / 2.0F, offsetY - quadSize / 2.0F, offsetZ, offsetX + quadSize / 2.0F, offsetY + quadSize / 2.0F, offsetZ, particleColor, particleColor, particleColor, particleColor, 0.0F, true);
                }

                orbitPhase *= -1.35F;
                invertFade = !invertFade;
                ringY += this.targetHeight / 4.0F;
            }
        }
    }

    public void renderSquare(Render3DEvent event) {
        if (this.squareTexture == null) {
            this.squareTexture = TextureLoader.loadTexture("assets/prestige/icons/target.png");
        }

        Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, this.smoothedPos.x, this.smoothedPos.y, this.smoothedPos.z);
        Matrix4f matrix = new Matrix4f();
        matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
        Quaternionf rotation = new Quaternionf().identity();
        rotation.rotateY(-event.context.camera().getYaw() * (float) Math.PI / 180.0F);
        rotation.rotateX((float)(event.context.camera().getPitch() * (float) Math.PI / 180.0F + Math.sin(this.spinAngle / 50.0F) / 4.0));
        rotation.rotateZ((float)Math.toRadians(this.spinAngle));
        matrix.rotate(rotation);
        float halfSize = ((this.targetHeight / 2.0F + 0.5F) * 80.0F / 100.0F * this.fadeProgress * 0.85F) / 2.0F;
        Color bottomLeftColor = this.interpolateColor(Prestige.moduleManager.getMenu().getMenuColor().getValue().brighter().getRGB(), Prestige.moduleManager.getMenu().getMenuColor().getValue().getRGB(), (float)(Math.sin(System.currentTimeMillis() * 0.001) * 0.5 + 0.5));
        Color bottomRightColor = this.interpolateColor(Prestige.moduleManager.getMenu().getMenuColor().getValue().brighter().getRGB(), Prestige.moduleManager.getMenu().getMenuColor().getValue().getRGB(), (float)(Math.sin(System.currentTimeMillis() * 0.001 + 1.0) * 0.5 + 0.5));
        Color topRightColor = this.interpolateColor(Prestige.moduleManager.getMenu().getMenuColor().getValue().brighter().getRGB(), Prestige.moduleManager.getMenu().getMenuColor().getValue().getRGB(), (float)(Math.sin(System.currentTimeMillis() * 0.001 + 2.0) * 0.5 + 0.5));
        Color topLeftColor = this.interpolateColor(Prestige.moduleManager.getMenu().getMenuColor().getValue().brighter().getRGB(), Prestige.moduleManager.getMenu().getMenuColor().getValue().getRGB(), (float)(Math.sin(System.currentTimeMillis() * 0.001 + 3.0) * 0.5 + 0.5));
        RoundedTextureShader.drawWorldTextureGradient(event.renderer, event.context, matrix, this.squareTexture, -halfSize, -halfSize, 0.0F, halfSize, halfSize, 0.0F, bottomLeftColor, bottomRightColor, topRightColor, topLeftColor, 0.0F, false);
    }

    public static float inverseLerp(float value, float start, float end) {
        return (value - start) / (end - start);
    }

    private float lerp(float start, float end, float delta) {
        return start + delta * (end - start);
    }

    private Color interpolateColor(int fromRgb, int toRgb, float progress) {
        return new Color(((int)((fromRgb >> 16 & 0xFF) * (1.0F - progress) + (toRgb >> 16 & 0xFF) * progress)) << 16 | ((int)((fromRgb >> 8 & 0xFF) * (1.0F - progress) + (toRgb >> 8 & 0xFF) * progress)) << 8 | ((int)((fromRgb & 0xFF) * (1.0F - progress) + (toRgb & 0xFF) * progress)));
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.math.TrailTracker;
import dev.zprestige.prestige.math.TrailMark;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class Trails extends Module {
    private BoolSetting selfOnly;
    private EnumSetting mode;
    private IntSetting duration;
    private FloatSetting size;
    private BoolSetting themeColor;
    private ColorSetting color;
    private static GlTextureHandle glowTexture;
    private Map trackers = new HashMap();
    private List marks = new ArrayList();

    public Trails() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.color.visibleWhen((Predicate<Color>)ignored -> !this.themeColor.getValue());
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            HashSet seenIds = new HashSet();
            String currentMode = this.mode.getValue();

            double minSpacing = switch (currentMode) {
                case "Particles" -> 0.25;
                case "Ring" -> 0.65;
                case "Footsteps" -> 0.75;
                default -> 0.5;
            };
            int lifetimeMs = this.duration.getValue();

            for (PlayerEntity player : mc.world.getPlayers()) {
                int entityId = player.getId();
                seenIds.add(entityId);
                if ((!this.selfOnly.getValue() || player == mc.player) && player.isOnGround()) {
                    TrailTracker tracker = (TrailTracker)this.trackers.get(entityId);
                    Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
                    if (tracker == null) {
                        tracker = new TrailTracker();
                        tracker.lastPos = currentPos;
                        this.trackers.put(entityId, tracker);
                    } else {
                        double deltaX = currentPos.x - tracker.lastPos.x;
                        double deltaZ = currentPos.z - tracker.lastPos.z;
                        if (!(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) < minSpacing)) {
                            float yaw = player.getYaw();
                            switch (currentMode) {
                                case "Footsteps":
                                    boolean leftFoot = tracker.leftFoot;
                                    double yawRadians = Math.toRadians(yaw);
                                    double footOffset = 0.18;
                                    double offsetX = Math.cos(yawRadians) * footOffset * (leftFoot ? -1 : 1);
                                    double offsetZ = Math.sin(yawRadians) * footOffset * (leftFoot ? -1 : 1);
                                    Vec3d footPos = new Vec3d(currentPos.x + offsetX, currentPos.y, currentPos.z + offsetZ);
                                    this.marks.add(new TrailMark(footPos, currentMode, lifetimeMs, yaw, leftFoot, null));
                                    tracker.leftFoot = !leftFoot;
                                    break;
                                case "Particles":
                                    for (int particleIndex = 0; particleIndex < 4; particleIndex++) {
                                        double angle = Math.random() * Math.PI * 2.0;
                                        double horizontalSpeed = 0.025 + Math.random() * 0.05;
                                        Vec3d velocity = new Vec3d(Math.cos(angle) * horizontalSpeed, 0.05 + Math.random() * 0.05, Math.sin(angle) * horizontalSpeed);
                                        this.marks.add(new TrailMark(currentPos, currentMode, lifetimeMs, yaw, false, velocity));
                                    }
                                    break;
                                default:
                                    this.marks.add(new TrailMark(currentPos, currentMode, lifetimeMs, yaw, false, null));
                            }

                            tracker.lastPos = currentPos;
                        }
                    }
                }
            }

            this.trackers.keySet().retainAll(seenIds);
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        long now = System.currentTimeMillis();
        this.marks.removeIf(candidate -> now - ((dev.zprestige.prestige.math.TrailMark)candidate).spawnTime >= ((dev.zprestige.prestige.math.TrailMark)candidate).lifetimeMs);
        if (!this.marks.isEmpty()) {
            Color baseColor = this.themeColor.getValue() ? UiMath.getAccentColor() : this.color.getValue();
            Color darkColor = baseColor.darker();
            Color brightColor = baseColor.brighter();
            float sizeScale = this.size.getValue();

            for (TrailMark mark : (Iterable<TrailMark>)(this.marks)) {
                float progress = (float)(now - mark.spawnTime) / mark.lifetimeMs;
                if (!(progress < 0.0F) && !(progress > 1.0F)) {
                    float fadeAlpha = 1.0F - smootherStep(progress);
                    Color primaryColor;
                    Color secondaryColor;
                    if (mark.mode.equals("Footsteps")) {
                        primaryColor = baseColor;
                        secondaryColor = lightenColor(baseColor, 0.3F);
                    } else {
                        primaryColor = blendColors(darkColor, brightColor, mark.colorBlend);
                        secondaryColor = blendColors(primaryColor, brightColor, 0.6F);
                    }

                    switch (mark.mode) {
                        case "Footsteps":
                            this.renderFootstep(event, mark, primaryColor, secondaryColor, fadeAlpha, sizeScale);
                            break;
                        case "Ring":
                            this.renderRing(event, mark, primaryColor, secondaryColor, fadeAlpha, sizeScale, progress);
                            break;
                        case "Particles":
                            this.renderParticle(event, mark, primaryColor, fadeAlpha, sizeScale, progress);
                    }
                }
            }
        }
    }

    private void renderFootstep(Render3DEvent event, TrailMark mark, Color glowColor, Color coreColor, float alpha, float scale) {
        loadGlowTexture();
        Vec3d raisedPos = mark.pos.add(0.0, 0.02, 0.0);
        Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, raisedPos.x, raisedPos.y, raisedPos.z);
        Matrix4f matrix = new Matrix4f();
        matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
        matrix.rotateY(-mark.yawRadians);
        matrix.rotateX((float)Math.toRadians(90.0));
        matrix.scale(0.04F);
        float halfWidth = 4.0F * scale;
        float halfLength = 7.5F * scale;
        float glowHalfWidth = halfWidth * 1.55F;
        float glowHalfLength = halfLength * 1.4F;
        RoundedTextureShader.drawWorldTexture(event.renderer, event.context, matrix, glowTexture, -glowHalfWidth, -glowHalfLength, 0.0F, glowHalfWidth, glowHalfLength, 0.0F, scaleAlpha(glowColor, alpha * 0.45F), 0.0F);
        RoundedTextureShader.drawWorldTexture(event.renderer, event.context, matrix, glowTexture, -halfWidth, -halfLength, 0.0F, halfWidth, halfLength, 0.0F, scaleAlpha(coreColor, alpha), 0.0F);
    }

    private void renderRing(Render3DEvent event, TrailMark mark, Color glowColor, Color ringColor, float alpha, float scale, float progress) {
        float radius = (0.05F + 0.55F * easeOutQuad(progress)) * scale;
        ShapeRenderer.drawGlowRing(event.context, event.renderer, (float)mark.pos.x, (float)mark.pos.y + 0.01F, (float)mark.pos.z, radius, 0.018F, 0.12F, scaleAlpha(ringColor, alpha), scaleAlpha(glowColor, alpha));
    }

    private void renderParticle(Render3DEvent event, TrailMark mark, Color particleColor, float alpha, float scale, float progress) {
        loadGlowTexture();
        float ageTicks = mark.lifetimeMs * progress / 50.0F;
        Vec3d displacement = mark.velocity.multiply(ageTicks);
        this.drawGlowBillboard(event, mark.pos.add(displacement.x, displacement.y - (9.0E-4 * ageTicks * ageTicks) + 0.05, displacement.z), (1.6F - 0.5F * progress) * 2.5F * scale, scaleAlpha(particleColor, alpha));
    }

    private void drawGlowBillboard(Render3DEvent event, Vec3d pos, float quadSize, Color tint) {
        if (tint.getAlpha() > 0) {
            Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, pos.x, pos.y, pos.z);
            Matrix4f matrix = new Matrix4f();
            matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
            matrix.scale(0.04F);
            matrix.rotateY((float)Math.toRadians(-event.context.camera().getYaw()));
            matrix.rotateX((float)Math.toRadians(event.context.camera().getPitch()));
            RoundedTextureShader.drawWorldTexture(event.renderer, event.context, matrix, glowTexture, 0.0F, 0.0F, 0.0F, quadSize, quadSize, 0.0F, tint, 0.0F);
        }
    }

    private static void loadGlowTexture() {
        if (glowTexture == null) {
            glowTexture = TextureLoader.loadTexture("assets/prestige/particles/glow.png");
        }
    }

    @Override
    public void onDisable() {
        this.marks.clear();
        this.trackers.clear();
    }

    private static float smootherStep(float progress) {
        if (progress <= 0.0F) {
            return 0.0F;
        } else {
            return progress >= 1.0F ? 1.0F : progress * progress * progress * (progress * (progress * 6.0F - 15.0F) + 10.0F);
        }
    }

    private static float easeOutQuad(float progress) {
        return 1.0F - (1.0F - progress) * (1.0F - progress);
    }

    private static Color scaleAlpha(Color baseColor, float alphaScale) {
        int alpha = Math.max(0, Math.min(255, (int)(baseColor.getAlpha() * alphaScale)));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }

    private static Color lightenColor(Color baseColor, float amount) {
        if (amount < 0.0F) {
            amount = 0.0F;
        }

        if (amount > 1.0F) {
            amount = 1.0F;
        }

        int red = (int)(baseColor.getRed() + (255 - baseColor.getRed()) * amount);
        int green = (int)(baseColor.getGreen() + (255 - baseColor.getGreen()) * amount);
        int blue = (int)(baseColor.getBlue() + (255 - baseColor.getBlue()) * amount);
        return new Color(clampColorComponent(red), clampColorComponent(green), clampColorComponent(blue), baseColor.getAlpha());
    }

    private static Color blendColors(Color from, Color to, float progress) {
        if (progress < 0.0F) {
            progress = 0.0F;
        }

        if (progress > 1.0F) {
            progress = 1.0F;
        }

        float inverse = 1.0F - progress;
        int red = (int)(from.getRed() * inverse + to.getRed() * progress);
        int green = (int)(from.getGreen() * inverse + to.getGreen() * progress);
        int blue = (int)(from.getBlue() * inverse + to.getBlue() * progress);
        int alpha = (int)(from.getAlpha() * inverse + to.getAlpha() * progress);
        return new Color(clampColorComponent(red), clampColorComponent(green), clampColorComponent(blue), clampColorComponent(alpha));
    }

    private static int clampColorComponent(int component) {
        return component < 0 ? 0 : (component > 255 ? 255 : component);
    }
}

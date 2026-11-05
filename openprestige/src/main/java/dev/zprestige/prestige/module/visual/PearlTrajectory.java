package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.math.PearlLandingMarker;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PearlTrajectory extends Module {
    private BoolSetting renderLine;
    private BoolSetting themeColor;
    private ColorSetting lineColor;
    private BoolSetting landingTag;
    private FloatSetting tagScale;
    private BoolSetting tagBackground;
    private ColorSetting tagBgColor;
    private static final Identifier PEARL_TEXTURE_ID = Identifier.of("minecraft", "textures/item/ender_pearl.png");
    private static GlTextureHandle pearlTexture;
    private Map landingMarkers = new HashMap();
    private Map spawnTimes = new HashMap();
    private Map predictedPaths = new HashMap();

    public PearlTrajectory() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.lineColor
            .visibleWhen((Predicate<Color>)ignored -> this.renderLine.getValue() && !this.themeColor.getValue());
        this.themeColor.visibleWhen((Predicate<Boolean>)ignored -> this.renderLine.getValue());
        this.tagBackground.visibleWhen((Predicate<Boolean>)ignored -> this.landingTag.getValue());
        this.tagBgColor
            .visibleWhen((Predicate<Color>)ignored -> this.landingTag.getValue() && this.tagBackground.getValue());
        this.tagScale.visibleWhen((Predicate<Float>)ignored -> this.landingTag.getValue());
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            this.predictedPaths.clear();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl) {
                    this.predictedPaths.put(pearl.getId(), this.simulatePearlPath(pearl));
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.predictedPaths.clear();
        this.landingMarkers.clear();
        this.spawnTimes.clear();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.world != null && mc.player != null) {
            this.landingMarkers.clear();
            long now = System.currentTimeMillis();
            HashSet activePearlIds = new HashSet();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl) {
                    int pearlId = pearl.getId();
                    activePearlIds.add(pearlId);
                    this.spawnTimes.putIfAbsent(pearlId, now);
                    ArrayList path = (ArrayList)this.predictedPaths.get(pearlId);
                    if (path == null) {
                        path = this.simulatePearlPath(pearl);
                        this.predictedPaths.put(pearlId, path);
                    }

                    if (path.size() >= 2) {
                        float flightSeconds = (path.size() - 1) / 20.0F;
                        float alpha = Math.min(1.0F, (float)(now - (Long)this.spawnTimes.get(pearlId)) / 1000.0F) * Math.min(1.0F, flightSeconds / 1.0F);
                        if (this.renderLine.getValue() && alpha > 0.01F) {
                            Color baseColor = this.themeColor.getValue() ? UiMath.getAccentColor() : this.lineColor.getValue();
                            int lineAlpha = Math.max(0, Math.min(255, (int)(baseColor.getAlpha() * alpha)));
                            Color fadedLineColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), lineAlpha);
                            ShapeRenderer.drawPath(event.context, event.renderer, path, fadedLineColor);
                        }

                        Vec3d landingPos = (Vec3d)path.get(path.size() - 1);
                        this.landingMarkers.put(pearlId, new PearlLandingMarker(landingPos, flightSeconds, alpha));
                    }
                }
            }

            this.spawnTimes.keySet().retainAll(activePearlIds);
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (this.landingTag.getValue()) {
            if (this.canRenderOverlay()) {
                if (!this.landingMarkers.isEmpty()) {
                    boolean showBackground = this.tagBackground.getValue();
                    Color backgroundColor = showBackground ? this.tagBgColor.getValue() : null;
                    MsdfFont font = Prestige.fontManager.getDefaultFont();
                    float lineHeight = font.getLineHeight();
                    if (pearlTexture == null) {
                        pearlTexture = GlTextureHandle.wrapMinecraftTexture(PEARL_TEXTURE_ID);
                    }

                    float iconSize = 16.0F * 1.0F;
                    float spacing = 3.0F;
                    float dividerThickness = 0.5F;
                    float paddingX = 4.0F;
                    float paddingY = 3.0F;
                    float cornerRadius = 5.0F;
                    Color dividerColor = new Color(255, 255, 255, 50);

                    for (PearlLandingMarker marker : (Iterable<PearlLandingMarker>)(this.landingMarkers.values())) {
                        if (!(marker.alpha <= 0.01F)) {
                            Vec3d screenPos = Render2D.worldToScreen(marker.landingPos.add(0.0, 0.6, 0.0));
                            if (!(screenPos.z < 0.0) && !(screenPos.z >= 1.0)) {
                                String timeText = String.format("%.1fs", marker.flightSeconds);
                                float scale = this.tagScale.getValue();
                                float textWidth = font.getTextWidth(timeText);
                                float contentWidth = Math.max(iconSize, textWidth);
                                float contentHeight = iconSize + spacing + dividerThickness + spacing + lineHeight;
                                float panelWidth = contentWidth + 2.0F * paddingX;
                                float panelHeight = contentHeight + 2.0F * paddingY;
                                float centerX = (float)screenPos.x / scale;
                                float centerY = (float)screenPos.y / scale;
                                Matrix4f matrix = new Matrix4f().scale(scale, scale, scale);
                                float panelX = centerX - panelWidth / 2.0F;
                                float panelY = centerY - panelHeight / 2.0F;
                                float alpha = marker.alpha;
                                Color fadedBackground;
                                if (backgroundColor == null) {
                                    fadedBackground = null;
                                } else {
                                    fadedBackground = scaleAlpha(backgroundColor, alpha);
                                }

                                Color panelColor = fadedBackground;
                                Color fadedDivider = scaleAlpha(dividerColor, alpha);
                                Color textColor = new Color(255, 255, 255, (int)(255.0F * alpha));
                                if (showBackground) {
                                    if (alpha > 0.3F) {
                                        BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, 5, cornerRadius);
                                    }

                                    RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, panelColor, cornerRadius);
                                }

                                float iconX = centerX - iconSize / 2.0F;
                                float iconY = panelY + paddingY;
                                Render2D.drawTexture(iconX, iconY, iconSize, iconSize, matrix, pearlTexture, textColor);
                                float dividerLeft = panelX + paddingX;
                                float dividerRight = panelX + panelWidth - paddingX;
                                float dividerY = iconY + iconSize + spacing;
                                ShapeRenderer.drawRect(matrix, dividerLeft, dividerY, dividerRight, dividerY + dividerThickness, fadedDivider);
                                font.drawText(matrix, timeText, centerX - textWidth / 2.0F, dividerY + dividerThickness + spacing, textColor);
                            }
                        }
                    }
                }
            }
        }
    }

    private static Color scaleAlpha(Color color, float factor) {
        int alpha = Math.max(0, Math.min(255, (int)(color.getAlpha() * factor)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private ArrayList simulatePearlPath(EnderPearlEntity pearl) {
        ArrayList points = new ArrayList();
        dev.zprestige.prestige.combat.PearlTrajectorySimulatedPearl simulated = new dev.zprestige.prestige.combat.PearlTrajectorySimulatedPearl(
            mc.world, mc.player, Items.ENDER_PEARL.getDefaultStack()
        );
        simulated.setPosition(pearl.getX(), pearl.getY(), pearl.getZ());
        simulated.setVelocity(pearl.getVelocity());
        int steps = 0;

        while (!simulated.landed && steps++ < 400) {
            points.add(new Vec3d(simulated.getX(), simulated.getY(), simulated.getZ()));
            simulated.tick();
        }

        if (simulated.landed) {
            points.add(new Vec3d(simulated.getX(), simulated.getY(), simulated.getZ()));
        }

        return points;
    }

    private boolean canRenderOverlay() {
        return mc.currentScreen == null || mc.currentScreen instanceof ChatScreen;
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TwoDEspTarget;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.math.TwoDEspBox;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class TwoDESP extends Module {
    private BoolSetting showInvisibles;
    private EnumSetting style;
    private FloatSetting rounding;
    private FloatSetting lineWidth;
    private MultiSetting features;
    private FloatSetting fillAlpha;
    private FloatSetting glowStrength;
    private BoolSetting animated;
    private FloatSetting animationSpeed;
    private EnumSetting healthPosition;
    private BoolSetting healthText;
    private BoolSetting healthShine;
    private FloatSetting fadeSpeed;
    private IntSetting maxDistance;
    private IntSetting limit;
    private BoolSetting menuColor;
    private ColorSetting boxColor;
    private ColorSetting gradientColor;
    private ColorSetting friendColor;
    private ColorSetting healthHigh;
    private ColorSetting healthLow;
    private Frustum frustum;
    private long lastFrameTime = 0L;
    private long frameCounter = 0L;
    private Map visibleTargets = new LinkedHashMap();
    private Map animatedBoxes = new HashMap();

    public TwoDESP() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.healthText
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.features.isSelected("Health Bar")
            );
        this.healthHigh
            .visibleWhen(
                (Predicate<Color>)ignored -> this.features.isSelected("Health Bar")
            );
        this.healthShine
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.features.isSelected("Health Bar")
            );
        this.animationSpeed
            .visibleWhen(
                (Predicate<Float>)ignored -> this.features.isSelected("Gradient") && this.animated.getValue()
            );
        this.healthPosition
            .visibleWhen(
                (Predicate<String>)ignored -> this.features.isSelected("Health Bar")
            );
        this.fillAlpha
            .visibleWhen((Predicate<Float>)ignored -> this.features.isSelected("Fill"));
        this.animated
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.features.isSelected("Gradient")
            );
        this.boxColor.visibleWhen((Predicate<Color>)ignored -> !this.menuColor.getValue());
        this.gradientColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.features.isSelected("Gradient")
            );
        this.healthLow
            .visibleWhen(
                (Predicate<Color>)ignored -> this.features.isSelected("Health Bar")
            );
        this.glowStrength
            .visibleWhen((Predicate<Float>)ignored -> this.features.isSelected("Glow"));
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world == null || mc.player == null) {
            this.visibleTargets.clear();
            this.animatedBoxes.clear();
        } else {
            this.visibleTargets.clear();
            int maxRange = this.maxDistance.getValue();
            boolean includeInvisible = this.showInvisibles.getValue();
            ArrayList candidates = new ArrayList();

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && Prestige.botFilter.isValidTarget(player) && !player.isRemoved()) {
                    float distance = mc.player.distanceTo(player);
                    if (!(distance > maxRange) && (includeInvisible || !player.isInvisible())) {
                        TwoDEspTarget espTarget = new TwoDEspTarget();
                        espTarget.player = player;
                        espTarget.name = player.getName().getString();
                        espTarget.health = player.getHealth() + player.getAbsorptionAmount();
                        espTarget.maxHealth = Math.max(1.0F, player.getMaxHealth());
                        espTarget.width = player.getWidth();
                        espTarget.height = player.getHeight();
                        espTarget.distance = distance;
                        espTarget.friend = Prestige.friendManager.isFriend(espTarget.name);
                        candidates.add(espTarget);
                    }
                }
            }

            candidates.sort(Comparator.comparingDouble(candidate -> ((dev.zprestige.prestige.combat.TwoDEspTarget)candidate).distance));

            for (int index = 0; index < candidates.size() && index < this.limit.getValue(); index++) {
                TwoDEspTarget nearest = (TwoDEspTarget)candidates.get(index);
                this.visibleTargets.put(nearest.player.getUuid(), nearest);
            }
        }
    }

    @Subscribe
    public void onFrustumSetup(FrustumSetupEvent event) {
        this.frustum = event.getFrustum();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.world != null && mc.player != null) {
            if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) {
                this.frameCounter++;
                long now = System.currentTimeMillis();
                float deltaSeconds = this.lastFrameTime == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrameTime) / 1000.0F);
                this.lastFrameTime = now;
                float fadeStep = 1.0F - (float)Math.exp(-deltaSeconds * this.fadeSpeed.getValue());
                int screenWidth = mc.getWindow().getScaledWidth();
                int screenHeight = mc.getWindow().getScaledHeight();
                MsdfFont font = Prestige.fontManager.getDefaultFont();
                Matrix4f matrix = new Matrix4f();
                Color baseColor = this.menuColor.getValue()
                    ? Prestige.moduleManager.getMenu().getMenuColor().getValue()
                    : this.boxColor.getValue();
                Color secondaryColor = this.gradientColor.getValue();
                float gradientPhase = this.features.isSelected("Gradient") && this.animated.getValue()
                    ? (float)((Math.sin(now / 1000.0 * this.animationSpeed.getValue().floatValue()) + 1.0) * 0.5)
                    : 0.0F;

                for (Entry entry : (Iterable<Entry>)(this.visibleTargets.entrySet())) {
                    UUID uuid = (UUID)entry.getKey();
                    TwoDEspTarget espTarget = (TwoDEspTarget)entry.getValue();
                    PlayerEntity player = espTarget.player;
                    if (player != null && !player.isRemoved() && (this.frustum == null || this.frustum.isVisible(player.getBoundingBox()))) {
                        Vec3d renderPos = Render2D.getInterpolatedPos(player);
                        float upperBound = espTarget.width;
                        float[] screenBounds = this.projectBoundingBox(renderPos, upperBound, espTarget.height);
                        if (screenBounds != null) {
                            float rawCoord = screenBounds[0];
                            upperBound = screenWidth + 20;
                            float minX = clamp(rawCoord, -20.0F, upperBound);
                            rawCoord = screenBounds[1];
                            upperBound = screenHeight + 20;
                            float minY = clamp(rawCoord, -20.0F, upperBound);
                            rawCoord = screenBounds[2];
                            upperBound = screenWidth + 20;
                            float maxX = clamp(rawCoord, -20.0F, upperBound);
                            rawCoord = screenBounds[3];
                            upperBound = screenHeight + 20;
                            float maxY = clamp(rawCoord, -20.0F, upperBound);
                            if (!(maxX - minX < 3.0F) && !(maxY - minY < 3.0F)) {
                                TwoDEspBox box = (TwoDEspBox)this.animatedBoxes.get(uuid);
                                if (box == null) {
                                    box = new TwoDEspBox();
                                    box.alpha = 0.0F;
                                    this.animatedBoxes.put(uuid, box);
                                }

                                box.minX = minX;
                                box.minY = minY;
                                box.maxX = maxX;
                                box.maxY = maxY;
                                box.alpha = lerp(box.alpha, 1.0F, fadeStep);
                                box.lastFrame = this.frameCounter;
                                box.target = espTarget;
                                this.renderTarget(event.renderer, event.context, matrix, font, espTarget, box, baseColor, secondaryColor, gradientPhase, now);
                            }
                        }
                    }
                }

                Iterator boxIterator = this.animatedBoxes.entrySet().iterator();

                while (boxIterator.hasNext()) {
                    TwoDEspBox fadingBox = (TwoDEspBox)((Entry)boxIterator.next()).getValue();
                    if (fadingBox.lastFrame != this.frameCounter) {
                        fadingBox.alpha = lerp(fadingBox.alpha, 0.0F, fadeStep);
                        if (!(fadingBox.alpha < 0.02F) && fadingBox.target != null) {
                            this.renderTarget(event.renderer, event.context, matrix, font, fadingBox.target, fadingBox, baseColor, secondaryColor, gradientPhase, now);
                        } else {
                            boxIterator.remove();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.animatedBoxes.clear();
        this.visibleTargets.clear();
        this.lastFrameTime = 0L;
    }

    private void renderTarget(BufferSource buffers, CameraMatrices context, Matrix4f matrix, MsdfFont font, TwoDEspTarget espTarget, TwoDEspBox box, Color baseColor, Color secondaryColor, float gradientPhase, long now) {
        float alpha = clamp01(box.alpha);
        if (!(alpha < 0.02F)) {
            float minX = box.minX;
            float minY = box.minY;
            float maxX = box.maxX;
            float maxY = box.maxY;
            float boxWidth = maxX - minX;
            float boxHeight = maxY - minY;
            Color primaryColor = espTarget.friend ? this.friendColor.getValue() : baseColor;
            boolean useGradient = this.features.isSelected("Gradient");
            Color topColor = primaryColor;
            Color bottomColor = useGradient && !espTarget.friend ? secondaryColor : primaryColor;
            if (useGradient && this.animated.getValue() && !espTarget.friend) {
                Color animatedTop = lerpColor(primaryColor, secondaryColor, gradientPhase);
                Color animatedBottom = lerpColor(secondaryColor, primaryColor, gradientPhase);
                topColor = animatedTop;
                bottomColor = animatedBottom;
            }

            Color fadedTop = scaleAlpha(topColor, alpha);
            Color fadedBottom = scaleAlpha(bottomColor, alpha);
            float strokeWidth = this.lineWidth.getValue();
            float radius = Math.min(this.rounding.getValue(), Math.min(boxWidth, boxHeight) * 0.4F);
            if (radius < 0.0F) {
                radius = 0.0F;
            }

            if (this.features.isSelected("Glow")) {
                this.drawGlow(matrix, minX, minY, maxX, maxY, primaryColor, alpha, radius);
            }

            if (this.features.isSelected("Fill")) {
                Render2D.drawRoundedRect(matrix, minX, minY, maxX, maxY, scaleAlpha(primaryColor, alpha * this.fillAlpha.getValue()), radius);
            }

            switch (this.style.getValue()) {
                case "Box":
                    this.drawBox(matrix, minX, minY, maxX, maxY, fadedTop, fadedBottom, strokeWidth, radius);
                    break;
                case "Corners":
                    this.drawCorners(matrix, minX, minY, maxX, maxY, fadedTop, fadedBottom, strokeWidth);
                    break;
                case "Brackets":
                    this.drawBrackets(matrix, minX, minY, maxX, maxY, fadedTop, fadedBottom, strokeWidth);
            }

            if (this.features.isSelected("Health Bar")) {
                this.drawHealthBar(matrix, font, minX, minY, maxX, maxY, espTarget.health, espTarget.maxHealth, alpha, now);
            }

            if (this.features.isSelected("Name Tag")) {
                this.drawNameTag(buffers, context, matrix, font, minX, minY, maxX, espTarget.name, alpha);
            }

            if (this.features.isSelected("Distance")) {
                this.drawDistanceTag(buffers, context, matrix, font, minX, maxY, maxX, espTarget.distance, alpha);
            }
        }
    }

    private void drawBox(Matrix4f matrix, float minX, float minY, float maxX, float maxY, Color topColor, Color bottomColor, float strokeWidth, float radius) {
        Color outlineColor = averageColor(topColor, bottomColor);
        drawRoundedOutline(matrix, minX - 0.5F, minY - 0.5F, maxX + 0.5F, maxY + 0.5F, new Color(0, 0, 0, Math.round(80.0F * (outlineColor.getAlpha() / 255.0F))), radius + 0.5F, 1.0F);
        drawRoundedOutline(matrix, minX, minY, maxX, maxY, outlineColor, radius, strokeWidth);
    }

    private void drawCorners(Matrix4f matrix, float minX, float minY, float maxX, float maxY, Color topColor, Color bottomColor, float strokeWidth) {
        float cornerLength = clamp(Math.min(maxX - minX, maxY - minY) * 0.28F, 4.0F, 22.0F);
        float halfWidth = strokeWidth * 0.5F;
        Color shadowColor = new Color(0, 0, 0, Math.round(120.0F * (topColor.getAlpha() / 255.0F)));
        this.drawShadowedRect(matrix, minX - halfWidth, minY - halfWidth, minX + cornerLength, minY + halfWidth, topColor, shadowColor);
        this.drawShadowedRect(matrix, minX - halfWidth, minY - halfWidth, minX + halfWidth, minY + cornerLength, topColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - cornerLength, minY - halfWidth, maxX + halfWidth, minY + halfWidth, topColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - halfWidth, minY - halfWidth, maxX + halfWidth, minY + cornerLength, topColor, shadowColor);
        this.drawShadowedRect(matrix, minX - halfWidth, maxY - halfWidth, minX + cornerLength, maxY + halfWidth, bottomColor, shadowColor);
        this.drawShadowedRect(matrix, minX - halfWidth, maxY - cornerLength, minX + halfWidth, maxY + halfWidth, bottomColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - cornerLength, maxY - halfWidth, maxX + halfWidth, maxY + halfWidth, bottomColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - halfWidth, maxY - cornerLength, maxX + halfWidth, maxY + halfWidth, bottomColor, shadowColor);
    }

    private void drawBrackets(Matrix4f matrix, float minX, float minY, float maxX, float maxY, Color topColor, Color bottomColor, float strokeWidth) {
        float bracketLength = clamp((maxY - minY) * 0.35F, 5.0F, 26.0F);
        float halfWidth = strokeWidth * 0.5F;
        float centerY = (minY + maxY) * 0.5F;
        Color shadowColor = new Color(0, 0, 0, Math.round(120.0F * (topColor.getAlpha() / 255.0F)));
        this.drawShadowedRect(matrix, minX - halfWidth, minY - halfWidth, minX + halfWidth, minY + bracketLength, topColor, shadowColor);
        this.drawShadowedRect(matrix, minX - halfWidth, centerY - bracketLength * 0.4F, minX + halfWidth, centerY + bracketLength * 0.4F, averageColor(topColor, bottomColor), shadowColor);
        this.drawShadowedRect(matrix, minX - halfWidth, maxY - bracketLength, minX + halfWidth, maxY + halfWidth, bottomColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - halfWidth, minY - halfWidth, maxX + halfWidth, minY + bracketLength, topColor, shadowColor);
        this.drawShadowedRect(matrix, maxX - halfWidth, centerY - bracketLength * 0.4F, maxX + halfWidth, centerY + bracketLength * 0.4F, averageColor(topColor, bottomColor), shadowColor);
        this.drawShadowedRect(matrix, maxX - halfWidth, maxY - bracketLength, maxX + halfWidth, maxY + halfWidth, bottomColor, shadowColor);
    }

    private void drawShadowedRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, Color shadowColor) {
        Render2D.drawRect(matrix, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F, y2 + 0.5F, shadowColor);
        Render2D.drawRect(matrix, x1, y1, x2, y2, color);
    }

    private void drawGlow(Matrix4f matrix, float minX, float minY, float maxX, float maxY, Color color, float alpha, float radius) {
        float layers = 4.0F;
        float strength = this.glowStrength.getValue();

        for (int layer = 1; layer <= layers; layer++) {
            float progress = layer / layers;
            float expand = strength * progress;
            float layerAlpha = alpha * 0.14F * (1.0F - progress);
            Render2D.drawRoundedRect(matrix, minX - expand, minY - expand, maxX + expand, maxY + expand, new Color(color.getRed(), color.getGreen(), color.getBlue(), toAlphaByte(layerAlpha)), radius + expand);
        }
    }

    private static void drawRoundedOutline(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, float radius, float thickness) {
        ShapeRenderer.drawRoundedRectOutline(Render2D.currentBuffers, Render2D.currentContext, matrix, x1, y1, x2, y2, color, radius, thickness);
    }

    private void drawHealthBar(Matrix4f matrix, MsdfFont font, float minX, float minY, float maxX, float maxY, float health, float maxHealth, float alpha, long now) {
        float healthFraction = clamp01(health / maxHealth);
        Color highColor = this.healthHigh.getValue();
        Color lowColor = this.healthLow.getValue();
        Color fillColor = lerpColor(lowColor, highColor, healthFraction);
        Color backgroundColor = new Color(0, 0, 0, toAlphaByte(alpha * 0.63F));
        String position = this.healthPosition.getValue();
        float barThickness = 2.6F;
        float barGap = 3.0F;
        float barRadius = 1.6F;
        boolean vertical = position.equals("Left") || position.equals("Right");
        float barMinX;
        float barMinY;
        float barMaxX;
        float barMaxY;
        if (vertical) {
            if (position.equals("Left")) {
                barMaxX = minX - barGap;
                barMinX = barMaxX - barThickness;
            } else {
                barMinX = maxX + barGap;
                barMaxX = barMinX + barThickness;
            }

            barMinY = minY;
            barMaxY = maxY;
        } else {
            if (position.equals("Top")) {
                barMaxY = minY - barGap;
                barMinY = barMaxY - barThickness;
            } else {
                barMinY = maxY + barGap;
                barMaxY = barMinY + barThickness;
            }

            barMinX = minX;
            barMaxX = maxX;
        }

        Render2D.drawRoundedRect(matrix, barMinX - 0.6F, barMinY - 0.6F, barMaxX + 0.6F, barMaxY + 0.6F, backgroundColor, barRadius);
        if (vertical) {
            float fillHeight = (barMaxY - barMinY) * healthFraction;
            float fillTop = barMaxY - fillHeight;
            if (fillHeight > 1.0F) {
                ShapeRenderer.drawGradientRoundedRect(matrix, barMinX, fillTop, barMaxX, barMaxY, scaleAlpha(lowColor, alpha), scaleAlpha(lowColor, alpha), scaleAlpha(fillColor, alpha), scaleAlpha(fillColor, alpha), new Vector4f(barRadius));
            }

            if (this.healthShine.getValue() && fillHeight > 4.0F) {
                float shinePhase = (float)(now % 1600L / 1600.0);
                float shineCenterY = fillTop + (barMaxY - fillTop) * shinePhase;
                float shineHeight = Math.min(8.0F, fillHeight * 0.35F);
                Render2D.drawRect(matrix, barMinX, shineCenterY - shineHeight * 0.5F, barMaxX, shineCenterY + shineHeight * 0.5F, new Color(255, 255, 255, toAlphaByte(alpha * 0.28F * (1.0F - Math.abs(shinePhase - 0.5F) * 2.0F))));
            }
        } else {
            float fillWidth = (barMaxX - barMinX) * healthFraction;
            float fillRight = barMinX + fillWidth;
            if (fillWidth > 1.0F) {
                ShapeRenderer.drawGradientRoundedRect(matrix, barMinX, barMinY, fillRight, barMaxY, scaleAlpha(lowColor, alpha), scaleAlpha(fillColor, alpha), scaleAlpha(fillColor, alpha), scaleAlpha(lowColor, alpha), new Vector4f(barRadius));
            }

            if (this.healthShine.getValue() && fillWidth > 4.0F) {
                float sweepPhase = (float)(now % 1600L / 1600.0);
                float shineCenterX = barMinX + fillWidth * sweepPhase;
                float shineWidth = Math.min(8.0F, fillWidth * 0.35F);
                Render2D.drawRect(matrix, shineCenterX - shineWidth * 0.5F, barMinY, shineCenterX + shineWidth * 0.5F, barMaxY, new Color(255, 255, 255, toAlphaByte(alpha * 0.28F * (1.0F - Math.abs(sweepPhase - 0.5F) * 2.0F))));
            }
        }

        if (this.healthText.getValue()) {
            String hpText = String.valueOf(Math.round(health));
            Color textBackground = new Color(0, 0, 0, toAlphaByte(alpha * 0.55F));
            float textScale = 0.5F;
            float textWidth = font.getTextWidth(hpText) * textScale;
            float textHeight = font.getLineHeight() * textScale;
            float textY;
            float textX;
            if (vertical) {
                textX = (barMinX + barMaxX) * 0.5F - textWidth * 0.5F;
                textY = barMaxY + 2.0F;
            } else {
                textX = (barMinX + barMaxX) * 0.5F - textWidth * 0.5F;
                textY = position.equals("Top") ? barMinY - textHeight - 2.0F : barMaxY + 2.0F;
            }

            Render2D.drawRoundedRect(matrix, textX - 2.0F, textY - 1.0F, textX + textWidth + 2.0F, textY + textHeight + 1.0F, textBackground, 1.5F);
            font.drawTextScaled(matrix, hpText, textX, textY, textScale, scaleAlpha(new Color(240, 240, 245), alpha));
        }
    }

    private void drawNameTag(BufferSource buffers, CameraMatrices context, Matrix4f matrix, MsdfFont font, float minX, float minY, float maxX, String playerName, float alpha) {
        float textWidth = font.getTextWidth(playerName);
        float lineHeight = font.getLineHeight();
        float paddingX = 4.0F;
        float paddingY = 1.5F;
        float labelWidth = textWidth + paddingX * 2.0F;
        float labelLeft = ((minX + maxX) * 0.5F) - labelWidth * 0.5F;
        float labelRight = labelLeft + labelWidth;
        float labelBottom = minY - 3.0F;
        float labelTop = labelBottom - (lineHeight + paddingY * 2.0F);
        this.drawLabelBackground(buffers, context, matrix, labelLeft, labelTop, labelRight, labelBottom, alpha);
        font.drawText(matrix, playerName, labelLeft + paddingX, labelTop + paddingY, scaleAlpha(new Color(240, 240, 250), alpha));
    }

    private void drawDistanceTag(BufferSource buffers, CameraMatrices context, Matrix4f matrix, MsdfFont font, float minX, float maxY, float maxX, float distance, float alpha) {
        int wholeMeters = (int)distance;
        int tenths = (int)(distance * 10.0F) - wholeMeters * 10;
        if (tenths < 0) {
            tenths = -tenths;
        }

        String distanceText = wholeMeters + "." + tenths + "m";
        float textScale = 0.75F;
        float textWidth = font.getTextWidth(distanceText) * textScale;
        float textHeight = font.getLineHeight() * textScale;
        float paddingX = 3.5F;
        float paddingY = 1.5F;
        float labelWidth = textWidth + paddingX * 2.0F;
        float labelLeft = ((minX + maxX) * 0.5F) - labelWidth * 0.5F;
        float labelRight = labelLeft + labelWidth;
        float labelTop = maxY + 3.0F;
        this.drawLabelBackground(buffers, context, matrix, labelLeft, labelTop, labelRight, labelTop + textHeight + paddingY * 2.0F, alpha);
        font.drawTextScaled(matrix, distanceText, labelLeft + paddingX, labelTop + paddingY, textScale, scaleAlpha(new Color(200, 200, 220), alpha));
    }

    private void drawLabelBackground(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x1, float y1, float x2, float y2, float alpha) {
        float width = x2 - x1;
        float height = y2 - y1;
        if (this.features.isSelected("Blur Labels")) {
            BlurredRoundedRectShader.drawBlurredRect(buffers, context, matrix, x1, y1, width, height, 5, 2.5F);
        }

        RoundedRectShader.drawRoundedRect(buffers, context, matrix, x1, y1, width, height, new Color(16, 16, 22, toAlphaByte(alpha * 0.72F)), 2.5F);
    }

    private float[] projectBoundingBox(Vec3d pos, float entityWidth, float entityHeight) {
        double halfWidth = entityWidth * 0.5;
        double fullHeight = entityHeight;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int corner = 0; corner < 8; corner++) {
            Vec3d projected = Render2D.worldToScreen(new Vec3d(pos.x + ((corner & 1) == 0 ? -halfWidth : halfWidth), pos.y + ((corner & 2) == 0 ? 0.0 : fullHeight), pos.z + ((corner & 4) == 0 ? -halfWidth : halfWidth)));
            if (projected.z < 0.0 || projected.z >= 1.0) {
                return null;
            }

            float screenX = (float)projected.x;
            float screenY = (float)projected.y;
            if (screenX < minX) {
                minX = screenX;
            }

            if (screenX > maxX) {
                maxX = screenX;
            }

            if (screenY < minY) {
                minY = screenY;
            }

            if (screenY > maxY) {
                maxY = screenY;
            }
        }

        return new float[]{minX, minY, maxX, maxY};
    }

    private static Color scaleAlpha(Color color, float factor) {
        float baseAlpha = color.getAlpha();
        int alpha = Math.round(baseAlpha * clamp01(factor));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampByte(alpha));
    }

    private static Color lerpColor(Color startColor, Color endColor, float progress) {
        progress = clamp01(progress);
        int red = Math.round(startColor.getRed() + (endColor.getRed() - startColor.getRed()) * progress);
        int green = Math.round(startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * progress);
        int blue = Math.round(startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * progress);
        int alpha = Math.round(startColor.getAlpha() + (endColor.getAlpha() - startColor.getAlpha()) * progress);
        return new Color(clampByte(red), clampByte(green), clampByte(blue), clampByte(alpha));
    }

    private static Color averageColor(Color first, Color second) {
        return new Color(
            (first.getRed() + second.getRed()) / 2,
            (first.getGreen() + second.getGreen()) / 2,
            (first.getBlue() + second.getBlue()) / 2,
            (first.getAlpha() + second.getAlpha()) / 2
        );
    }

    private static float lerp(float current, float target, float factor) {
        return current + (target - current) * clamp01(factor);
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int toAlphaByte(float fraction) {
        int scaled = Math.round(fraction * 255.0F);
        return scaled < 0 ? 0 : (scaled > 255 ? 255 : scaled);
    }

    private static int clampByte(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}

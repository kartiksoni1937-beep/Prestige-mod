package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.JumpCircle;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.ShapeRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class JumpCircles extends Module {
    private BoolSetting selfOnly;
    private FloatSetting maxRadius;
    private IntSetting duration;
    private FloatSetting thickness;
    private FloatSetting glowDistance;
    private BoolSetting themeColor;
    private ColorSetting color;
    private Map lastOnGround = new HashMap();
    private Map lastY = new HashMap();
    private List circles = new ArrayList();
    private static final long MAX_ALPHA = 3161348516882678015L;

    public JumpCircles() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.color.visibleWhen((Predicate<Color>)ignored -> !this.themeColor.getValue());
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null) {
            HashSet seenIds = new HashSet();

            for (PlayerEntity player : mc.world.getPlayers()) {
                int entityId = player.getId();
                seenIds.add(entityId);
                boolean wasGrounded = (boolean)this.lastOnGround.getOrDefault(entityId, false);
                boolean onGround = player.isOnGround();
                Vec3d pos = this.getTrackedPos(player);
                double currentY = pos.y;
                double previousY = (double)this.lastY.getOrDefault(entityId, currentY);
                double deltaY = currentY - previousY;
                if ((!this.selfOnly.getValue() || player == mc.player) && wasGrounded && !onGround && deltaY > 0.05) {
                    this.circles.add(new JumpCircle(new Vec3d(pos.x, previousY, pos.z), this.duration.getValue()));
                }

                this.lastOnGround.put(entityId, onGround);
                this.lastY.put(entityId, currentY);
            }

            this.lastOnGround.keySet().retainAll(seenIds);
            this.lastY.keySet().retainAll(seenIds);
        }
    }

    private Vec3d getTrackedPos(PlayerEntity player) {
        if (player == mc.player) {
            return new Vec3d(player.getX(), player.getY(), player.getZ());
        } else {
            try {
                return player.getTrackedPosition().withDelta(0L, 0L, 0L);
            } catch (Throwable ignored) {
                return new Vec3d(player.getX(), player.getY(), player.getZ());
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        long now = System.currentTimeMillis();
        this.circles.removeIf(candidate -> now - ((dev.zprestige.prestige.math.JumpCircle)candidate).spawnTime >= ((dev.zprestige.prestige.math.JumpCircle)candidate).lifetimeMs);
        if (!this.circles.isEmpty()) {
            Color baseColor = this.themeColor.getValue() ? UiMath.getAccentColor() : this.color.getValue();
            Color brightColor = baseColor.brighter();
            float ringThickness = this.thickness.getValue();
            float glowSpread = this.glowDistance.getValue();
            float finalRadius = this.maxRadius.getValue();

            for (JumpCircle circle : (Iterable<JumpCircle>)(this.circles)) {
                float progress = (float)(now - circle.spawnTime) / circle.lifetimeMs;
                if (!(progress < 0.0F) && !(progress > 1.0F)) {
                    float radius = 0.1F + (finalRadius - 0.1F) * easeOutQuad(progress);
                    float alpha;
                    if (progress < 0.2F) {
                        alpha = progress / 0.2F;
                    } else {
                        alpha = 1.0F - (progress - 0.2F) / 0.8F;
                    }

                    alpha = Math.max(0.0F, Math.min(1.0F, alpha));
                    Color ringColor = scaleAlpha(brightColor, alpha);
                    Color glowColor = scaleAlpha(baseColor, alpha);
                    ShapeRenderer.drawGlowRing(event.context, event.renderer, (float)circle.pos.x, (float)circle.pos.y, (float)circle.pos.z, radius, ringThickness, glowSpread, ringColor, glowColor);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.circles.clear();
        this.lastOnGround.clear();
        this.lastY.clear();
    }

    private static float easeOutQuad(float progress) {
        return 1.0F - (1.0F - progress) * (1.0F - progress);
    }

    private static Color scaleAlpha(Color baseColor, float alphaScale) {
        int alpha = Math.max(0, Math.min((int)MAX_ALPHA, (int)(baseColor.getAlpha() * alphaScale)));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }
}

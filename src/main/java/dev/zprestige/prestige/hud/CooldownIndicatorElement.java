package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.CooldownProgress;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import org.joml.Matrix4f;

public class CooldownIndicatorElement extends HudElement {
    private AttackIndicator savedAttackIndicator;
    private boolean vanillaIndicatorHidden;
    private long lastFrameTime;
    private long stateChangeTime;
    private boolean cooldownActive;
    private float visibilityAtChange;
    private float visibility;
    private float smoothedProgress;
    private float lastProgress;
    private long readyFlashTime;

    public CooldownIndicatorElement() {
        super("Cooldown Indicator", "Themed cooldown bar - covers attack, projectiles, and bow/crossbow/trident charge");
        this.savedAttackIndicator = null;
        this.vanillaIndicatorHidden = false;
        this.lastFrameTime = 0L;
        this.stateChangeTime = 0L;
        this.cooldownActive = false;
        this.visibilityAtChange = 0.0F;
        this.visibility = 0.0F;
        this.smoothedProgress = 0.0F;
        this.lastProgress = 0.0F;
        this.readyFlashTime = -100000L;
    }

    @Override
    public void resetPosition() {
        if (mc.getWindow() != null) {
            float screenWidth = mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale();
            float screenHeight = mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale();
            this.setPosition(screenWidth / 2.0F - 14.0F, screenHeight / 2.0F + 14.0F);
        } else {
            this.setPosition(150.0F, 110.0F);
        }
    }

    @Override
    public void toggle() {
        super.toggle();
        if (this.isEnabled()) {
            this.hideVanillaIndicator();
        } else {
            this.restoreVanillaIndicator();
        }
    }

    private void hideVanillaIndicator() {
        if (mc.options != null && !this.vanillaIndicatorHidden) {
            try {
                this.savedAttackIndicator = mc.options.getAttackIndicator().getValue();
                mc.options.getAttackIndicator().setValue(AttackIndicator.OFF);
                this.vanillaIndicatorHidden = true;
            } catch (Throwable ignored) {
            }
        }
    }

    private void restoreVanillaIndicator() {
        if (mc.options != null && this.vanillaIndicatorHidden && this.savedAttackIndicator != null) {
            try {
                mc.options.getAttackIndicator().setValue(this.savedAttackIndicator);
            } catch (Throwable ignored) {
            }

            this.vanillaIndicatorHidden = false;
            this.savedAttackIndicator = null;
        }
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        if (!this.vanillaIndicatorHidden) {
            this.hideVanillaIndicator();
        }

        if (mc.getWindow() != null) {
            float screenWidth = mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale();
            float screenHeight = mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale();
            this.setPosition(screenWidth / 2.0F - 14.0F, screenHeight / 2.0F + 14.0F);
        } else {
            this.setPosition(150.0F, 110.0F);
        }

        this.setSize(28.0F, 3.0F);
        boolean inEditor = mc.currentScreen instanceof HudEditorScreen;
        if (mc.player != null && mc.world != null) {
            long now = System.currentTimeMillis();
            float frameSeconds = this.lastFrameTime == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrameTime) / 1000.0F);
            this.lastFrameTime = now;
            CooldownProgress progress = this.getActiveProgress();
            boolean active = progress != null && progress.progress < 0.999F;
            if (progress != null) {
                float smoothing = 1.0F - (float)Math.exp(-frameSeconds * 16.0F);
                this.smoothedProgress = this.smoothedProgress + (progress.progress - this.smoothedProgress) * smoothing;
            }

            if (active != this.cooldownActive) {
                this.visibilityAtChange = this.visibility;
                this.stateChangeTime = now;
                if (!active && this.lastProgress > 0.8F) {
                    this.readyFlashTime = now;
                }

                this.cooldownActive = active;
            }

            if (progress != null) {
                this.lastProgress = progress.progress;
            }

            long sinceChange = now - this.stateChangeTime;
            if (active) {
                float appearProgress = Math.min(1.0F, (float)sinceChange / 220.0F);
                this.visibility = this.visibilityAtChange + (1.0F - this.visibilityAtChange) * smootherStep(appearProgress);
            } else {
                float fadeProgress = Math.min(1.0F, (float)sinceChange / 350.0F);
                this.visibility = this.visibilityAtChange * (1.0F - smootherStep(fadeProgress));
            }

            this.visibility = clamp01(this.visibility);
            if (inEditor) {
                this.drawBar(batch, cameraData, matrix, 1.0F, Math.max(this.smoothedProgress, 0.5F), 0.0F);
            } else {
                float flashStrength = smootherStep(1.0F - clamp01((float)(now - this.readyFlashTime) / 280.0F));
                if (this.visibility > 0.01F) {
                    this.drawBar(batch, cameraData, matrix, this.visibility, this.smoothedProgress, flashStrength);
                }
            }
        } else {
            if (inEditor) {
                this.drawBar(batch, cameraData, matrix, 1.0F, 0.5F, 0.0F);
            }
        }
    }

    private void drawBar(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix, float alpha, float progress, float flash) {
        float radius = 1.5F;
        float barWidth = 28.0F * alpha;
        if (!(barWidth < 0.5F)) {
            float barX = this.x + (28.0F - barWidth) * 0.5F;
            Color backgroundColor = scaleAlpha(HudStyle.BACKGROUND_COLOR, alpha);
            if (alpha > 0.7F) {
                BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, barX, this.y, barWidth, 3.0F, 5, radius);
            }

            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX, this.y, barWidth, 3.0F, backgroundColor, radius);
            float trackWidth = barWidth - 1.0F;
            float fillHeight = 2.0F;
            float fillRadius = fillHeight * 0.5F;
            if (progress > 0.001F && trackWidth > 1.0F) {
                float clampedProgress = Math.min(1.0F, progress);
                Color fillColor = getProgressColor(clampedProgress);
                float fillWidth = trackWidth * clampedProgress;
                if (fillWidth > fillHeight) {
                    RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX + 0.5F, this.y + 0.5F, fillWidth, fillHeight, scaleAlpha(fillColor, alpha), fillRadius);
                    if (clampedProgress < 0.995F && fillWidth > 3.0F) {
                        Color tipColor = scaleAlpha(lerpColor(fillColor, Color.WHITE, 0.75F), alpha * 0.9F);
                        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX + 0.5F + fillWidth - 1.5F, this.y + 0.5F, 1.5F, fillHeight, tipColor, fillRadius);
                    }
                }
            }

            if (flash > 0.01F) {
                int flashAlpha = Math.min(255, (int)(170.0F * flash * alpha));
                if (flashAlpha > 2) {
                    RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX, this.y, barWidth, 3.0F, new Color(255, 255, 255, flashAlpha), radius);
                }
            }
        }
    }

    private static Color getProgressColor(float progress) {
        Color accent = UiMath.getAccentColor();
        float brightness = 0.55F + 0.45F * smootherStep(clamp01(progress / 0.75F));
        Color shadedColor = new Color((int)(accent.getRed() * brightness), (int)(accent.getGreen() * brightness), (int)(accent.getBlue() * brightness));
        float whiteBlend = smootherStep(clamp01((progress - 0.88F) / 0.12F));
        return lerpColor(shadedColor, Color.WHITE, whiteBlend * 0.65F);
    }

    private static Color lerpColor(Color startColor, Color endColor, float factor) {
        factor = clamp01(factor);
        return new Color(
            (int)(startColor.getRed() + (endColor.getRed() - startColor.getRed()) * factor),
            (int)(startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * factor),
            (int)(startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * factor)
        );
    }

    private CooldownProgress getActiveProgress() {
        float tickDelta = Render2D.getTickDelta();
        if (mc.player.isUsingItem()) {
            ItemStack activeStack = mc.player.getActiveItem();
            if (activeStack != null && !activeStack.isEmpty()) {
                if (activeStack.getItem() instanceof BowItem) {
                    CooldownProgress bowProgress = new CooldownProgress();
                    bowProgress.progress = Math.min(1.0F, (mc.player.getItemUseTime() + tickDelta) / 20.0F);
                    return bowProgress;
                }

                if (activeStack.getItem() instanceof CrossbowItem) {
                    CooldownProgress crossbowProgress = new CooldownProgress();
                    int pullTime = Math.max(1, CrossbowItem.getPullTime(activeStack, mc.player));
                    crossbowProgress.progress = Math.min(1.0F, (mc.player.getItemUseTime() + tickDelta) / pullTime);
                    return crossbowProgress;
                }

                if (activeStack.getItem() instanceof TridentItem) {
                    CooldownProgress tridentProgress = new CooldownProgress();
                    tridentProgress.progress = Math.min(1.0F, (mc.player.getItemUseTime() + tickDelta) / 10.0F);
                    return tridentProgress;
                }
            }
        }

        float itemCooldown = Math.max(this.getItemCooldown(mc.player.getMainHandStack(), tickDelta), this.getItemCooldown(mc.player.getOffHandStack(), tickDelta));
        if (itemCooldown > 0.001F) {
            CooldownProgress cooldownProgress = new CooldownProgress();
            cooldownProgress.progress = 1.0F - itemCooldown;
            return cooldownProgress;
        } else {
            if (this.isMeleeWeapon(mc.player.getMainHandStack())) {
                float attackProgress = mc.player.getAttackCooldownProgress(tickDelta);
                if (attackProgress < 1.0F) {
                    CooldownProgress attackCooldown = new CooldownProgress();
                    attackCooldown.progress = attackProgress;
                    return attackCooldown;
                }
            }

            return null;
        }
    }

    private boolean isMeleeWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            ? InventoryUtil.isSword(stack)
                || InventoryUtil.isAxe(stack)
                || InventoryUtil.isMace(stack)
                || stack.getItem() instanceof TridentItem
            : false;
    }

    private float getItemCooldown(ItemStack stack, float tickDelta) {
        if (stack != null && !stack.isEmpty()) {
            try {
                return mc.player.getItemCooldownManager().getCooldownProgress(stack, tickDelta);
            } catch (Throwable ignored) {
                return 0.0F;
            }
        } else {
            return 0.0F;
        }
    }

    private static float smootherStep(float value) {
        if (value <= 0.0F) {
            return 0.0F;
        } else {
            return value >= 1.0F ? 1.0F : value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
        }
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static Color scaleAlpha(Color color, float alphaScale) {
        int scaledAlpha = Math.max(0, Math.min(255, (int)(color.getAlpha() * alphaScale)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), scaledAlpha);
    }
}

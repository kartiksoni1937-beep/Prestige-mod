package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ToggleAnimation;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.type.Easing;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class TargetElement extends HudElement {
    private final ToggleAnimation showAnimation;
    private GlTextureHandle skinTexture;
    private Identifier skinTextureId;
    private String targetName;
    private float health;
    private float displayedHealth;
    private float healthFraction;
    private float displayedHealthFraction;
    private float damageTrailFraction;
    private float lastRawHealth;
    private float distance;
    private UUID targetUuid;
    private long lastDamageTime;
    private Iterable armorStacks;
    private ItemStack mainHandStack;
    private ItemStack offHandStack;
    private long lastFrameTime;
    private long frameTimeMs;

    public TargetElement() {
        super("Target", "Displays information about your current target");
        this.showAnimation = new ToggleAnimation(500.0F, false, Easing.BACK_IN_OUT);
        this.targetName = "";
        this.health = 0.0F;
        this.displayedHealth = 0.0F;
        this.healthFraction = 0.0F;
        this.displayedHealthFraction = 0.0F;
        this.damageTrailFraction = 0.0F;
        this.lastRawHealth = 0.0F;
        this.distance = 0.0F;
        this.lastDamageTime = 0L;
        this.mainHandStack = ItemStack.EMPTY;
        this.offHandStack = ItemStack.EMPTY;
        this.lastFrameTime = 0L;
        this.frameTimeMs = 0L;
    }

    @Override
    public void resetPosition() {
        float screenWidth = mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale();
        float screenHeight = mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale();
        this.setPosition(screenWidth / 2.0F - 65.0F, screenHeight / 2.0F + 80.0F);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        this.frameTimeMs = System.currentTimeMillis() - this.lastFrameTime;
        this.lastFrameTime = System.currentTimeMillis();
        PlayerEntity targetPlayer = mc.currentScreen instanceof HudEditorScreen ? mc.player : Prestige.targetTracker.getTarget();
        if (targetPlayer != null && (mc.getNetworkHandler() == null || targetPlayer.getUuid() == null || mc.getNetworkHandler().getPlayerListEntry(targetPlayer.getUuid()) == null)) {
            targetPlayer = null;
        }

        this.showAnimation.setState(!(mc.currentScreen instanceof HudEditorScreen) && targetPlayer != null);
        float panelWidth = 130.0F;
        float panelHeight = 40.0F;
        float scale = mc.currentScreen instanceof HudEditorScreen ? 1.0F : this.showAnimation.getProgress();
        if (scale > 0.01F) {
            Matrix4f scaledMatrix = new Matrix4f();
            scaledMatrix.translate(this.x + panelWidth / 2.0F, this.y + panelHeight / 2.0F, 0.0F);
            scaledMatrix.scale(scale, scale, scale);
            scaledMatrix.translate(-this.x - panelWidth / 2.0F, -this.y - panelHeight / 2.0F, 0.0F);
            scaledMatrix = matrix.mul(scaledMatrix, new Matrix4f());
            BlurredRoundedRectShader.drawBlurredRectCorners(batch, cameraData, scaledMatrix, this.x, this.y, panelWidth, panelHeight, 5, new Vector4f(4.0F));
            ShapeRenderer.drawRoundedRect(scaledMatrix, this.x, this.y, this.x + panelWidth, this.y + panelHeight, HudStyle.BACKGROUND_COLOR, new Vector4f(4.0F));
            if (targetPlayer != null) {
                Identifier newSkinId = mc.getNetworkHandler().getPlayerListEntry(targetPlayer.getUuid()).getSkinTextures().body().texturePath();
                if (!newSkinId.equals(this.skinTextureId)) {
                    this.skinTextureId = newSkinId;
                    this.skinTexture = GlTextureHandle.wrapMinecraftTexture(newSkinId);
                }

                this.targetName = targetPlayer.getName().getString();
                float rawHealth = targetPlayer.getHealth() + targetPlayer.getAbsorptionAmount();
                if (!Float.isFinite(rawHealth)) {
                    rawHealth = 0.0F;   // NaN/Infinity health (server-controlled) would make floorToDecimals throw
                }

                this.health = (float)Math.ceil(rawHealth);
                this.healthFraction = UiMath.clamp(rawHealth / 20.0F, 0.0F, 1.0F);
                this.distance = mc.player != null ? targetPlayer.distanceTo(mc.player) : 0.0F;
                if (!Float.isFinite(this.distance)) {
                    this.distance = 0.0F;
                }

                if (this.targetUuid == null || !this.targetUuid.equals(targetPlayer.getUuid())) {
                    this.targetUuid = targetPlayer.getUuid();
                    this.displayedHealth = this.health;
                    this.displayedHealthFraction = this.healthFraction;
                    this.damageTrailFraction = this.healthFraction;
                    this.lastDamageTime = 0L;
                } else if (rawHealth < this.lastRawHealth - 0.05F) {
                    this.lastDamageTime = System.currentTimeMillis();
                }

                this.lastRawHealth = rawHealth;
                ArrayList armorList = new ArrayList();

                for (EquipmentSlot slot : AttributeModifierSlot.ARMOR) {
                    if (!slot.getName().equals("body")) {
                        armorList.add(targetPlayer.getEquippedStack(slot));
                    }
                }

                Collections.reverse(armorList);
                this.armorStacks = armorList;
                this.mainHandStack = targetPlayer.getMainHandStack();
                this.offHandStack = targetPlayer.getOffHandStack();
            }

            long sinceDamage = System.currentTimeMillis() - this.lastDamageTime;
            if (this.lastDamageTime != 0L && sinceDamage < 350L) {
                int damageFlashAlpha = (int)((55.0F * (1.0F - (float)sinceDamage / 350.0F)) * UiMath.clamp(scale, 0.0F, 1.0F));
                ShapeRenderer.drawRoundedRect(scaledMatrix, this.x, this.y, this.x + panelWidth, this.y + panelHeight, new Color(235, 70, 70, damageFlashAlpha), new Vector4f(4.0F));
            }

            if (this.skinTexture != null) {
                Render2D.drawTextureRegion(this.x + 5.0F, this.y + 5.0F, 30.0F, 30.0F, 3.75F, 22.5F, 3.75F, 3.75F, 0.0F, scaledMatrix, this.skinTexture, ShapeRenderer.withAlpha(Color.WHITE, UiMath.clamp(scale, 0.0F, 1.0F)));
                Render2D.drawRoundedRectOutline(scaledMatrix, this.x + 5.0F, this.y + 5.0F, this.x + 35.0F, this.y + 35.0F, new Color(14, 14, 14, 140), 0.0F);
            }

            Prestige.fontManager.getDefaultFont().drawText(scaledMatrix, this.targetName, this.x + 40.0F, this.y + 3.0F, HudStyle.TEXT_COLOR);
            String distanceText = String.valueOf(UiMath.floorToDecimals(this.distance, 1)).replace(".0", "") + "m";
            float distanceTextWidth = Prestige.fontManager.getDefaultFont().getTextWidth(distanceText) * 0.75F;
            float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
            Prestige.fontManager.getDefaultFont().drawTextScaled(scaledMatrix, distanceText, this.x + panelWidth - 5.5F - distanceTextWidth, this.y + 3.0F + (fontHeight - fontHeight * 0.75F) * 0.5F, 0.75F, HudStyle.MUTED_TEXT_COLOR);
            this.displayedHealthFraction = UiMath.approach(this.displayedHealthFraction, this.healthFraction, (float)this.frameTimeMs * 0.008F);
            this.displayedHealth = UiMath.approach(this.displayedHealth, this.health, (float)this.frameTimeMs * 0.01F);
            if (this.healthFraction >= this.damageTrailFraction) {
                this.damageTrailFraction = this.healthFraction;
            } else if (System.currentTimeMillis() - this.lastDamageTime > 400L) {
                this.damageTrailFraction = UiMath.approach(this.damageTrailFraction, this.healthFraction, (float)this.frameTimeMs * 0.003F);
            }

            float barX = this.x + 40.0F;
            float barWidth = 65.0F;
            ShapeRenderer.drawRoundedRect(scaledMatrix, barX, this.y + 28.0F, barX + barWidth, this.y + 35.0F, new Color(255, 255, 255, 22), new Vector4f(1.5F));
            if (this.damageTrailFraction > this.displayedHealthFraction + 0.005F) {
                ShapeRenderer.drawRoundedRect(scaledMatrix, barX, this.y + 28.0F, barX + barWidth * this.damageTrailFraction, this.y + 35.0F, new Color(235, 85, 85, 130), new Vector4f(1.5F));
            }

            Color healthColor = this.getHealthColor(this.displayedHealthFraction);
            float fillWidth = Math.max(barWidth * this.displayedHealthFraction, 2.5F);
            Render2D.drawRoundedGradientRect(scaledMatrix, barX, this.y + 28.0F, barX + fillWidth, this.y + 35.0F, 1.5F, healthColor.darker(), healthColor.brighter(), healthColor.darker(), healthColor.brighter());
            String healthText = String.valueOf(UiMath.floorToDecimals(this.displayedHealth, 1)).replace(".0", "");
            Color healthTextColor = this.displayedHealthFraction > 0.6F ? HudStyle.TEXT_COLOR : healthColor;
            Prestige.fontManager.getDefaultFont().drawText(scaledMatrix, healthText, this.x + panelWidth - Prestige.fontManager.getDefaultFont().getTextWidth(healthText) - 5.5F, this.y + 26.0F, healthTextColor);
            if (this.armorStacks != null) {
                float itemOffsetX = -2.5F;

                for (ItemStack armorStack : (Iterable<ItemStack>)(this.armorStacks)) {
                    if (!armorStack.isEmpty()) {
                        Render2D.drawItem(armorStack, this.x + 40.5F + itemOffsetX, this.y + 13.0F, 0.8F, scaledMatrix);
                    }

                    itemOffsetX += 15.0F;
                }

                if (!this.mainHandStack.isEmpty()) {
                    if (Render2D.drawItem(this.mainHandStack, this.x + 40.5F + itemOffsetX, this.y + 13.0F, 0.8F, scaledMatrix)) {
                        itemOffsetX += 15.0F;
                    }
                }

                if (!this.offHandStack.isEmpty()) {
                    Render2D.drawItem(this.offHandStack, this.x + 40.5F + itemOffsetX, this.y + 13.0F, 0.8F, scaledMatrix);
                }
            }
        }

        this.setSize(panelWidth, panelHeight);
    }

    private float getHealthFraction(PlayerEntity player) {
        return UiMath.clamp((player.getHealth() + player.getAbsorptionAmount()) / 20.0F, 0.0F, 1.0F);
    }

    private Color getHealthColor(float fraction) {
        if (fraction > 0.6F) {
            return UiMath.getAccentColor();
        } else {
            return fraction > 0.3F ? new Color(240, 170, 60) : new Color(235, 75, 75);
        }
    }
}

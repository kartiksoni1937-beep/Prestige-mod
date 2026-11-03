package dev.zprestige.prestige.module.spear;

import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ToggleAnimation;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.type.Easing;
import java.awt.Color;
import java.util.Set;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class SpearDamageIndicator extends Module {
    public SpearDamageIndicator() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }
    private ToggleAnimation visibilityAnimation = new ToggleAnimation(500.0F, false, Easing.BACK_IN_OUT);
    private float displayedDamage = 0.0F;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Spear);
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        PlayerEntity target = (InventoryUtil.isHolding(Items.NETHERITE_SPEAR) && mc.player.isUsingItem()) ? TargetSelector.getClosestEnemy() : null;
        this.visibilityAnimation.setState(target != null);
        float animationProgress = this.visibilityAnimation.getProgress();
        if (!(animationProgress <= 0.01F)) {
            Vec3d lookDirection = mc.player.getRotationVector();
            double ownApproachSpeed = lookDirection.dotProduct(KineticWeaponComponent.getAmplifiedMovement(mc.player));
            float estimatedDamage = (float)mc.player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE) + MathHelper.floor(Math.max(0.0, ownApproachSpeed - (target == null ? 0.0 : lookDirection.dotProduct(KineticWeaponComponent.getAmplifiedMovement(target)))) / 2.0);
            this.displayedDamage = UiMath.approach(this.displayedDamage, estimatedDamage, Render2D.getTickDelta() / 5.0F);
            Matrix4f matrix = new Matrix4f();
            float panelWidth = 64.0F;
            float panelHeight = 20.0F;
            float panelX = mc.getWindow().getScaledWidth() / 2.0F - panelWidth / 2.0F;
            float panelY = mc.getWindow().getScaledHeight() / 2.0F + 30.0F;
            matrix.translate(panelX + panelWidth / 2.0F, panelY + panelHeight / 2.0F, 0.0F);
            matrix.scale(animationProgress, animationProgress, animationProgress);
            matrix.translate(-panelX - panelWidth / 2.0F, -panelY - panelHeight / 2.0F, 0.0F);
            BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, 5, 5.0F);
            RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, new Color(14, 14, 14, 150), 5.0F);
            String label = this.displayedDamage >= 20.0F ? "LETHAL" : "Damage: " + (int)this.displayedDamage + "HP";
            float labelWidth = Prestige.fontManager.getDefaultFont().getTextWidth(label);
            float barPadding = 7.0F;
            float damageFraction = MathHelper.clamp(this.displayedDamage / 20.0F, 0.0F, 1.0F);
            float barX = panelX + barPadding;
            float barY = panelY + panelHeight - 4.0F - 3.0F;
            float barWidth = panelWidth - barPadding * 2.0F;
            float fillWidth = barWidth * damageFraction;
            Color barBackgroundColor = new Color(0, 0, 0, 90);
            Color barFillColor = new Color((int)((1.0F - damageFraction) * 255.0F), (int)(damageFraction * 255.0F), 0, 220);
            RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, barX, barY, barWidth, 4.0F, barBackgroundColor, 2.0F);
            if (fillWidth > 0.0F) {
                RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, barX, barY, fillWidth, 4.0F, barFillColor, 2.0F);
            }

            float labelX = panelX + (panelWidth - labelWidth) / 2.0F;
            Prestige.fontManager.getDefaultFont().drawText(matrix, label, labelX, panelY + 3.0F, UiMath.getAccentColor());
        }
    }
}

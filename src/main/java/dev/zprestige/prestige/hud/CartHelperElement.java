package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class CartHelperElement extends HudElement {
    private static final Color READY_COLOR = new Color(80, 220, 120);
    private static final Color ALERT_COLOR = new Color(235, 64, 64);
    private static final Color BAR_TRACK_COLOR = new Color(255, 255, 255, 30);
    private static final Set RAIL_ITEMS = Set.of(Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);
    private String statusText;
    private boolean ready;
    private int crossbowState;
    private float crossbowCharge;
    private int cartCount;
    private final ItemStack[] iconStacks;
    private boolean iconsInitialized;

    public CartHelperElement() {
        super("Cart Helper", "Cart PvP at a glance — trigger readiness, crossbow charge, and TNT cart stock.");
        this.statusText = "Ready";
        this.ready = true;
        this.crossbowState = 0;
        this.crossbowCharge = 0.0F;
        this.cartCount = 0;
        this.iconStacks = new ItemStack[2];
        this.iconsInitialized = false;
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 340.0F);
    }

    private void initIconStacks() {
        if (!this.iconsInitialized) {
            this.iconStacks[0] = new ItemStack(Items.CROSSBOW);
            this.iconStacks[0].set(DataComponentTypes.ITEM_MODEL, Identifier.of("minecraft", "crossbow_standby"));
            this.iconStacks[1] = new ItemStack(Items.TNT_MINECART);
            this.iconsInitialized = true;
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            boolean hasRail = false;
            boolean hasBow = false;
            boolean crossbowCharged = false;
            boolean hasCrossbow = false;
            int cartTotal = 0;

            for (int slot = 0; slot <= 35; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (stack.getItem() == Items.TNT_MINECART) {
                    cartTotal += stack.getCount();
                }

                if (slot <= 8) {
                    if (RAIL_ITEMS.contains(stack.getItem())) {
                        hasRail = true;
                    }

                    if (stack.getItem() == Items.BOW) {
                        hasBow = true;
                    }

                    if (stack.getItem() == Items.CROSSBOW) {
                        hasCrossbow = true;
                        if (CrossbowItem.isCharged(stack)) {
                            crossbowCharged = true;
                        }
                    }
                }
            }

            ItemStack offhandStack = mc.player.getOffHandStack();
            if (offhandStack.getItem() == Items.TNT_MINECART) {
                cartTotal += offhandStack.getCount();
            }

            this.cartCount = cartTotal;
            boolean hasWeapon = hasBow || crossbowCharged;
            this.ready = hasRail && cartTotal > 0 && hasWeapon;
            this.statusText = !hasRail ? "No Rail" : (cartTotal == 0 ? "No Carts" : (!hasWeapon ? "No Weapon" : "Ready"));
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof CrossbowItem) {
                int pullTime = Math.max(1, CrossbowItem.getPullTime(mc.player.getActiveItem(), mc.player));
                this.crossbowState = 2;
                this.crossbowCharge = Math.min(1.0F, (mc.player.getItemUseTime() + Render2D.getTickDelta()) / pullTime);
            } else if (crossbowCharged) {
                this.crossbowState = 3;
                this.crossbowCharge = 1.0F;
            } else {
                this.crossbowState = hasCrossbow ? 1 : 0;
                this.crossbowCharge = 0.0F;
            }
        }
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        this.initIconStacks();
        String title = "Cart Helper";
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float headerHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float rowHeight = HudStyle.getRowHeight(fontHeight, 0.9F);
        float titleWidth = Prestige.fontManager.getDefaultFont().getTextWidth(title);
        boolean previewMode = mc.player == null || mc.world == null;
        String shownStatus = previewMode ? "Ready" : this.statusText;
        boolean shownReady = previewMode || this.ready;
        int shownCrossbowState = previewMode ? 2 : this.crossbowState;
        float shownCharge = previewMode ? 0.65F : this.crossbowCharge;
        int shownCartCount = previewMode ? 24 : this.cartCount;
        String crossbowText = shownCrossbowState == 3 ? "Ready" : (shownCrossbowState == 1 ? "Uncharged" : "None");
        String cartCountText = String.valueOf(shownCartCount);
        float panelWidth = 5.5F + titleWidth + 5.5F;
        panelWidth = Math.max(panelWidth, Math.max(12.5F + Prestige.fontManager.getDefaultFont().getTextWidth(shownStatus) * 0.9F + 5.5F, Math.max(18.1F + (shownCrossbowState == 2 ? 30.0F : Prestige.fontManager.getDefaultFont().getTextWidth(crossbowText) * 0.9F) + 5.5F, 18.1F + Prestige.fontManager.getDefaultFont().getTextWidth(cartCountText) * 0.9F + 5.5F)));
        float panelHeight = headerHeight + 3.0F + rowHeight * 3.0F + 3.0F;
        boolean rightAligned = this.isAnchoredRight();
        this.setSize(panelWidth, panelHeight);
        float panelX = this.x;
        float panelY = this.y;
        BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, panelX, panelY, panelWidth, panelHeight, HudStyle.BACKGROUND_COLOR, 4.0F);
        float titleY = panelY + 3.0F;
        float charX = rightAligned ? panelX + panelWidth - 5.5F - titleWidth : panelX + 5.5F;

        for (int charIndex = 0; charIndex < title.length(); charIndex++) {
            String charText = String.valueOf(title.charAt(charIndex));
            int waveIndex = rightAligned ? title.length() - 1 - charIndex : charIndex;
            Prestige.fontManager.getDefaultFont().drawText(matrix, charText, charX, titleY, Render2D.getWaveColor(UiMath.getAccentColor(), 10, 10 + waveIndex));
            charX += Prestige.fontManager.getDefaultFont().getTextWidth(charText);
        }

        float dividerY = panelY + headerHeight - 0.5F;
        ShapeRenderer.drawRect(matrix, panelX + 5.5F, dividerY, panelX + panelWidth - 5.5F, dividerY + 0.5F, HudStyle.DIVIDER_COLOR);
        int pulseAlpha = (int)Math.min(255.0, 170.0 + 85.0 * Math.sin(System.currentTimeMillis() * 0.012));
        float rowY = panelY + headerHeight + 3.0F;
        float rowTextY = rowY + (rowHeight - fontHeight * 0.9F) / 2.0F;
        float dotY = rowY + (rowHeight - 4.0F) / 2.0F;
        Color statusColor = shownReady ? READY_COLOR : new Color(ALERT_COLOR.getRed(), ALERT_COLOR.getGreen(), ALERT_COLOR.getBlue(), pulseAlpha);
        float statusWidth = Prestige.fontManager.getDefaultFont().getTextWidth(shownStatus) * 0.9F;
        if (rightAligned) {
            float mirroredDotX = panelX + panelWidth - 5.5F - 4.0F;
            float mirroredStatusX = mirroredDotX - 3.0F - statusWidth;
            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, mirroredDotX, dotY, 4.0F, 4.0F, statusColor, 1.5F);
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, shownStatus, mirroredStatusX, rowTextY, 0.9F, HudStyle.TEXT_COLOR);
        } else {
            float dotX = panelX + 5.5F;
            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, dotX, dotY, 4.0F, 4.0F, statusColor, 1.5F);
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, shownStatus, dotX + 4.0F + 3.0F, rowTextY, 0.9F, HudStyle.TEXT_COLOR);
        }

        rowY += rowHeight;
        rowTextY = rowY + (rowHeight - fontHeight * 0.9F) / 2.0F;
        float iconY = rowY + (rowHeight - 9.6F) / 2.0F;
        float barY = rowY + (rowHeight - 3.0F) / 2.0F;
        Color crossbowColor = shownCrossbowState == 3 ? READY_COLOR : (shownCrossbowState == 1 ? HudStyle.SECONDARY_TEXT_COLOR : HudStyle.MUTED_TEXT_COLOR);
        if (rightAligned) {
            float mirroredCrossbowIconX = panelX + panelWidth - 5.5F - 9.6F;
            Render2D.drawItem(this.iconStacks[0], mirroredCrossbowIconX, iconY, 0.6F, matrix);
            if (shownCrossbowState == 2) {
                this.drawChargeBar(batch, cameraData, matrix, mirroredCrossbowIconX - 3.0F - 30.0F, barY, shownCharge);
            } else {
                float crossbowTextWidth = Prestige.fontManager.getDefaultFont().getTextWidth(crossbowText) * 0.9F;
                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, crossbowText, mirroredCrossbowIconX - 3.0F - crossbowTextWidth, rowTextY, 0.9F, crossbowColor);
            }
        } else {
            float crossbowIconX = panelX + 5.5F;
            Render2D.drawItem(this.iconStacks[0], crossbowIconX, iconY, 0.6F, matrix);
            if (shownCrossbowState == 2) {
                this.drawChargeBar(batch, cameraData, matrix, crossbowIconX + 9.6F + 3.0F, barY, shownCharge);
            } else {
                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, crossbowText, crossbowIconX + 9.6F + 3.0F, rowTextY, 0.9F, crossbowColor);
            }
        }

        rowY += rowHeight;
        rowTextY = rowY + (rowHeight - fontHeight * 0.9F) / 2.0F;
        iconY = rowY + (rowHeight - 9.6F) / 2.0F;
        Color cartColor = (!previewMode && shownCartCount < 8) ? new Color(ALERT_COLOR.getRed(), ALERT_COLOR.getGreen(), ALERT_COLOR.getBlue(), pulseAlpha) : HudStyle.TEXT_COLOR;
        float cartTextWidth = Prestige.fontManager.getDefaultFont().getTextWidth(cartCountText) * 0.9F;
        if (rightAligned) {
            float mirroredCartIconX = panelX + panelWidth - 5.5F - 9.6F;
            Render2D.drawItem(this.iconStacks[1], mirroredCartIconX, iconY, 0.6F, matrix);
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, cartCountText, mirroredCartIconX - 3.0F - cartTextWidth, rowTextY, 0.9F, cartColor);
        } else {
            float cartIconX = panelX + 5.5F;
            Render2D.drawItem(this.iconStacks[1], cartIconX, iconY, 0.6F, matrix);
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, cartCountText, cartIconX + 9.6F + 3.0F, rowTextY, 0.9F, cartColor);
        }
    }

    private void drawChargeBar(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix, float barX, float barY, float progress) {
        float radius = 1.5F;
        RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX, barY, 30.0F, 3.0F, BAR_TRACK_COLOR, radius);
        float fillWidth = 30.0F * Math.min(1.0F, Math.max(0.0F, progress));
        if (fillWidth > 3.0F) {
            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX, barY, fillWidth, 3.0F, UiMath.getAccentColor(), radius);
            RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, barX + fillWidth - 1.5F, barY, 1.5F, 3.0F, new Color(255, 255, 255, 200), radius);
        }
    }
}

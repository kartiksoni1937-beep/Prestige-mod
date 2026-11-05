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
import dev.zprestige.prestige.type.NotificationType;
import java.awt.Color;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix4f;

public class ResourcesElement extends HudElement {
    private static final Item[] TRACKED_ITEMS = new Item[]{Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE};
    private static final String[] RESOURCE_NAMES = new String[]{"Gapples", "Totems", "Crystals", "Exp"};
    private static final int[] LOW_THRESHOLDS = new int[]{5, 2, 16, 8};
    private final int[] counts;
    private final int[] previousCounts;
    private final long[] nextWarningTimes;
    private final ItemStack[] iconStacks;
    private boolean warningsArmed;
    private boolean iconsInitialized;

    public ResourcesElement() {
        super("Resources", "Tracks gapples, totems, crystals, and exp — warns when any drops below threshold.");
        this.counts = new int[TRACKED_ITEMS.length];
        this.previousCounts = new int[TRACKED_ITEMS.length];
        this.nextWarningTimes = new long[TRACKED_ITEMS.length];
        this.iconStacks = new ItemStack[TRACKED_ITEMS.length];
        this.warningsArmed = false;
        this.iconsInitialized = false;
    }

    private void initIconStacks() {
        if (!this.iconsInitialized) {
            for (int index = 0; index < TRACKED_ITEMS.length; index++) {
                this.iconStacks[index] = new ItemStack(TRACKED_ITEMS[index]);
            }

            this.iconsInitialized = true;
        }
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 220.0F);
    }

    private static int countItem(Item item) {
        int total = 0;

        for (int slot = 0; slot <= 35; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }

        ItemStack offhandStack = mc.player.getOffHandStack();
        if (offhandStack.getItem() == item) {
            total += offhandStack.getCount();
        }

        return total;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            for (int index = 0; index < TRACKED_ITEMS.length; index++) {
                this.counts[index] = countItem(TRACKED_ITEMS[index]);
            }

            if (mc.player.age < 40) {
                System.arraycopy(this.counts, 0, this.previousCounts, 0, this.counts.length);
                this.warningsArmed = false;
            } else if (!this.warningsArmed) {
                System.arraycopy(this.counts, 0, this.previousCounts, 0, this.counts.length);
                this.warningsArmed = true;
            } else {
                long now = System.currentTimeMillis();

                for (int resourceIndex = 0; resourceIndex < TRACKED_ITEMS.length; resourceIndex++) {
                    int threshold = LOW_THRESHOLDS[resourceIndex];
                    if ((this.previousCounts[resourceIndex] >= threshold && this.counts[resourceIndex] < threshold) && now >= this.nextWarningTimes[resourceIndex]) {
                        Prestige.notificationManager.addNotification("Resources", RESOURCE_NAMES[resourceIndex] + " low: " + this.counts[resourceIndex] + "/" + threshold, 4000L, NotificationType.WARNING);
                        long warningCooldown = 5000L + (long)(Math.random() * 5000.0);
                        this.nextWarningTimes[resourceIndex] = now + warningCooldown;
                    }

                    this.previousCounts[resourceIndex] = this.counts[resourceIndex];
                }
            }
        }
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        this.initIconStacks();
        String title = "Resources";
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        float headerHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float rowHeight = HudStyle.getRowHeight(fontHeight, 0.9F);
        float titleWidth = Prestige.fontManager.getDefaultFont().getTextWidth(title);
        float panelWidth = 5.5F + titleWidth + 5.5F;

        for (int measureIndex = 0; measureIndex < TRACKED_ITEMS.length; measureIndex++) {
            float rowWidth = 18.1F + (Prestige.fontManager.getDefaultFont().getTextWidth(String.valueOf(this.counts[measureIndex])) * 0.9F) + 5.5F;
            if (rowWidth > panelWidth) {
                panelWidth = rowWidth;
            }
        }

        float panelHeight = headerHeight + 3.0F + rowHeight * TRACKED_ITEMS.length + 3.0F;
        this.isAnchoredBottom();
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

        for (int rowIndex = 0; rowIndex < TRACKED_ITEMS.length; rowIndex++) {
            Color countColor = (this.warningsArmed && this.counts[rowIndex] < LOW_THRESHOLDS[rowIndex]) ? new Color(235, 64, 64, pulseAlpha) : HudStyle.TEXT_COLOR;
            String countText = String.valueOf(this.counts[rowIndex]);
            float countWidth = Prestige.fontManager.getDefaultFont().getTextWidth(countText) * 0.9F;
            float textY = rowY + (rowHeight - fontHeight * 0.9F) / 2.0F;
            float iconY = rowY + (rowHeight - 9.6F) / 2.0F;
            float iconX;
            float countX;
            if (rightAligned) {
                iconX = panelX + panelWidth - 5.5F - 9.6F;
                countX = iconX - 3.0F - countWidth;
            } else {
                iconX = panelX + 5.5F;
                countX = iconX + 9.6F + 3.0F;
            }

            Render2D.drawItem(this.iconStacks[rowIndex], iconX, iconY, 0.6F, matrix);
            Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, countText, countX, textY, 0.9F, countColor);
            rowY += rowHeight;
        }
    }
}

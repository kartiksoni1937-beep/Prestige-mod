package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.event.RenderLabelEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.inventory.EnchantmentLabel;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.client.render.Frustum;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class NameTags extends Module {
    private FloatSetting scale;
    private IntSetting limit;
    private FloatSetting fadeDistance;
    private BoolSetting distance;
    private BoolSetting healthNumber;
    private BoolSetting totemPops;
    private BoolSetting hands;
    private BoolSetting armor;
    private BoolSetting enchantments;
    private static final Color MEDIUM_HEALTH_COLOR = new Color(240, 170, 60);
    private static final Color LOW_HEALTH_COLOR = new Color(235, 75, 75);
    private static final Color BAR_TRACK_COLOR = new Color(255, 255, 255, 22);
    private static final Color DAMAGE_TRAIL_COLOR = new Color(235, 85, 85, 130);
    private static final Color POP_TEXT_COLOR = new Color(235, 75, 75);
    private static final Color FRIEND_NAME_COLOR = new Color(120, 220, 240);
    private Frustum frustum;
    private Map entriesByPlayer = new HashMap();
    private List sortedEntries = new ArrayList();
    private Map displayEntries = new LinkedHashMap();

    public NameTags() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.enchantments.visibleWhen((Predicate<Boolean>)ignored -> this.armor.getValue());
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            MsdfFont font = Prestige.fontManager.getDefaultFont();
            Color accentColor = Prestige.moduleManager.getMenu().getMenuColor().getValue();
            this.entriesByPlayer.clear();

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && Prestige.botFilter.isValidTarget(player)) {
                    dev.zprestige.prestige.combat.NameTagEntry previous = (dev.zprestige.prestige.combat.NameTagEntry)this.displayEntries.get(player);
                    dev.zprestige.prestige.combat.NameTagEntry entry = new dev.zprestige.prestige.combat.NameTagEntry();
                    entry.name = player.getName().getString();
                    entry.nameColor = Prestige.friendManager.isFriend(entry.name) ? FRIEND_NAME_COLOR : HudStyle.TEXT_COLOR;
                    float health = player.getHealth() + player.getAbsorptionAmount();
                    entry.healthRatio = UiMath.clamp(health / Math.max(1.0F, player.getMaxHealth() + player.getAbsorptionAmount()), 0.0F, 1.0F);
                    float previousDisplayed = previous == null ? entry.healthRatio : previous.displayedHealthRatio;
                    entry.displayedHealthRatio = Math.max(entry.healthRatio, previousDisplayed + (entry.healthRatio - previousDisplayed) * 0.15F);
                    if (entry.healthRatio > previousDisplayed) {
                        entry.displayedHealthRatio = entry.healthRatio;
                    }

                    if (entry.healthRatio > 0.6F) {
                        entry.healthColor = accentColor;
                    } else if (entry.healthRatio > 0.3F) {
                        entry.healthColor = MEDIUM_HEALTH_COLOR;
                    } else {
                        entry.healthColor = LOW_HEALTH_COLOR;
                    }

                    entry.pops = Prestige.totemPopManager.getPopCount(player);
                    entry.popText = this.totemPops.getValue() && entry.pops > 0 ? "-" + entry.pops : "";
                    if (this.healthNumber.getValue()) {
                        entry.healthText = Math.round(health) + "hp";
                    } else {
                        entry.healthText = "";
                    }

                    if (this.hands.getValue()) {
                        if (!player.getMainHandStack().isEmpty()) {
                            entry.items.add(player.getMainHandStack());
                        }

                        if (!player.getOffHandStack().isEmpty()) {
                            entry.items.add(player.getOffHandStack());
                        }
                    }

                    if (this.armor.getValue()) {
                        boolean showEnchants = this.enchantments.getValue();

                        for (EquipmentSlot slot : AttributeModifierSlot.ARMOR) {
                            ItemStack armorStack = player.getEquippedStack(slot);
                            if (!armorStack.isEmpty()) {
                                entry.items.add(armorStack);
                                if (showEnchants) {
                                    entry.enchantLabels.addAll(this.getEnchantLabels(armorStack));
                                }
                            }
                        }
                    }

                    entry.nameWidth = font.getTextWidth(entry.name);
                    entry.popTextWidth = entry.popText.isEmpty() ? 0.0F : font.getTextWidth(entry.popText) * 0.75F;
                    entry.healthTextWidth = entry.healthText.isEmpty() ? 0.0F : font.getTextWidth(entry.healthText) * 0.75F;
                    this.entriesByPlayer.put(player, entry);
                }
            }

            this.sortedEntries.clear();
            this.sortedEntries.addAll(this.entriesByPlayer.entrySet());
            this.sortedEntries.sort((first, second) -> Float.compare(mc.player.distanceTo((Entity)((java.util.Map.Entry)second).getKey()), mc.player.distanceTo((Entity)((java.util.Map.Entry)first).getKey())));
            this.displayEntries.clear();

            for (Entry sortedEntry : (Iterable<Entry>)(this.sortedEntries)) {
                this.displayEntries.put((PlayerEntity)sortedEntry.getKey(), (dev.zprestige.prestige.combat.NameTagEntry)sortedEntry.getValue());
            }
        } else {
            this.entriesByPlayer.clear();
            this.sortedEntries.clear();
            this.displayEntries.clear();
        }
    }

    @Override
    public void onDisable() {
        this.entriesByPlayer.clear();
        this.sortedEntries.clear();
        this.displayEntries.clear();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.player != null) {
            MsdfFont font = Prestige.fontManager.getDefaultFont();
            float lineHeight = font.getLineHeight();
            float cardHeight = HudStyle.getCardHeight(lineHeight);
            float fadeRange = this.fadeDistance.getValue();
            boolean showDistance = this.distance.getValue();
            boolean showHealthNumber = this.healthNumber.getValue();
            boolean showItems = this.hands.getValue() || this.armor.getValue();
            boolean showEnchants = this.enchantments.getValue() && this.armor.getValue();
            int maxTags = this.limit.getValue();
            int drawnTags = 0;

            for (Entry mapEntry : (Iterable<Entry>)(this.displayEntries.entrySet())) {
                if (drawnTags >= maxTags) {
                    break;
                }

                PlayerEntity player = (PlayerEntity)mapEntry.getKey();
                dev.zprestige.prestige.combat.NameTagEntry tagEntry = (dev.zprestige.prestige.combat.NameTagEntry)mapEntry.getValue();
                if (this.frustum == null || this.frustum.isVisible(player.getBoundingBox())) {
                    float playerDistance = mc.player.distanceTo(player);
                    if (!(playerDistance > fadeRange)) {
                        Vec3d screenPos = Render2D.worldToScreen(
                            Render2D.getInterpolatedPos(player).add(0.0, player.getHeight() + 0.5 * (playerDistance / 10.0F), 0.0)
                        );
                        if (!(screenPos.z < 0.0) && !(screenPos.z >= 1.0)) {
                            drawnTags++;
                            float tagScale = this.scale.getValue() * 0.8F - playerDistance / 300.0F;
                            if (tagScale < 0.15F) {
                                tagScale = 0.15F;
                            }

                            float fadeFactor;
                            if (playerDistance > fadeRange - 10.0F) {
                                fadeFactor = (fadeRange - playerDistance) / 10.0F;
                                fadeFactor = UiMath.clamp(fadeFactor, 0.0F, 1.0F);
                            } else {
                                fadeFactor = 1.0F;
                            }

                            float alpha = fadeFactor;
                            float centerX = (float)screenPos.x / tagScale;
                            float centerY = (float)screenPos.y / tagScale;
                            Matrix4f matrix = new Matrix4f().scale(tagScale, tagScale, tagScale);
                            String distanceText = showDistance ? Math.round(playerDistance) + "m" : "";
                            float distanceTextWidth = distanceText.isEmpty() ? 0.0F : font.getTextWidth(distanceText) * 0.65F;
                            float nameRowWidth = tagEntry.nameWidth + (tagEntry.popText.isEmpty() ? 0.0F : 3.0F + tagEntry.popTextWidth);
                            float rightTextWidth = 0.0F;
                            if (!distanceText.isEmpty()) {
                                rightTextWidth = distanceTextWidth;
                            }

                            float contentWidth = Math.max(nameRowWidth + (rightTextWidth > 0.0F ? 6.0F + rightTextWidth : 0.0F), 60.0F);
                            if (showHealthNumber) {
                                contentWidth = Math.max(contentWidth, nameRowWidth + 6.0F + tagEntry.healthTextWidth);
                            }

                            float cardWidth = contentWidth + 11.0F;
                            float cardX = centerX - cardWidth / 2.0F;
                            float cardY = centerY - cardHeight / 2.0F;
                            BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, cardX, cardY, cardWidth, cardHeight, 5, 4.0F);
                            RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, cardX, cardY, cardWidth, cardHeight, scaleAlpha(HudStyle.BACKGROUND_COLOR, alpha), 4.0F);
                            float textY = cardY + 3.0F;
                            float contentLeft = cardX + 5.5F;
                            float contentRight = cardX + cardWidth - 5.5F;
                            font.drawText(matrix, tagEntry.name, contentLeft, textY, scaleAlpha(tagEntry.nameColor, alpha));
                            float popX = contentLeft + tagEntry.nameWidth + 3.0F;
                            if (!tagEntry.popText.isEmpty()) {
                                font.drawTextScaled(matrix, tagEntry.popText, popX, textY + (lineHeight - lineHeight * 0.75F), 0.75F, scaleAlpha(POP_TEXT_COLOR, alpha));
                            }

                            if (!distanceText.isEmpty()) {
                                font.drawTextScaled(matrix, distanceText, contentRight - distanceTextWidth, textY + (lineHeight - lineHeight * 0.65F), 0.65F, scaleAlpha(HudStyle.MUTED_TEXT_COLOR, alpha));
                            }

                            float barY = textY + lineHeight + 1.0F;
                            float barRight = contentRight;
                            if (showHealthNumber && !tagEntry.healthText.isEmpty()) {
                                barRight = contentRight - (tagEntry.healthTextWidth + 3.0F);
                            }

                            float barWidth = barRight - contentLeft;
                            Vector4f barRadii = new Vector4f(1.5F, 1.5F, 1.5F, 1.5F);
                            ShapeRenderer.drawRoundedRect(matrix, contentLeft, barY, contentLeft + barWidth, barY + 4.0F, scaleAlpha(BAR_TRACK_COLOR, alpha), barRadii);
                            float displayedEnd = contentLeft + barWidth * tagEntry.displayedHealthRatio;
                            float healthEnd = contentLeft + barWidth * tagEntry.healthRatio;
                            if (tagEntry.displayedHealthRatio > tagEntry.healthRatio + 0.001F) {
                                ShapeRenderer.drawRoundedRect(matrix, healthEnd, barY, displayedEnd, barY + 4.0F, scaleAlpha(DAMAGE_TRAIL_COLOR, alpha), barRadii);
                            }

                            if (tagEntry.healthRatio > 0.001F) {
                                ShapeRenderer.drawRoundedRect(matrix, contentLeft, barY, healthEnd, barY + 4.0F, scaleAlpha(tagEntry.healthColor, alpha), barRadii);
                            }

                            if (showHealthNumber && !tagEntry.healthText.isEmpty()) {
                                float healthTextY = barY + 2.0F - lineHeight * 0.75F / 2.0F;
                                font.drawTextScaled(matrix, tagEntry.healthText, barRight + 3.0F, healthTextY, 0.75F, scaleAlpha(tagEntry.healthColor, alpha));
                            }

                            if (showItems && !tagEntry.items.isEmpty()) {
                                int itemCount = tagEntry.items.size();
                                float itemY = cardY - 9.6F - 2.0F;
                                float itemStartX = centerX - itemCount * 12.0F / 2.0F + 1.0F;

                                for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                                    Render2D.drawItem((ItemStack)tagEntry.items.get(itemIndex), itemStartX + itemIndex * 12.0F, itemY, 0.6F, matrix);
                                }

                                if (showEnchants && !tagEntry.enchantLabels.isEmpty()) {
                                    int armorStart = this.hands.getValue()
                                        ? (player.getMainHandStack().isEmpty() ? 0 : 1) + (player.getOffHandStack().isEmpty() ? 0 : 1)
                                        : 0;

                                    for (int armorIndex = armorStart; armorIndex < itemCount; armorIndex++) {
                                        float labelX = itemStartX + armorIndex * 12.0F;
                                        float labelOffsetY = 0.0F;
                                        ItemStack itemStack = (ItemStack)tagEntry.items.get(armorIndex);

                                        for (EnchantmentLabel enchantLabel : (Iterable<EnchantmentLabel>)(tagEntry.enchantLabels)) {
                                            if (enchantLabel.stack == itemStack) {
                                                font.drawTextScaled(matrix, enchantLabel.text, labelX, itemY - lineHeight * 0.5F - labelOffsetY, 0.5F, scaleAlpha(HudStyle.SECONDARY_TEXT_COLOR, alpha));
                                                labelOffsetY += lineHeight * 0.5F;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onFrustumSetup(FrustumSetupEvent event) {
        this.frustum = event.getFrustum();
    }

    @Subscribe
    public void onLabelText(LabelTextEvent event) {
        if (event.getEntity() != null && event.getEntity() != mc.player && Prestige.botFilter.isValidTarget(event.getEntity())) {
            event.setText(Text.empty());
            event.cancel();
        }
    }

    @Subscribe
    public void onRenderLabel(RenderLabelEvent event) {
        Entity entity = event.getEntity();
        if (entity != null) {
            if (Prestige.botFilter.isValidTarget(entity)) {
                event.cancel();
            }
        }
    }

    private static Color scaleAlpha(Color color, float factor) {
        float baseAlpha = color.getAlpha();
        int alpha = Math.round(baseAlpha * UiMath.clamp(factor, 0.0F, 1.0F));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private List getEnchantLabels(ItemStack stack) {
        ArrayList labels = new ArrayList();
        ItemEnchantmentsComponent enchantComponent = EnchantmentHelper.getEnchantments(stack);
        if (enchantComponent.isEmpty()) {
            return labels;
        } else {
            for (RegistryEntry enchantment : enchantComponent.getEnchantments()) {
                if (!enchantment.getKey().isEmpty()) {
                    String labelText = (String)enchantment.getKey().map(key -> ((net.minecraft.registry.RegistryKey)key).getValue().getPath()).orElse("Unknown");
                    labelText = labelText.length() > 3 ? labelText.substring(0, 3) : labelText;
                    labelText = labelText + " " + enchantComponent.getLevel(enchantment);
                    labels.add(new EnchantmentLabel(stack, labelText));
                }
            }

            return labels;
        }
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.inventory.ItemEspLabel;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ItemESP extends Module {
    private FloatSetting scale;
    private IntSetting limit;
    private FloatSetting maxDistance;
    private BoolSetting background;
    private ColorSetting backgroundColor;
    private Frustum frustum;
    private Map labels = new LinkedHashMap();
    private static final String COUNT_SEPARATOR = " x";

    public ItemESP() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.backgroundColor.visibleWhen((Predicate<Color>)ignored -> this.background.getValue());
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            float maxDistanceSq = this.maxDistance.getValue() * this.maxDistance.getValue();
            ArrayList entries = new ArrayList();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ItemEntity itemEntity && !(itemEntity.squaredDistanceTo(mc.player) > maxDistanceSq)) {
                    ItemStack stack = itemEntity.getStack();
                    if (stack != null && !stack.isEmpty()) {
                        ItemEspLabel espLabel = new ItemEspLabel();
                        espLabel.stack = stack;
                        String itemName = stack.getName().getString();
                        espLabel.label = stack.getCount() > 1 ? itemName + COUNT_SEPARATOR + stack.getCount() : itemName;
                        entries.add(Map.entry(itemEntity, espLabel));
                    }
                }
            }

            entries.sort((first, second) -> Float.compare(mc.player.distanceTo((Entity)((java.util.Map.Entry)second).getKey()), mc.player.distanceTo((Entity)((java.util.Map.Entry)first).getKey())));
            this.labels = new LinkedHashMap();

            for (Entry entry : (Iterable<Entry>)(entries)) {
                this.labels.put((ItemEntity)entry.getKey(), (ItemEspLabel)entry.getValue());
            }
        } else {
            this.labels = new LinkedHashMap();
        }
    }

    @Override
    public void onDisable() {
        this.labels = new LinkedHashMap();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.canRenderOverlay()) {
                float contentHeight = 10.0F;
                int maxLabels = this.limit.getValue();
                boolean showBackground = this.background.getValue();
                Color panelColor = showBackground ? this.backgroundColor.getValue() : null;
                MsdfFont font = Prestige.fontManager.getDefaultFont();
                float lineHeight = font.getLineHeight();
                int drawnCount = 0;

                for (Entry entry : (Iterable<Entry>)(this.labels.entrySet())) {
                    if (drawnCount >= maxLabels) {
                        break;
                    }

                    ItemEntity itemEntity = (ItemEntity)entry.getKey();
                    ItemEspLabel espLabel = (ItemEspLabel)entry.getValue();
                    if (!itemEntity.isRemoved() && (this.frustum == null || this.frustum.isVisible(itemEntity.getBoundingBox()))) {
                        float distance = mc.player.distanceTo(itemEntity);
                        Vec3d screenPos = Render2D.worldToScreen(
                            Render2D.getInterpolatedPos(itemEntity).add(0.0, itemEntity.getHeight() + 0.4 * (1.0F + distance / 12.0F), 0.0)
                        );
                        if (!(screenPos.z < 0.0) && !(screenPos.z >= 1.0)) {
                            drawnCount++;
                            float labelScale = this.scale.getValue() - distance / 500.0F;
                            if (labelScale < 0.15F) {
                                labelScale = 0.15F;
                            }

                            float textWidth = font.getTextWidth(espLabel.label);
                            float paddingX = 5.0F;
                            float paddingY = 2.0F;
                            float cornerRadius = 4.0F;
                            float centerX = (float)screenPos.x / labelScale;
                            float centerY = (float)screenPos.y / labelScale;
                            Matrix4f matrix = new Matrix4f().scale(labelScale, labelScale, labelScale);
                            float panelWidth = textWidth + 2.0F * paddingX;
                            float panelHeight = contentHeight + 2.0F * paddingY;
                            float panelX = centerX - panelWidth / 2.0F;
                            float panelY = centerY - paddingY;
                            if (showBackground) {
                                BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, 5, cornerRadius);
                                RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, panelX, panelY, panelWidth, panelHeight, panelColor, cornerRadius);
                            }

                            float textX = centerX - textWidth / 2.0F;
                            float textY = centerY + (contentHeight - lineHeight) / 2.0F;
                            font.drawText(matrix, espLabel.label, textX, textY, Color.WHITE);
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

    private boolean canRenderOverlay() {
        return mc.currentScreen == null || mc.currentScreen instanceof ChatScreen;
    }
}

package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.joml.Matrix4f;

public class ModuleListElement extends HudElement {
    private final Map moduleAnimations;
    private final Map lastEnabledStates;
    private final Map enableTimes;
    private static float animationDelta = 0.0F;
    private float lastHeight;
    private float lastWidth;

    public ModuleListElement() {
        super("Module List", "List of currently enabled modules");
        this.moduleAnimations = new HashMap();
        this.lastEnabledStates = new HashMap();
        this.enableTimes = new HashMap();
        this.lastHeight = 0.0F;
        this.lastWidth = 0.0F;
    }

    @Override
    public void resetPosition() {
        this.setPosition(2.0F, 135.0F);
    }

    @Override
    public void renderEditorOutline(Matrix4f matrix) {
        if (this.width <= 0.0F || this.height <= 0.0F) {
            return;
        }

        super.renderEditorOutline(matrix);
    }

    @Override
    public void render(BufferSource batch, CameraMatrices cameraData, Matrix4f matrix) {
        long now = System.currentTimeMillis();
        animationDelta = (float)(now - 0L) * 0.005F;

        for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            boolean enabled = module.isEnabled();
            Boolean wasEnabled = (Boolean)this.lastEnabledStates.put(module, enabled);
            if (enabled && Boolean.FALSE.equals(wasEnabled)) {
                this.enableTimes.put(module, now);
            }

            float targetVisibility = enabled ? 1.0F : 0.0F;
            float visibility = (float)this.moduleAnimations.getOrDefault(module, 0.0F);
            visibility = UiMath.approach(visibility, targetVisibility, animationDelta);
            this.moduleAnimations.put(module, visibility);
        }

        ArrayList visibleEntries = new ArrayList();

        for (Entry animationEntry : (Iterable<Entry>)(this.moduleAnimations.entrySet())) {
            if ((Float)animationEntry.getValue() > 0.005F) {
                visibleEntries.add(animationEntry);
            }
        }

        if (visibleEntries.isEmpty()) {
            this.setSize(0.0F, 0.0F);
            this.lastHeight = 0.0F;
            this.lastWidth = 0.0F;
        } else {
            visibleEntries.sort(
                (first, second) -> Float.compare(
                    Prestige.fontManager.getDefaultFont().getTextWidth(((Module)((java.util.Map.Entry)second).getKey()).getName()),
                    Prestige.fontManager.getDefaultFont().getTextWidth(((Module)((java.util.Map.Entry)first).getKey()).getName())
                )
            );
            float rowHeight = HudStyle.getRowHeight(Prestige.fontManager.getDefaultFont().getLineHeight(), 1.0F);
            float listWidth = 0.0F;
            float listHeight = 0.0F;

            for (Entry measuredEntry : (Iterable<Entry>)(visibleEntries)) {
                float entryWidth = Prestige.fontManager.getDefaultFont().getTextWidth(((Module)measuredEntry.getKey()).getName()) + 11.0F;
                if (entryWidth > listWidth) {
                    listWidth = entryWidth;
                }

                listHeight += (rowHeight + 1.0F) * (Float)measuredEntry.getValue();
            }

            mc.getWindow().getScaledWidth();
            mc.getWindow().getScaledHeight();
            boolean bottomAligned = this.isAnchoredBottom();
            boolean rightAligned = this.isAnchoredRight();
            if (!(mc.currentScreen instanceof HudEditorScreen)) {
                if (bottomAligned && this.lastHeight > 0.0F) {
                    this.y = this.y - (listHeight - this.lastHeight);
                }

                if (rightAligned && this.lastWidth > 0.0F) {
                    this.x = this.x - (listWidth - this.lastWidth);
                }
            }

            this.lastHeight = listHeight;
            this.lastWidth = listWidth;
            this.setSize(listWidth, listHeight);
            Color accent = UiMath.getAccentColor();
            int entryCount = visibleEntries.size();
            float rowY = bottomAligned ? this.y + listHeight - rowHeight : this.y;

            for (int rowIndex = 0; rowIndex < visibleEntries.size(); rowIndex++) {
                Entry rowEntry = (Entry)visibleEntries.get(rowIndex);
                Module rowModule = (Module)rowEntry.getKey();
                float rowVisibility = (Float)rowEntry.getValue();
                String moduleName = rowModule.getName();
                float rowWidth = Prestige.fontManager.getDefaultFont().getTextWidth(moduleName) + 11.0F;
                float slideOffset = (1.0F - rowVisibility) * 6.0F;
                Color waveColor = Render2D.getWaveColor(accent, 10, 10 + (rightAligned ? entryCount - 1 - rowIndex : rowIndex));
                float rowX;
                if (rightAligned) {
                    rowX = this.x + listWidth - rowWidth - slideOffset;
                } else {
                    rowX = this.x + slideOffset;
                }

                long enabledAt = (long)this.enableTimes.getOrDefault(rowModule, 0L);
                float enableFlash = enabledAt == 0L ? 0.0F : Math.max(0.0F, 1.0F - (float)(now - enabledAt) / 500.0F);
                enableFlash *= enableFlash;
                int backgroundAlpha = clampAlpha((int)(HudStyle.BACKGROUND_COLOR.getAlpha() * rowVisibility));
                float tintAmount = enableFlash * 0.35F;
                Color backgroundColor = new Color(lerpChannel(HudStyle.BACKGROUND_COLOR.getRed(), waveColor.getRed(), tintAmount), lerpChannel(HudStyle.BACKGROUND_COLOR.getGreen(), waveColor.getGreen(), tintAmount), lerpChannel(HudStyle.BACKGROUND_COLOR.getBlue(), waveColor.getBlue(), tintAmount), backgroundAlpha);
                Color textColor = new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), clampAlpha((int)(255.0F * rowVisibility)));
                if (rowVisibility > 0.7F) {
                    BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, rowX, rowY, rowWidth, rowHeight, 5, 4.0F);
                }

                RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, rowX, rowY, rowWidth, rowHeight, backgroundColor, 4.0F);
                Prestige.fontManager.getDefaultFont().drawText(matrix, moduleName, rowX + 5.5F, rowY + 3.0F, textColor);
                if (bottomAligned) {
                    rowY -= (rowHeight + 1.0F) * rowVisibility;
                } else {
                    rowY += (rowHeight + 1.0F) * rowVisibility;
                }
            }
        }
    }

    private static int clampAlpha(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    private static int lerpChannel(int start, int end, float factor) {
        return clampAlpha((int)(start + (end - start) * factor));
    }
}

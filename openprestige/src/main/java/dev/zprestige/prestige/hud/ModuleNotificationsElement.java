package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.NotificationTypeSwitchMap;
import dev.zprestige.prestige.render.ModuleToast;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import dev.zprestige.prestige.type.NotificationType;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.joml.Matrix4f;

public class ModuleNotificationsElement extends HudElement {
    public static ModuleNotificationsElement instance = new ModuleNotificationsElement();
    private final ArrayList notifications;
    private final Map iconTextures;
    private long lastFrameTime;
    private float lastHeight;
    private float lastWidth;

    public ModuleNotificationsElement() {
        super("Module Notifications", "Draws module notifications when toggled.");
        this.notifications = new ArrayList();
        this.iconTextures = new HashMap();
        this.lastFrameTime = 0L;
        this.lastHeight = 0.0F;
        this.lastWidth = 0.0F;
        instance = this;
    }

    public static void notifyToggle(Module module) {
        if (instance.isEnabled()) {
            if (module.isEnabled()) {
                instance.addNotification(module.getName(), "Has been enabled.", NotificationType.SUCCESS);
            } else {
                instance.addNotification(module.getName(), "Has been disabled.", NotificationType.ERROR);
            }
        }
    }

    public static void postNotification(Module module, String message, NotificationType type) {
        if (instance.isEnabled()) {
            instance.addNotification(module.getName(), message, type);
        }
    }

    private void addNotification(String title, String message, NotificationType type) {
        for (ModuleToast existing : (Iterable<ModuleToast>)(this.notifications)) {
            if (existing.title.equals(title) && !existing.dismissed) {
                existing.dismissed = true;
            }
        }

        this.notifications.add(new ModuleToast(title, message, type));
        while (this.notifications.size() > 32) {
            this.notifications.remove(0);   // toasts only expire while the HUD is drawn; keep the list bounded
        }
    }

    @Override
    public void resetPosition() {
        if (mc.getWindow() != null) {
            this.setPosition((mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale()) - 90.0F - 4.0F, (mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale()) - 70.0F);
        } else {
            this.setPosition(200.0F, 150.0F);
        }
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
        float frameDelta = this.lastFrameTime == 0L ? 0.0F : (float)(now - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = now;
        boolean inEditor = mc.currentScreen instanceof HudEditorScreen;
        Iterator iterator = this.notifications.iterator();

        while (iterator.hasNext()) {
            ModuleToast updatedToast = (ModuleToast)iterator.next();
            if (!updatedToast.dismissed && now - updatedToast.createdAt >= 2000L) {
                updatedToast.dismissed = true;
            }

            updatedToast.animation = UiMath.approach(updatedToast.animation, updatedToast.dismissed ? 0.0F : 1.0F, updatedToast.dismissed ? frameDelta * 2.0F : frameDelta);
            if (updatedToast.dismissed && updatedToast.animation <= 0.01F) {
                iterator.remove();
            }
        }

        int activeCount = 0;

        for (ModuleToast countedToast : (Iterable<ModuleToast>)(this.notifications)) {
            if (!countedToast.dismissed) {
                activeCount++;
            }
        }

        for (ModuleToast overflowToast : (Iterable<ModuleToast>)(this.notifications)) {
            if (activeCount <= 5) {
                break;
            }

            if (!overflowToast.dismissed) {
                overflowToast.dismissed = true;
                activeCount--;
            }
        }

        ArrayList visibleToasts = new ArrayList(this.notifications);
        if (inEditor && visibleToasts.isEmpty()) {
            ModuleToast previewToast = new ModuleToast("Module Notifications", "Toasts appear here.", NotificationType.SUCCESS);
            previewToast.animation = 1.0F;
            visibleToasts.add(previewToast);
        }

        if (visibleToasts.isEmpty()) {
            this.setSize(0.0F, 0.0F);
            this.lastHeight = 0.0F;
            this.lastWidth = 0.0F;
        } else {
            float remainingLife = mc.getWindow().getFramebufferWidth();
            HudEditorScreen.getHudPixelScale();
            remainingLife = mc.getWindow().getFramebufferHeight();
            HudEditorScreen.getHudPixelScale();
            boolean rightAligned = this.isAnchoredRight();
            boolean bottomAligned = this.isAnchoredBottom();
            float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
            float toastHeight = fontHeight * 1.65F + 6.0F;
            float maxToastWidth = 0.0F;
            float totalHeight = 0.0F;

            for (ModuleToast measuredToast : (Iterable<ModuleToast>)(visibleToasts)) {
                maxToastWidth = Math.max(maxToastWidth, this.getToastWidth(measuredToast));
                totalHeight += (toastHeight + 3.0F) * measuredToast.animation;
            }

            if (!inEditor) {
                if (bottomAligned && this.lastHeight > 0.0F) {
                    this.y = this.y - (totalHeight - this.lastHeight);
                }

                if (rightAligned && this.lastWidth > 0.0F) {
                    this.x = this.x - (maxToastWidth - this.lastWidth);
                }
            }

            this.lastHeight = totalHeight;
            this.lastWidth = maxToastWidth;
            this.setSize(maxToastWidth, totalHeight);
            float toastY = bottomAligned ? this.y + totalHeight - toastHeight : this.y;

            for (int toastIndex = visibleToasts.size() - 1; toastIndex >= 0; toastIndex--) {
                ModuleToast toast = (ModuleToast)visibleToasts.get(toastIndex);
                float appearProgress = toast.animation;
                float toastWidth = this.getToastWidth(toast);
                Color typeColor = getTypeColor(toast.type);
                float slideOffset = (1.0F - appearProgress) * 12.0F;
                float toastX = rightAligned ? this.x + maxToastWidth - toastWidth + slideOffset : this.x - slideOffset;
                float toastRight = toastX + toastWidth;
                Color backgroundColor = new Color(HudStyle.BACKGROUND_COLOR.getRed(), HudStyle.BACKGROUND_COLOR.getGreen(), HudStyle.BACKGROUND_COLOR.getBlue(), clampAlpha((int)(HudStyle.BACKGROUND_COLOR.getAlpha() * appearProgress)));
                Color iconColor = withAlpha(typeColor, (int)(230.0F * appearProgress));
                Color titleColor = new Color(255, 255, 255, clampAlpha((int)(255.0F * appearProgress)));
                Color messageColor = new Color(HudStyle.MUTED_TEXT_COLOR.getRed(), HudStyle.MUTED_TEXT_COLOR.getGreen(), HudStyle.MUTED_TEXT_COLOR.getBlue(), clampAlpha((int)(255.0F * appearProgress)));
                if (appearProgress > 0.7F) {
                    BlurredRoundedRectShader.drawBlurredRect(batch, cameraData, matrix, toastX, toastY, toastWidth, toastHeight, 5, 4.0F);
                }

                RoundedRectShader.drawRoundedRect(batch, cameraData, matrix, toastX, toastY, toastWidth, toastHeight, backgroundColor, 4.0F);
                GlTextureHandle typeIcon = this.getTypeIcon(toast.type);
                float iconX = rightAligned ? toastRight - 5.5F - 10.0F : toastX + 5.5F;
                if (typeIcon != null) {
                    RoundedTextureShader.drawTexture(batch, cameraData, matrix, typeIcon, iconX, toastY + (toastHeight - 10.0F) / 2.0F, 10.0F, 10.0F, iconColor);
                }
                float titleWidth = Prestige.fontManager.getDefaultFont().getTextWidth(toast.title) * 0.9F;
                float messageWidth = Prestige.fontManager.getDefaultFont().getTextWidth(toast.message) * 0.75F;
                float titleX = rightAligned ? iconX - 3.0F - titleWidth : iconX + 10.0F + 3.0F;
                float messageX = rightAligned ? iconX - 3.0F - messageWidth : iconX + 10.0F + 3.0F;
                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, toast.title, titleX, toastY + 3.0F, 0.9F, titleColor);
                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, toast.message, messageX, toastY + 3.0F + fontHeight * 0.9F, 0.75F, messageColor);
                if (toast.dismissed) {
                    remainingLife = 0.0F;
                } else {
                    remainingLife = 1.0F - (float)(now - toast.createdAt) / 2000.0F;
                    remainingLife = UiMath.clamp(remainingLife, 0.0F, 1.0F);
                }

                float lifeBarWidth = (toastWidth - 11.0F) * remainingLife;
                if (lifeBarWidth > 0.5F) {
                    Color lifeBarColor = withAlpha(typeColor, (int)(150.0F * appearProgress));
                    if (rightAligned) {
                        ShapeRenderer.drawRect(matrix, toastRight - 5.5F - lifeBarWidth, toastY + toastHeight - 3.0F, toastRight - 5.5F, toastY + toastHeight - 2.0F, lifeBarColor);
                    } else {
                        ShapeRenderer.drawRect(matrix, toastX + 5.5F, toastY + toastHeight - 3.0F, toastX + 5.5F + lifeBarWidth, toastY + toastHeight - 2.0F, lifeBarColor);
                    }
                }

                float rowAdvance = (toastHeight + 3.0F) * appearProgress;
                if (bottomAligned) {
                    toastY -= rowAdvance;
                } else {
                    toastY += rowAdvance;
                }
            }
        }
    }

    private float getToastWidth(ModuleToast toast) {
        return Math.max(90.0F, 18.5F + Math.max(Prestige.fontManager.getDefaultFont().getTextWidth(toast.title) * 0.9F, Prestige.fontManager.getDefaultFont().getTextWidth(toast.message) * 0.75F) + 5.5F);
    }

    private GlTextureHandle getTypeIcon(NotificationType type) {
        GlTextureHandle icon = (GlTextureHandle)this.iconTextures.get(type);
        if (icon == null) {
            icon = TextureLoader.loadTexture("assets/prestige/icons/notifications/" + type.name().toLowerCase() + ".png");
            this.iconTextures.put(type, icon);
        }

        return icon;
    }

    private static Color getTypeColor(NotificationType type) {
        switch (NotificationTypeSwitchMap.SWITCH_MAP[type.ordinal()]) {
            case 1:
                return new Color(76, 175, 80);
            case 2:
                return new Color(244, 67, 54);
            case 3:
                return new Color(255, 152, 0);
            case 4:
                return new Color(33, 150, 243);
            default:
                return new Color(128, 128, 128);
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampAlpha(alpha));
    }

    private static int clampAlpha(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}

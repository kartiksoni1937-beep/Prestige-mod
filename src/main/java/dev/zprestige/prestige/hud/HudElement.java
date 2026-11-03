package dev.zprestige.prestige.hud;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.AxisSwitchMap;
import dev.zprestige.prestige.render.SnapGuide;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.HudAlignment;
import dev.zprestige.prestige.type.HudAnchor;
import java.awt.Color;
import java.util.ArrayList;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public class HudElement implements MinecraftAccess {
    private final String name;
    private final String description;
    private final BoolSetting enabledSetting;
    public float x;
    public float y;
    public float width;
    public float height;
    public HudAlignment anchorX;
    public HudAlignment anchorY;
    public float offsetX;
    public float offsetY;
    private boolean positionPending;
    private float pendingX;
    private float pendingY;
    private boolean xClamped;
    private boolean yClamped;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private static final String ENABLED_SETTING_NAME = "Enabled";

    public HudElement(String elementName, String elementDescription) {
        this.enabledSetting = new BoolSetting(ENABLED_SETTING_NAME, false);
        this.x = 0.0F;
        this.y = 0.0F;
        this.width = 0.0F;
        this.height = 0.0F;
        this.anchorX = HudAlignment.Start;
        this.anchorY = HudAlignment.Start;
        this.offsetX = 0.0F;
        this.offsetY = 0.0F;
        this.positionPending = false;
        this.pendingX = 0.0F;
        this.pendingY = 0.0F;
        this.xClamped = false;
        this.yClamped = false;
        this.dragging = false;
        this.dragOffsetX = 0.0F;
        this.dragOffsetY = 0.0F;
        this.name = elementName;
        this.description = elementDescription;
    }

    public void setPosition(float newX, float newY) {
        this.x = newX;
        this.y = newY;
        this.recomputeAnchors();
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public void recomputeAnchors() {
        if (mc.getWindow() != null) {
            if (!(this.width <= 0.0F) && !(this.height <= 0.0F)) {
                this.positionPending = false;
                this.computeAnchorX(mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale());
                this.computeAnchorY(mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale());
            } else {
                this.pendingX = this.x;
                this.pendingY = this.y;
                this.positionPending = true;
            }
        }
    }

    private void computeAnchorX(float screenWidth) {
        float centerDelta = this.x + this.width / 2.0F - screenWidth / 2.0F;
        if (Math.abs(centerDelta) <= 8.0F) {
            this.anchorX = HudAlignment.Center;
            this.offsetX = centerDelta;
        } else if (centerDelta < 0.0F) {
            this.anchorX = HudAlignment.Start;
            this.offsetX = this.x;
        } else {
            this.anchorX = HudAlignment.End;
            this.offsetX = screenWidth - (this.x + this.width);
        }
    }

    private void computeAnchorY(float screenHeight) {
        float centerDelta = this.y + this.height / 2.0F - screenHeight / 2.0F;
        if (Math.abs(centerDelta) <= 8.0F) {
            this.anchorY = HudAlignment.Center;
            this.offsetY = centerDelta;
        } else if (centerDelta < 0.0F) {
            this.anchorY = HudAlignment.Start;
            this.offsetY = this.y;
        } else {
            this.anchorY = HudAlignment.End;
            this.offsetY = screenHeight - (this.y + this.height);
        }
    }

    public boolean isPositionPending() {
        return this.positionPending;
    }

    public boolean isAnchoredRight() {
        return this.anchorX == HudAlignment.End;
    }

    public boolean isAnchoredBottom() {
        return this.anchorY == HudAlignment.End;
    }

    public void resolvePosition() {
        if (mc.getWindow() != null) {
            float screenWidth = mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale();
            float screenHeight = mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale();
            if (this.positionPending) {
                if (!(this.width > 0.0F) || !(this.height > 0.0F)) {
                    this.x = Math.clamp(this.pendingX, 0.0F, Math.max(0.0F, screenWidth - this.width));
                    this.y = Math.clamp(this.pendingY, 0.0F, Math.max(0.0F, screenHeight - this.height));
                    return;
                }

                this.x = this.pendingX;
                this.y = this.pendingY;
                this.recomputeAnchors();
            }
            float anchoredX = switch (this.anchorX) {
                case Start -> this.offsetX;
                case Center -> screenWidth / 2.0F - this.width / 2.0F + this.offsetX;
                case End -> screenWidth - this.offsetX - this.width;
            };

            float anchoredY = switch (this.anchorY) {
                case Start -> this.offsetY;
                case Center -> screenHeight / 2.0F - this.height / 2.0F + this.offsetY;
                case End -> screenHeight - this.offsetY - this.height;
            };
            this.x = Math.clamp(anchoredX, 0.0F, Math.max(0.0F, screenWidth - this.width));
            this.y = Math.clamp(anchoredY, 0.0F, Math.max(0.0F, screenHeight - this.height));
            this.xClamped = this.x != anchoredX;
            this.yClamped = this.y != anchoredY;
        }
    }

    public void setAnchors(HudAlignment newAnchorX, HudAlignment newAnchorY, float newOffsetX, float newOffsetY) {
        this.anchorX = newAnchorX;
        this.anchorY = newAnchorY;
        this.offsetX = newOffsetX;
        this.offsetY = newOffsetY;
        this.positionPending = false;
        this.resolvePosition();
    }

    public void setCornerAnchor(HudAnchor corner, float cornerOffsetX, float cornerOffsetY) {
        this.setAnchors((corner == HudAnchor.TopLeft || corner == HudAnchor.BottomLeft) ? HudAlignment.Start : HudAlignment.End, (corner == HudAnchor.TopLeft || corner == HudAnchor.TopRight) ? HudAlignment.Start : HudAlignment.End, cornerOffsetX, cornerOffsetY);
    }

    public void nudge(float deltaX, float deltaY) {
        if (this.positionPending) {
            this.pendingX += deltaX;
            this.pendingY += deltaY;
            this.x += deltaX;
            this.y += deltaY;
        } else {
            this.offsetX = this.offsetX + (this.anchorX == HudAlignment.End ? -deltaX : deltaX);
            this.offsetY = this.offsetY + (this.anchorY == HudAlignment.End ? -deltaY : deltaY);
            this.resolvePosition();
            if (mc.getWindow() != null && !(this.width <= 0.0F) && !(this.height <= 0.0F)) {
                if (!this.xClamped) {
                    this.computeAnchorX(mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale());
                }

                if (!this.yClamped) {
                    this.computeAnchorY(mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale());
                }
            }
        }
    }

    public void setSize(float newWidth, float newHeight) {
        this.width = newWidth;
        this.height = newHeight;
    }

    public void toggle() {
        this.enabledSetting.setValue(!this.enabledSetting.getValue());
        if (this.isEnabled()) {
            Prestige.eventBus.register(this);
        } else {
            Prestige.eventBus.unregister(this);
        }
    }

    public boolean isEnabled() {
        return this.enabledSetting.getValue();
    }

    public void onUpdate() {
    }

    public void render(dev.zprestige.prestige.render.BufferSource batch, dev.zprestige.prestige.data.CameraMatrices cameraData, Matrix4f matrix) {
    }

    public void resetPosition() {
    }

    public void renderEditorRow(Matrix4f matrix, float rowX, float rowY, float rowWidth, float rowHeight, int mouseX, int mouseY, int clickState, ArrayList snapLines) {
        boolean hovered = mouseX > rowX && mouseX < rowX + rowWidth && mouseY > rowY && mouseY < rowY + rowHeight;
        if (this.dragging) {
            float draggedX = mouseX + this.dragOffsetX;
            float draggedY = mouseY + this.dragOffsetY;
            this.x = draggedX;
            this.y = draggedY;
            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), 340) != 1) {
                float bestSnapDistanceX = 5.0F;
                float bestSnapDistanceY = 5.0F;
                SnapGuide snapLineX = null;
                SnapGuide snapLineY = null;
                byte snapEdgeX = 0;
                byte snapEdgeY = 0;

                for (SnapGuide snapLine : (Iterable<SnapGuide>)(snapLines)) {
                    switch (AxisSwitchMap.SWITCH_MAP[snapLine.getAxis().ordinal()]) {
                        case 1:
                            float leftDistance = Math.abs(snapLine.getPosition() - this.x);
                            float rightDistance = Math.abs(snapLine.getPosition() - (this.x + this.width));
                            float centerXDistance = Math.abs(snapLine.getPosition() - (this.x + this.width / 2.0F));
                            if (leftDistance < bestSnapDistanceX) {
                                bestSnapDistanceX = leftDistance;
                                snapLineX = snapLine;
                                snapEdgeX = 0;
                            }

                            if (rightDistance < bestSnapDistanceX) {
                                bestSnapDistanceX = rightDistance;
                                snapLineX = snapLine;
                                snapEdgeX = 1;
                            }

                            if (centerXDistance < bestSnapDistanceX) {
                                bestSnapDistanceX = centerXDistance;
                                snapLineX = snapLine;
                                snapEdgeX = 2;
                            }
                            break;
                        case 2:
                            float topDistance = Math.abs(snapLine.getPosition() - this.y);
                            float bottomDistance = Math.abs(snapLine.getPosition() - (this.y + this.height));
                            float centerYDistance = Math.abs(snapLine.getPosition() - (this.y + this.height / 2.0F));
                            if (topDistance < bestSnapDistanceY) {
                                bestSnapDistanceY = topDistance;
                                snapLineY = snapLine;
                                snapEdgeY = 0;
                            }

                            if (bottomDistance < bestSnapDistanceY) {
                                bestSnapDistanceY = bottomDistance;
                                snapLineY = snapLine;
                                snapEdgeY = 1;
                            }

                            if (centerYDistance < bestSnapDistanceY) {
                                bestSnapDistanceY = centerYDistance;
                                snapLineY = snapLine;
                                snapEdgeY = 2;
                            }
                    }
                }

                float screenWidth = mc.getWindow().getFramebufferWidth() / HudEditorScreen.getHudPixelScale();
                float screenHeight = mc.getWindow().getFramebufferHeight() / HudEditorScreen.getHudPixelScale();
                if (snapLineX != null) {
                    this.x = snapEdgeX == 0
                        ? snapLineX.getPosition()
                        : (
                            snapEdgeX == 1
                                ? snapLineX.getPosition() - this.width
                                : snapLineX.getPosition() - this.width / 2.0F
                        );
                    Render2D.drawRect(matrix, snapLineX.getPosition() - 0.5F, -1.0F, snapLineX.getPosition() + 0.5F, screenHeight, UiMath.getAccentColor());
                }

                if (snapLineY != null) {
                    this.y = snapEdgeY == 0
                        ? snapLineY.getPosition()
                        : (
                            snapEdgeY == 1
                                ? snapLineY.getPosition() - this.height
                                : snapLineY.getPosition() - this.height / 2.0F
                        );
                    Render2D.drawRect(matrix, 0.0F, snapLineY.getPosition() - 0.5F, screenWidth, snapLineY.getPosition() + 0.5F, UiMath.getAccentColor());
                }
            }
        }

        ShapeRenderer.drawRoundedRect(matrix, rowX, rowY, rowX + rowWidth, rowY + rowHeight, this.isEnabled()
            ? new Color(
                UiMath.getAccentColor().getRed(), UiMath.getAccentColor().getGreen(), UiMath.getAccentColor().getBlue(), 40
            )
            : new Color(255, 255, 255, hovered ? 12 : 5), new Vector4f(3.0F));
        if (this.isEnabled()) {
            ShapeRenderer.drawRoundedRect(matrix, rowX, rowY + 1.5F, rowX + 2.0F, rowY + rowHeight - 1.5F, UiMath.getAccentColor(), new Vector4f(1.0F));
        }

        Render2D.drawRoundedRectOutline(matrix, rowX, rowY, rowX + rowWidth, rowY + rowHeight, new Color(255, 255, 255, hovered ? 18 : 8), 3.0F);
        float labelX = rowX + (this.isEnabled() ? 6.0F : 3.0F);
        float labelY = rowY + rowHeight / 2.0F - Prestige.fontManager.getDefaultFont().getLineHeight() / 2.0F;
        Color labelColor = this.isEnabled() ? Color.WHITE : new Color(180, 180, 180);
        Prestige.fontManager.getDefaultFont().drawText(matrix, this.name, labelX, labelY, labelColor);
        if (clickState == 1 && hovered) {
            this.toggle();
        }
    }

    public void handleEditorMouse(int mouseX, int mouseY, boolean released) {
        if (released) {
            if (this.dragging) {
                this.dragging = false;
                this.recomputeAnchors();
            }
        } else {
            if (mouseX > this.x && mouseX < this.x + this.width && mouseY > this.y && mouseY < this.y + this.height) {
                this.dragOffsetX = this.x - mouseX;
                this.dragOffsetY = this.y - mouseY;
                this.dragging = true;
            }
        }
    }

    public void renderEditorOutline(Matrix4f matrix) {
        Render2D.drawRoundedRectOutline(matrix, this.x, this.y, this.x + this.width, this.y + this.height, Color.WHITE, 0.0F);
    }

    public String getName() {
        return this.name;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }
}

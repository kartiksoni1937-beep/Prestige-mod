package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.HudStyle;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.hud.CartHelperElement;
import dev.zprestige.prestige.hud.CooldownIndicatorElement;
import dev.zprestige.prestige.hud.CrosshairElement;
import dev.zprestige.prestige.hud.FPSElement;
import dev.zprestige.prestige.hud.HudElement;
import dev.zprestige.prestige.hud.KeybindsElement;
import dev.zprestige.prestige.hud.LatencyElement;
import dev.zprestige.prestige.hud.ModuleListElement;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.hud.OffscreenArrowElement;
import dev.zprestige.prestige.hud.PotionDisplayElement;
import dev.zprestige.prestige.hud.ResourcesElement;
import dev.zprestige.prestige.hud.TPSElement;
import dev.zprestige.prestige.hud.TargetElement;
import dev.zprestige.prestige.hud.TimeElement;
import dev.zprestige.prestige.hud.WatermarkElement;
import dev.zprestige.prestige.hud.WebsiteElement;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.module.visual.HudEditor;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.RenderCallback;
import dev.zprestige.prestige.render.SnapGuide;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.type.GuideAxis;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends BaseScreen {
    private final HudElement[] elements;
    private final ArrayList snapLines;
    private boolean draggingPanel;
    private float panelDragOffsetX;
    private float panelDragOffsetY;
    private float panelX;
    private float panelY;
    private int pendingClick;
    private boolean defaultPositionsApplied;
    private HudElement selectedElement;
    private static final int[] EDITOR_KEYS = new int[]{263, 262, 265, 264, 82};
    private final long[] keyRepeatAt;
    private final boolean[] keyWasDown;
    private float fadeProgress;
    private long lastFrameTime;

    public static float getHudScale() {
        return HudEditor.getHudScalePercent() / 100.0F;
    }

    public static float getHudPixelScale() {
        return 2.0F * getHudScale();
    }

    public HudEditorScreen() {
        super();
        this.elements = new HudElement[]{
            new CrosshairElement(),
            new CooldownIndicatorElement(),
            new OffscreenArrowElement(),
            new ModuleNotificationsElement(),
            new TargetElement(),
            new FPSElement(),
            new TPSElement(),
            new KeybindsElement(),
            new ModuleListElement(),
            new LatencyElement(),
            new TimeElement(),
            new PotionDisplayElement(),
            new ResourcesElement(),
            new CartHelperElement(),
            new WatermarkElement(),
            new WebsiteElement()
        };
        this.snapLines = new ArrayList();
        this.draggingPanel = false;
        this.panelDragOffsetX = 0.0F;
        this.panelDragOffsetY = 0.0F;
        this.panelX = -1.0F;
        this.panelY = -1.0F;
        this.pendingClick = 0;
        this.defaultPositionsApplied = false;
        this.selectedElement = null;
        this.keyRepeatAt = new long[EDITOR_KEYS.length];
        this.keyWasDown = new boolean[EDITOR_KEYS.length];
        this.fadeProgress = 0.0F;
        this.lastFrameTime = System.currentTimeMillis();
        RenderEvents.HUD_RENDER.register((RenderCallback)(renderBatch, renderMatrices) -> {
            if (MinecraftAccess.mc.currentScreen == this) {
                try {
                    Mouse mouse = MinecraftAccess.mc.mouse;
                    this.renderEditor(renderBatch, renderMatrices, (int)Math.round(mouse.getX() / MinecraftAccess.mc.getWindow().getScaleFactor()), (int)Math.round(mouse.getY() / MinecraftAccess.mc.getWindow().getScaleFactor()), Render2D.getTickDelta());
                } catch (Throwable error) {
                    Prestige.logRenderError(error);   // do not let an editor render error reach the game loop
                }
            }
        });
    }

    @Override
    public void init() {
        super.init();
        if (this.panelX == -1.0F && this.panelY == -1.0F) {
            float screenWidth = MinecraftAccess.mc.getWindow().getFramebufferWidth() / getHudPixelScale();
            float screenHeight = MinecraftAccess.mc.getWindow().getFramebufferHeight() / getHudPixelScale();
            this.panelX = screenWidth / 2.0F - 70.0F;
            this.panelY = screenHeight / 2.0F - this.getPanelHeight() / 2.0F;
        }

        if (!this.defaultPositionsApplied) {
            for (HudElement element : this.elements) {
                element.resetPosition();
            }

            this.defaultPositionsApplied = true;
        }

        if (MinecraftAccess.mc.getWindow() != null) {
            long windowHandle = MinecraftAccess.mc.getWindow().getHandle();

            for (int i = 0; i < EDITOR_KEYS.length; i++) {
                this.keyWasDown[i] = GLFW.glfwGetKey(windowHandle, EDITOR_KEYS[i]) == 1;
                this.keyRepeatAt[i] = Long.MAX_VALUE;
            }
        }

        this.selectedElement = null;
        this.fadeProgress = 0.0F;
        this.lastFrameTime = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    public void renderEditor(BufferSource batch, CameraMatrices matrices, int mouseX, int mouseY, float tickDelta) {
        Render2D.currentBuffers = batch;
        Render2D.currentContext = matrices;
        float guiScale = MinecraftAccess.mc.getWindow().getScaleFactor();
        float renderScale = getHudPixelScale() / guiScale;
        float screenWidth = MinecraftAccess.mc.getWindow().getFramebufferWidth() / getHudPixelScale();
        float screenHeight = MinecraftAccess.mc.getWindow().getFramebufferHeight() / getHudPixelScale();
        float hudMouseX = mouseX * guiScale / getHudPixelScale();
        float hudMouseY = mouseY * guiScale / getHudPixelScale();
        if (this.panelX == -1.0F && this.panelY == -1.0F) {
            this.panelX = screenWidth / 2.0F - 70.0F;
            this.panelY = screenHeight / 2.0F - this.getPanelHeight() / 2.0F;
        }

        float fadeStep = (float)(System.currentTimeMillis() - this.lastFrameTime) * 0.005F;
        this.lastFrameTime = System.currentTimeMillis();
        this.fadeProgress = UiMath.approach(this.fadeProgress, 1.0F, fadeStep);
        if (this.draggingPanel) {
            this.panelX = UiMath.approach(this.panelX, hudMouseX + this.panelDragOffsetX, Render2D.getTickDelta() / 10.0F);
            this.panelY = UiMath.approach(this.panelY, hudMouseY + this.panelDragOffsetY, Render2D.getTickDelta() / 10.0F);
        }

        this.snapLines.clear();
        this.snapLines.add(new SnapGuide(GuideAxis.Y, 3));
        this.snapLines.add(new SnapGuide(GuideAxis.X, 2));
        this.snapLines.add(new SnapGuide(GuideAxis.Y, (int)(screenHeight - 3.0F)));
        this.snapLines.add(new SnapGuide(GuideAxis.X, (int)(screenWidth - 3.0F)));
        this.snapLines.add(new SnapGuide(GuideAxis.X, (int)(screenWidth / 2.0F)));
        this.snapLines.add(new SnapGuide(GuideAxis.Y, (int)(screenHeight / 2.0F)));

        for (HudElement element : this.elements) {
            if (element.isEnabled() && !element.isDragging()) {
                element.resolvePosition();
            }
        }

        for (HudElement guideElement : this.elements) {
            if (guideElement.isEnabled() && !guideElement.isDragging() && !(guideElement.width <= 0.0F)) {
                this.snapLines.add(new SnapGuide(GuideAxis.X, (int)guideElement.x));
                this.snapLines.add(new SnapGuide(GuideAxis.X, (int)(guideElement.x + guideElement.width)));
                this.snapLines.add(new SnapGuide(GuideAxis.X, (int)(guideElement.x + guideElement.width / 2.0F)));
                this.snapLines.add(new SnapGuide(GuideAxis.Y, (int)guideElement.y));
                this.snapLines.add(new SnapGuide(GuideAxis.Y, (int)(guideElement.y + guideElement.height)));
                this.snapLines.add(new SnapGuide(GuideAxis.Y, (int)(guideElement.y + guideElement.height / 2.0F)));
            }
        }

        this.handleEditorKeys();
        Matrix4f matrix = new Matrix4f().scale(renderScale, renderScale, 1.0F);
        float panelHeight = this.getPanelHeight();
        Color accentColor = UiMath.getAccentColor();
        float fontHeight = Prestige.fontManager.getDefaultFont().getLineHeight();
        Render2D.drawRect(matrix, 0.0F, 0.0F, screenWidth, screenHeight, new Color(0, 0, 0, (int)(70.0F * this.fadeProgress)));
        Color gridColor = new Color(255, 255, 255, (int)(6.0F * this.fadeProgress));

        for (float gridX = 20.0F; gridX < screenWidth; gridX += 20.0F) {
            Render2D.drawRect(matrix, gridX - 0.25F, 0.0F, gridX + 0.25F, screenHeight, gridColor);
        }

        for (float gridY = 20.0F; gridY < screenHeight; gridY += 20.0F) {
            Render2D.drawRect(matrix, 0.0F, gridY - 0.25F, screenWidth, gridY + 0.25F, gridColor);
        }

        Color centerLineColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int)(30.0F * this.fadeProgress));
        Render2D.drawRect(matrix, screenWidth / 2.0F - 0.25F, 0.0F, screenWidth / 2.0F + 0.25F, screenHeight, centerLineColor);
        Render2D.drawRect(matrix, 0.0F, screenHeight / 2.0F - 0.25F, screenWidth, screenHeight / 2.0F + 0.25F, centerLineColor);
        Render2D.drawRoundedRectOutline(matrix, 2.0F, 3.0F, screenWidth - 3.0F, screenHeight - 3.0F, new Color(255, 255, 255, (int)(14.0F * this.fadeProgress)), 0.0F);
        BlurredRoundedRectShader.drawBlurredRect(batch, matrices, matrix, this.panelX, this.panelY, 140.0F, panelHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, matrices, matrix, this.panelX, this.panelY, 140.0F, panelHeight, new Color(HudStyle.BACKGROUND_COLOR.getRed(), HudStyle.BACKGROUND_COLOR.getGreen(), HudStyle.BACKGROUND_COLOR.getBlue(), (int)(HudStyle.BACKGROUND_COLOR.getAlpha() * this.fadeProgress)), 4.0F);
        ShapeRenderer.drawRoundedRect(matrix, this.panelX, this.panelY, this.panelX + 140.0F, this.panelY + 24.0F, new Color(24, 24, 30, (int)(235.0F * this.fadeProgress)), new Vector4f(4.0F, 4.0F, 0.0F, 0.0F));
        float pulse = (float)(Math.sin(System.currentTimeMillis() / 600.0) * 0.5 + 0.5);
        Render2D.drawRect(matrix, this.panelX + 4.0F, this.panelY + 24.0F - 1.0F, this.panelX + 140.0F - 4.0F, this.panelY + 24.0F, new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int)((150.0F + 90.0F * pulse) * this.fadeProgress)));
        Render2D.drawRoundedRectOutline(matrix, this.panelX, this.panelY, this.panelX + 140.0F, this.panelY + panelHeight, new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int)(70.0F * this.fadeProgress)), 4.0F);
        String headerText = "HUD Editor";
        float headerWidth = Prestige.fontManager.getDefaultFont().getTextWidth(headerText);
        Prestige.fontManager.getDefaultFont().drawText(matrix, headerText, this.panelX + 70.0F - headerWidth / 2.0F, this.panelY + 12.0F - fontHeight / 2.0F, new Color(255, 255, 255, (int)(255.0F * this.fadeProgress)));
        int enabledCount = 0;

        for (HudElement countedElement : this.elements) {
            if (countedElement.isEnabled()) {
                enabledCount++;
            }
        }

        String countText = enabledCount + "/" + this.elements.length;
        float countWidth = Prestige.fontManager.getDefaultFont().getTextWidth(countText);
        Prestige.fontManager.getDefaultFont().drawText(matrix, countText, this.panelX + 140.0F - 5.5F - countWidth, this.panelY + 12.0F - fontHeight / 2.0F, new Color(HudStyle.MUTED_TEXT_COLOR.getRed(), HudStyle.MUTED_TEXT_COLOR.getGreen(), HudStyle.MUTED_TEXT_COLOR.getBlue(), (int)(HudStyle.MUTED_TEXT_COLOR.getAlpha() * this.fadeProgress)));
        float rowX = this.panelX + 5.5F;
        float rowY = this.panelY + 24.0F + 3.0F;

        for (HudElement listElement : this.elements) {
            listElement.renderEditorRow(matrix, rowX, rowY, 129.0F, 17.0F, (int)hudMouseX, (int)hudMouseY, this.pendingClick, this.snapLines);
            rowY += 20.0F;
        }

        for (HudElement outlinedElement : this.elements) {
            if (outlinedElement.isEnabled()) {
                outlinedElement.renderEditorOutline(matrix);
            }
        }

        if (this.selectedElement != null && this.selectedElement.isEnabled()) {
            Render2D.drawRoundedRectOutline(matrix, this.selectedElement.x - 1.5F, this.selectedElement.y - 1.5F, this.selectedElement.x + this.selectedElement.width + 1.5F, this.selectedElement.y + this.selectedElement.height + 1.5F, new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int)(200.0F * this.fadeProgress)), 0.0F);
        }

        String[][] hints = new String[][]{
            {"Click", "toggle"}, {"Drag", "move"}, {"Shift", "free move / big nudge"}, {"Arrows", "nudge"}, {"R", "reset layout"}
        };
        String hintSeparator = "   ";
        float separatorWidth = Prestige.fontManager.getDefaultFont().getTextWidth(hintSeparator);
        float hintsWidth = separatorWidth * (hints.length - 1);

        for (String[] hint : hints) {
            hintsWidth += Prestige.fontManager.getDefaultFont().getTextWidth(hint[0])
                + Prestige.fontManager.getDefaultFont().getTextWidth(" = " + hint[1]);
        }

        if (hintsWidth + 16.0F > screenWidth - 8.0F) {
            hints = new String[][]{{"Drag", "move"}, {"Arrows", "nudge"}, {"R", "reset"}};
            hintsWidth = separatorWidth * (hints.length - 1);

            for (String[] compactHint : hints) {
                hintsWidth += Prestige.fontManager.getDefaultFont().getTextWidth(compactHint[0])
                    + Prestige.fontManager.getDefaultFont().getTextWidth(" = " + compactHint[1]);
            }
        }

        float hintPadding = 8.0F;
        float hintBarHeight = HudStyle.getRowHeight(fontHeight, 1.0F);
        float hintBarWidth = hintsWidth + hintPadding * 2.0F;
        float hintBarX = Math.max(4.0F, screenWidth / 2.0F - hintBarWidth / 2.0F);
        float hintBarY = screenHeight - hintBarHeight - 8.0F;
        BlurredRoundedRectShader.drawBlurredRect(batch, matrices, matrix, hintBarX, hintBarY, hintBarWidth, hintBarHeight, 5, 4.0F);
        RoundedRectShader.drawRoundedRect(batch, matrices, matrix, hintBarX, hintBarY, hintBarWidth, hintBarHeight, new Color(HudStyle.BACKGROUND_COLOR.getRed(), HudStyle.BACKGROUND_COLOR.getGreen(), HudStyle.BACKGROUND_COLOR.getBlue(), (int)(HudStyle.BACKGROUND_COLOR.getAlpha() * this.fadeProgress)), 4.0F);
        Color hintKeyColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int)(255.0F * this.fadeProgress));
        Color hintTextColor = new Color(HudStyle.SECONDARY_TEXT_COLOR.getRed(), HudStyle.SECONDARY_TEXT_COLOR.getGreen(), HudStyle.SECONDARY_TEXT_COLOR.getBlue(), (int)(230.0F * this.fadeProgress));
        float hintX = hintBarX + hintPadding;
        float hintY = hintBarY + (hintBarHeight - fontHeight) / 2.0F;

        for (int hintIndex = 0; hintIndex < hints.length; hintIndex++) {
            Prestige.fontManager.getDefaultFont().drawText(matrix, hints[hintIndex][0], hintX, hintY, hintKeyColor);
            hintX += Prestige.fontManager.getDefaultFont().getTextWidth(hints[hintIndex][0]);
            String hintDescription = " = " + hints[hintIndex][1];
            Prestige.fontManager.getDefaultFont().drawText(matrix, hintDescription, hintX, hintY, hintTextColor);
            hintX += Prestige.fontManager.getDefaultFont().getTextWidth(hintDescription);
            if (hintIndex < hints.length - 1) {
                hintX += separatorWidth;
            }
        }

        this.pendingClick = 0;
    }

    private float getPanelHeight() {
        return 24.0F + this.elements.length * 20.0F + 6.0F;
    }

    private void handleEditorKeys() {
        long windowHandle = MinecraftAccess.mc.getWindow().getHandle();
        long now = System.currentTimeMillis();
        boolean shiftDown = GLFW.glfwGetKey(windowHandle, 340) == 1 || GLFW.glfwGetKey(windowHandle, 344) == 1;

        for (int i = 0; i < EDITOR_KEYS.length; i++) {
            int key = EDITOR_KEYS[i];
            boolean down = GLFW.glfwGetKey(windowHandle, key) == 1;
            boolean justPressed = down && !this.keyWasDown[i];
            boolean triggered = false;
            if (justPressed) {
                triggered = true;
                this.keyRepeatAt[i] = now + 300L;
            } else if (down && now >= this.keyRepeatAt[i]) {
                triggered = true;
                this.keyRepeatAt[i] = now + 45L;
            }

            this.keyWasDown[i] = down;
            if (triggered) {
                if (key == 82) {
                    if (justPressed) {
                        this.resetLayout();
                    }
                } else if (this.selectedElement != null && this.selectedElement.isEnabled()) {
                    float step = shiftDown ? 10.0F : 1.0F;
                    switch (key) {
                        case 262:
                            this.selectedElement.nudge(step, 0.0F);
                            break;
                        case 263:
                            this.selectedElement.nudge(-step, 0.0F);
                            break;
                        case 264:
                            this.selectedElement.nudge(0.0F, step);
                            break;
                        case 265:
                            this.selectedElement.nudge(0.0F, -step);
                    }
                }
            }
        }
    }

    private void resetLayout() {
        for (HudElement element : this.elements) {
            element.resetPosition();
        }

        this.selectedElement = null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double rawX = click.x();
        double rawY = click.y();
        int button = click.button();
        if (this.panelX == -1.0F && this.panelY == -1.0F) {
            this.init();
        }

        double guiScale = MinecraftAccess.mc.getWindow().getScaleFactor();
        double hudX = rawX * guiScale / getHudPixelScale();
        double hudY = rawY * guiScale / getHudPixelScale();
        if (button == 0) {
            if (this.isOverPanelHeader((int)hudX, (int)hudY)) {
                this.panelDragOffsetX = (float)(this.panelX - hudX);
                this.panelDragOffsetY = (float)(this.panelY - hudY);
                this.draggingPanel = true;
            }

            if (!(hudX >= this.panelX && hudX <= this.panelX + 140.0F && hudY >= this.panelY && hudY <= this.panelY + this.getPanelHeight())) {
                for (HudElement element : this.elements) {
                    if (element.isEnabled()) {
                        element.handleEditorMouse((int)hudX, (int)hudY, false);
                    }
                }

                for (HudElement candidate : this.elements) {
                    if (candidate.isEnabled() && candidate.isDragging()) {
                        this.selectedElement = candidate;
                    }
                }
            }

            this.pendingClick = 1;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double rawX = click.x();
        double rawY = click.y();
        int button = click.button();
        double guiScale = MinecraftAccess.mc.getWindow().getScaleFactor();
        double hudX = rawX * guiScale / getHudPixelScale();
        double hudY = rawY * guiScale / getHudPixelScale();
        if (button == 0) {
            this.draggingPanel = false;

            for (HudElement element : this.elements) {
                if (element.isEnabled()) {
                    element.handleEditorMouse((int)hudX, (int)hudY, true);
                }
            }
        }

        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        MinecraftAccess.mc.setScreen(Prestige.clickGuiScreen);
        Prestige.clickGuiScreen.resetClosedState();
        return false;
    }

    private boolean isOverPanelHeader(int mouseX, int mouseY) {
        return mouseX >= this.panelX && mouseX <= this.panelX + 140.0F && mouseY >= this.panelY && mouseY <= this.panelY + 24.0F;
    }

    public boolean renderHud(BufferSource batch, CameraMatrices matrices) {
        if (MinecraftAccess.mc.currentScreen != null
            && !(MinecraftAccess.mc.currentScreen instanceof HudEditorScreen)
            && !(MinecraftAccess.mc.currentScreen instanceof ChatScreen)) {
            return false;
        } else if (!this.hasEnabledElement()) {
            return false;
        } else {
            float guiScale = MinecraftAccess.mc.getWindow().getScaleFactor();
            float renderScale = getHudPixelScale() / guiScale;
            Matrix4f matrix = new Matrix4f().scale(renderScale, renderScale, 1.0F);
            if (!this.defaultPositionsApplied) {
                for (HudElement element : this.elements) {
                    element.resetPosition();
                }

                this.defaultPositionsApplied = true;
            }

            boolean renderedAny = false;

            for (HudElement hudElement : this.elements) {
                if (hudElement.isEnabled()) {
                    if (!hudElement.isDragging()) {
                        hudElement.resolvePosition();
                    }

                    try {
                        hudElement.render(batch, matrices, matrix);
                    } catch (Throwable error) {
                        Prestige.logRenderError(error);   // one broken element must not take down the whole HUD
                    }
                    renderedAny = true;
                }
            }

            return renderedAny;
        }
    }

    public boolean shouldRenderHud() {
        if (MinecraftAccess.mc.currentScreen == this) {
            return true;
        } else {
            return MinecraftAccess.mc.currentScreen != null && !(MinecraftAccess.mc.currentScreen instanceof ChatScreen) ? false : this.hasEnabledElement();
        }
    }

    private boolean hasEnabledElement() {
        HudElement[] allElements = this.elements;

        for (HudElement element : allElements) {
            if (element.isEnabled()) {
                return true;
            }
        }

        return false;
    }

    public HudElement[] getElements() {
        return this.elements;
    }
}

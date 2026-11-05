package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClickGuiScreen extends Screen {
    public ClickGuiScreen() {
        super(Text.of(""));
    }

    public void resetClosedState() {
        Overlay.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Keep this screen alive as a hook carrier so the game continues rendering and updating underneath.
        // The real ClickGUI is the overlay (dev.zprestige.prestige.ui.mc.Overlay) drawn by the RenderSystem.flipFrame mixin.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Overlay.visible) {
            Overlay.onMouseButton(button, 0);
        }
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void blur() {
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.renderBackground(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void close() {
        Overlay.close();
        super.close();
    }
}

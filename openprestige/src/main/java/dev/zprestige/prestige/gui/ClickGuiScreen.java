package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.Native;
import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.client.gui.DrawContext;

public class ClickGuiScreen extends BaseScreen implements MinecraftAccess {
    private boolean nativeGuiShown = false;
    private boolean guiClosed = false;

    public void resetClosedState() {
        this.guiClosed = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (this.nativeGuiShown && !Native.isMenuOpen()) {
            this.close();
        } else {
            if (!this.nativeGuiShown && !this.guiClosed) {
                if (!Native.isMenuOpen()) {
                    Native.toggleMenu();
                }

                this.nativeGuiShown = true;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.close();
        return super.mouseClicked(mouseX, mouseY, button);
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
        this.guiClosed = true;
        this.nativeGuiShown = false;
        if (Native.isMenuOpen()) {
            Native.toggleMenu();
        }

        super.close();
    }
}

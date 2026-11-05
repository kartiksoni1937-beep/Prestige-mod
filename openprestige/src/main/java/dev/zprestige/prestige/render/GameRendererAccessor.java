package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.GameRenderer;

public class GameRendererAccessor {
    private final GameRenderer gameRenderer;

    public GameRendererAccessor(GameRenderer renderer) {
        this.gameRenderer = renderer;
    }

    public InGameOverlayRenderer getOverlayRenderer() {
        return (InGameOverlayRenderer)FieldAccessor.bindMapped(this.gameRenderer, "overlayRenderer", GameRenderer.class, InGameOverlayRenderer.class).get();
    }
}

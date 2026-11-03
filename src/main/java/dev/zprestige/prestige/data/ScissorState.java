package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;

public record ScissorState(boolean enabled, boolean restoreEnabled, int x, int y, int width, int height) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        if (this.enabled) {
            GL11.glEnable(3089);
            GL11.glScissor(this.x, this.y, this.width, this.height);
        } else {
            GL11.glDisable(3089);
        }
    }

    @Override
    public void cleanup() {
        if (this.restoreEnabled != this.enabled) {
            if (this.restoreEnabled) {
                GL11.glEnable(3089);
            } else {
                GL11.glDisable(3089);
            }
        }
    }

    @Override
    public boolean allowsDuplicates() {
        return true;
    }
}

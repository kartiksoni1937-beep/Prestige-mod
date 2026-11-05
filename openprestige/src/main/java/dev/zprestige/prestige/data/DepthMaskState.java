package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;

public record DepthMaskState(boolean depthWrite, boolean restoreDepthWrite) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        GL11.glDepthMask(this.depthWrite);
    }

    @Override
    public void cleanup() {
        if (this.restoreDepthWrite != this.depthWrite) {
            GL11.glDepthMask(this.restoreDepthWrite);
        }
    }
}

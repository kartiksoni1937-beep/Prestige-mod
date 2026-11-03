package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;

public record DepthFuncState(int depthFunc, int restoreDepthFunc) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        GL11.glDepthFunc(this.depthFunc);
    }

    @Override
    public void cleanup() {
        if (this.restoreDepthFunc != this.depthFunc) {
            GL11.glDepthFunc(this.restoreDepthFunc);
        }
    }
}

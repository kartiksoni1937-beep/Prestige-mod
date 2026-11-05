package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public record BlendFuncState(boolean enabled, boolean restoreEnabled, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        if (this.enabled) {
            GL11.glEnable(3042);
            GL14.glBlendFuncSeparate(this.srcRgb, this.dstRgb, this.srcAlpha, this.dstAlpha);
        } else {
            GL11.glDisable(3042);
        }
    }

    @Override
    public void cleanup() {
        if (this.restoreEnabled != this.enabled) {
            if (this.restoreEnabled) {
                GL11.glEnable(3042);
            } else {
                GL11.glDisable(3042);
            }
        }
    }
}

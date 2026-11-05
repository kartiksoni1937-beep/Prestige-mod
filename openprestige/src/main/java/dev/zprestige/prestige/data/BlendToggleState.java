package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public record BlendToggleState(boolean enabled, boolean wasEnabled) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        if (this.enabled) {
            GL11.glEnable(3042);
            GL14.glBlendFuncSeparate(770, 771, 1, 771);
        } else {
            GL11.glDisable(3042);
        }
    }

    @Override
    public void cleanup() {
        if (this.wasEnabled != this.enabled) {
            if (this.wasEnabled) {
                GL11.glEnable(3042);
            } else {
                GL11.glDisable(3042);
            }
        }
    }
}

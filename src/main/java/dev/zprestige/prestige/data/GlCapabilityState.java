package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;

public record GlCapabilityState(int capability, boolean enabled, boolean restoreEnabled) implements RenderStateElement {
    public GlCapabilityState(int glCapability, boolean enabledState) {
        this(glCapability, enabledState, enabledState);
    }

    @Override
    public void apply(GlRenderPass renderPass) {
        if (this.enabled) {
            GL11.glEnable(this.capability);
        } else {
            GL11.glDisable(this.capability);
        }
    }

    @Override
    public void cleanup() {
        if (this.restoreEnabled != this.enabled) {
            if (this.restoreEnabled) {
                GL11.glEnable(this.capability);
            } else {
                GL11.glDisable(this.capability);
            }
        }
    }

    @Override
    public boolean allowsDuplicates() {
        return true;
    }
}

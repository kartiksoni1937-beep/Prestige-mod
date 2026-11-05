package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPassBuilder;
import dev.zprestige.prestige.render.RenderStateElement;
import java.util.function.IntSupplier;

public record FramebufferBindState(IntSupplier framebuffer) implements RenderStateElement {
    public FramebufferBindState(int framebufferId) {
        this(() -> framebufferId);
    }

    @Override
    public void configure(GlRenderPassBuilder drawContext) {
        drawContext.setFramebuffer(this.framebuffer.getAsInt());
    }
}

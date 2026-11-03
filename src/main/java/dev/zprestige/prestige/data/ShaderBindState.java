package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPassBuilder;
import dev.zprestige.prestige.render.Shader;
import dev.zprestige.prestige.render.RenderStateElement;

public record ShaderBindState(Shader shader) implements RenderStateElement {
    @Override
    public void configure(GlRenderPassBuilder drawContext) {
        drawContext.setShader(this.shader);
    }
}

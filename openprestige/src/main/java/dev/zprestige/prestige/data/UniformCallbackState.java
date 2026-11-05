package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import java.util.function.Consumer;

public record UniformCallbackState(Consumer callback) implements RenderStateElement {
    @Override
    public void apply(GlRenderPass renderPass) {
        this.callback.accept(ProgramUniforms.of(renderPass.shader.getProgramId()));
    }

    @Override
    public boolean allowsDuplicates() {
        return true;
    }
}

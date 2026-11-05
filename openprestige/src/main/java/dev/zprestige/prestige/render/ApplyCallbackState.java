package dev.zprestige.prestige.render;

import java.util.function.Consumer;

public class ApplyCallbackState implements RenderStateElement {
    public final Consumer callback;

    public ApplyCallbackState(Consumer onApply) {
        this.callback = onApply;
    }

    @Override
    public void apply(GlRenderPass pass) {
        this.callback.accept(pass);
    }
}

package dev.zprestige.prestige.render;

import java.util.function.Consumer;

public class ConfigureCallbackState implements RenderStateElement {
    public final Consumer callback;

    public ConfigureCallbackState(Consumer onConfigure) {
        this.callback = onConfigure;
    }

    @Override
    public void configure(GlRenderPassBuilder builder) {
        this.callback.accept(builder);
    }
}

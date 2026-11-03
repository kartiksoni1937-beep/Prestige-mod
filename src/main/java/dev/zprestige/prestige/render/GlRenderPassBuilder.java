package dev.zprestige.prestige.render;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

public class GlRenderPassBuilder {
    public IntSupplier framebuffer = () -> 0;
    public OptionalInt clearColor = OptionalInt.empty();
    public OptionalDouble clearDepth = OptionalDouble.empty();
    public Shader shader;

    public GlRenderPassBuilder() {
    }

    public GlRenderPassBuilder setFramebuffer(int framebufferId) {
        this.framebuffer = () -> framebufferId;
        return this;
    }

    public GlRenderPassBuilder setFramebufferSupplier(IntSupplier supplier) {
        this.framebuffer = supplier;
        return this;
    }

    public GlRenderPassBuilder setClearColor(OptionalInt color) {
        this.clearColor = color;
        return this;
    }

    public GlRenderPassBuilder setClearDepth(OptionalDouble depth) {
        this.clearDepth = depth;
        return this;
    }

    public GlRenderPassBuilder setShader(Shader program) {
        this.shader = program;
        return this;
    }

    public GlRenderPass build() {
        return new GlRenderPass(this);
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.DepthMaskState;
import dev.zprestige.prestige.data.BlendToggleState;
import dev.zprestige.prestige.data.BlendFuncState;
import dev.zprestige.prestige.data.ScissorState;
import dev.zprestige.prestige.data.UniformCallbackState;
import dev.zprestige.prestige.data.GlCapabilityState;
import dev.zprestige.prestige.data.DepthFuncState;
import dev.zprestige.prestige.data.FramebufferBindState;
import dev.zprestige.prestige.data.ShaderBindState;
import java.util.function.Consumer;

public interface RenderStateElement {
    FramebufferBindState MAIN_TARGET = new FramebufferBindState(RenderCore::getMainFramebufferId);

    default void configure(GlRenderPassBuilder passBuilder) {
    }

    default void apply(GlRenderPass pass) {
    }

    default void cleanup() {
    }

    default boolean allowsDuplicates() {
        return false;
    }

    static DepthMaskState depthMask(boolean enabled, boolean restoreEnabled) {
        return new DepthMaskState(enabled, restoreEnabled);
    }

    static DepthMaskState depthMask(boolean enabled) {
        return new DepthMaskState(enabled, enabled);
    }

    static GlCapabilityState capability(int cap, boolean enabled) {
        return new GlCapabilityState(cap, enabled);
    }

    static GlCapabilityState capability(int cap, boolean enabled, boolean restoreEnabled) {
        return new GlCapabilityState(cap, enabled, restoreEnabled);
    }

    static ShaderBindState shader(Shader program) {
        return new ShaderBindState(program);
    }

    static FramebufferBindState target(int framebufferId) {
        return new FramebufferBindState(framebufferId);
    }

    static DepthFuncState depthFunc(int func, int restoreFunc) {
        return new DepthFuncState(func, restoreFunc);
    }

    static BlendToggleState blend(boolean enabled, boolean restoreEnabled) {
        return new BlendToggleState(enabled, restoreEnabled);
    }

    static BlendFuncState blendFunc(boolean enabled, boolean restoreEnabled, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        return new BlendFuncState(enabled, restoreEnabled, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    static ScissorState scissor(boolean enabled, boolean restoreEnabled, int x, int y, int width, int height) {
        return new ScissorState(enabled, restoreEnabled, x, y, width, height);
    }

    static UniformCallbackState uniforms(Consumer uniformSetter) {
        return new UniformCallbackState(uniformSetter);
    }

    static RenderStateElement onApply(Consumer action) {
        return new ApplyCallbackState(action);
    }

    static RenderStateElement onConfigure(Consumer action) {
        return new ConfigureCallbackState(action);
    }
}

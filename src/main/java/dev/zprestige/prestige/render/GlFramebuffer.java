package dev.zprestige.prestige.render;

import java.lang.ref.Cleaner.Cleanable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class GlFramebuffer implements AutoCloseable {
    public final int glId;
    private final Cleanable cleanable;

    public GlFramebuffer(int framebufferId) {
        this.glId = framebufferId;
        this.cleanable = RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> GL30.glDeleteFramebuffers(framebufferId)));
    }

    public static GlFramebuffer create() {
        return new GlFramebuffer(GL30.glGenFramebuffers());
    }

    public void attachTextureId(int attachment, int textureId) {
        int previous = GL11.glGetInteger(36006);
        GL30.glBindFramebuffer(36160, this.glId);
        GL30.glFramebufferTexture2D(36160, attachment, 3553, textureId, 0);
        GL30.glBindFramebuffer(36160, previous);
    }

    public void attachTexture(int attachment, GlTextureHandle texture) {
        this.attachTextureId(attachment, texture.glId);
    }

    public void clearColor(float red, float green, float blue, float alpha) {
        int previous = GL11.glGetInteger(36006);
        GL30.glBindFramebuffer(36009, this.glId);
        GL30.glClearBufferfv(6144, 0, new float[]{red, green, blue, alpha});
        GL30.glBindFramebuffer(36009, previous);
    }

    @Override
    public void close() throws Exception {
        this.cleanable.clean();
    }
}

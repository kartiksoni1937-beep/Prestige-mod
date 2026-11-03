package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.RenderCacheUtil;
import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.IntSupplier;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public class GlRenderPass {
    private final IntSupplier targetFramebuffer;
    private final OptionalInt clearColor;
    private final OptionalDouble clearDepth;
    public final Shader shader;
    private final int projMatLocation;
    private final int viewMatLocation;

    public GlRenderPass(GlRenderPassBuilder passBuilder) {
        this.targetFramebuffer = Objects.requireNonNull(passBuilder.framebuffer, "targetFramebuffer cannot be null");
        this.clearColor = Objects.requireNonNull(passBuilder.clearColor, "clearColor cannot be null");
        this.clearDepth = Objects.requireNonNull(passBuilder.clearDepth, "clearDepth cannot be null");
        this.shader = Objects.requireNonNull(passBuilder.shader, "shaderProgram cannot be null");
        this.projMatLocation = passBuilder.shader.getProjMatLocation();
        this.viewMatLocation = passBuilder.shader.getViewMatLocation();
    }

    public static GlRenderPassBuilder builder() {
        return new GlRenderPassBuilder();
    }

    public void begin() {
        GL30.glBindFramebuffer(36160, this.targetFramebuffer.getAsInt());
        this.clearTarget();
        GL30.glUseProgram(this.shader.getProgramId());
    }

    public void uploadMatrices(CameraMatrices context) {
        this.shader.getProgramId();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer projBuffer = stack.mallocFloat(16);
            FloatBuffer viewBuffer = stack.mallocFloat(16);
            if (this.projMatLocation >= 0) {
                context.projection().get(projBuffer);
                projBuffer.rewind();
                GL30.glUniformMatrix4fv(this.projMatLocation, false, projBuffer);
            }

            if (this.viewMatLocation >= 0) {
                context.modelView().get(viewBuffer);
                viewBuffer.rewind();
                GL30.glUniformMatrix4fv(this.viewMatLocation, false, viewBuffer);
            }
        }
    }

    public void drawIndexed(VertexArray vertexArray, GlBuffer indexBuffer, int primitiveMode, int indexCount) {
        GL30.glBindVertexArray(vertexArray.getGlId());
        GL30.glBindBuffer(34963, indexBuffer.getGlId());
        GL30.glDrawElements(primitiveMode, indexCount, 5125, 0L);
        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(34963, 0);
    }

    private void clearTarget() {
        short clearMask = 0;
        if (this.clearColor.isPresent()) {
            int argb = this.clearColor.getAsInt();
            GL30.glClearColor(RenderCacheUtil.getRedFloat(argb), RenderCacheUtil.getGreenFloat(argb), RenderCacheUtil.getBlueFloat(argb), RenderCacheUtil.getAlphaFloat(argb));
            clearMask = 16384;
        }

        if (this.clearDepth.isPresent()) {
            GL30.glClearDepth(this.clearDepth.getAsDouble());
            clearMask |= 256;
        }

        if (clearMask != 0) {
            GL30.glDisable(3089);
            GL30.glDepthMask(true);
            GL30.glColorMask(true, true, true, true);
            GL30.glClear(clearMask);
        }
    }
}

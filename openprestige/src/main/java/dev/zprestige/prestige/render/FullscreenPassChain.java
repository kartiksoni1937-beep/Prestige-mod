package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.QueuedRenderPass;
import dev.zprestige.prestige.type.GlPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class FullscreenPassChain {
    private static final GlBuffer QUAD_VERTICES = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
    private static final GlBuffer QUAD_INDICES = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
    private static final VertexArray QUAD_VAO = VertexArray.forBuffer(QUAD_VERTICES, VertexLayouts.POSITION_UV);
    private final List passes = new ArrayList();

    public FullscreenPassChain addPass(Shader shader, Consumer setup, int targetFramebuffer) {
        return this.addDynamicPass(shader, setup, (IntSupplier)() -> targetFramebuffer);
    }

    public FullscreenPassChain addDynamicPass(Shader shader, Consumer setup, IntSupplier targetSupplier) {
        this.passes.add(new QueuedRenderPass(GlRenderPass.builder().setFramebufferSupplier(targetSupplier).setShader(shader).build(), setup));
        return this;
    }

    public void render() {
        for (QueuedRenderPass entry : (Iterable<QueuedRenderPass>)(this.passes)) {
            GlRenderPass pass = entry.renderPass();
            pass.begin();
            entry.drawCallback().accept(pass);
            pass.drawIndexed(QUAD_VAO, QUAD_INDICES, 4, 6);
        }
    }

    static {
        long[] bufferSizes = new long[]{4L, 24L};
        QUAD_VERTICES.allocate(35044, bufferSizes[0] * VertexLayouts.POSITION_UV.stride);
        QUAD_INDICES.allocate(35044, bufferSizes[1]);

        try (MappedMeshBuilder builder = new MappedMeshBuilder(VertexLayouts.POSITION_UV, QUAD_VERTICES, QUAD_INDICES)) {
            builder.appendPosition(-1.0F, 1.0F, 1.0F).appendFloats(new float[]{0.0F, 1.0F}).appendPosition(-1.0F, -1.0F, 1.0F).appendFloats(new float[]{0.0F, 0.0F}).appendPosition(1.0F, -1.0F, 1.0F).appendFloats(new float[]{1.0F, 0.0F}).appendPosition(1.0F, 1.0F, 1.0F).appendFloats(new float[]{1.0F, 1.0F});
            builder.generateIndices(GlPrimitive.QUADS);
        }
    }
}

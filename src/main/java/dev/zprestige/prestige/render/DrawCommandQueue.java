package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.QueuedDraw;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.DrawBatchKey;
import dev.zprestige.prestige.data.MeshInfo;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public class DrawCommandQueue {
    public GlBuffer vertexBuffer;
    public GlBuffer indexBuffer;
    public VertexArray vertexArray;
    private boolean initialized = false;
    private final List commands = new ArrayList();
    public float nextDepth = 0.0F;
    private static final int MAX_QUEUED_COMMANDS = 200000;

    private void ensureInitialized() {
        if (!this.initialized) {
            this.vertexBuffer = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
            this.indexBuffer = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
            this.vertexArray = VertexArray.create(null);
            this.vertexBuffer.allocate(35048, 65536L);
            this.indexBuffer.allocate(35048, 16384L);
            this.initialized = true;
        }
    }

    public void enqueue(DrawCommand command) {
        if (this.commands.size() >= MAX_QUEUED_COMMANDS) {
            this.clear();
        }

        this.commands.add(new QueuedDraw(command, this.nextDepth));
        this.nextDepth = Math.nextUp(this.nextDepth);
    }

    public void flush(CameraMatrices context) {
        if (this.commands.isEmpty()) {
            this.nextDepth = 0.0F;
        } else {
            boolean scissorEnabled = false;
            try {
                this.ensureInitialized();
                int batchStart = 0;

                while (batchStart < this.commands.size()) {
                    DrawBatchKey key = ((QueuedDraw)this.commands.get(batchStart)).drawable().getBatchKey();
                    int batchEnd = batchStart + 1;

                    while (batchEnd < this.commands.size() && ((QueuedDraw)this.commands.get(batchEnd)).drawable().getBatchKey().equals(key)) {
                        batchEnd++;
                    }

                    List batch = this.commands.subList(batchStart, batchEnd);
                    Vector4f scissor = key.scissorRect();
                    if (scissor != null) {
                        GL11.glEnable(3089);
                        scissorEnabled = true;
                        int framebufferHeight = RenderCore.getFramebufferHeight();
                        float scale = RenderCore.getScaleFactor();
                        GL11.glScissor((int)(scissor.x * scale), (int)(framebufferHeight - scissor.y * scale - scissor.w * scale), (int)(scissor.z * scale), (int)(scissor.w * scale));
                    }

                    DrawPipeline pipeline = key.drawMode();
                    this.vertexArray
                        .setBindings(VertexArray.createBindings(this.vertexBuffer, pipeline.getVertexFormat()));
                    MappedMeshBuilder builder = new MappedMeshBuilder(pipeline.getVertexFormat(), this.vertexBuffer, this.indexBuffer);

                    int indexCount;
                    int vertexCount;
                    try {
                        for (QueuedDraw entry : (Iterable<QueuedDraw>)(batch)) {
                            entry.drawable().emitVertices(builder, entry.depth());
                        }

                        vertexCount = builder.getVertexCount();
                        indexCount = builder.getIndexCount();
                    } catch (Throwable failure) {
                        try {
                            builder.close();
                        } catch (Throwable closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }

                        throw failure;
                    }

                    builder.close();
                    pipeline.draw(context, this.vertexArray, this.indexBuffer, new MeshInfo(pipeline.getVertexFormat(), vertexCount, indexCount));
                    if (scissor != null) {
                        GL11.glDisable(3089);
                        scissorEnabled = false;
                    }

                    batchStart = batchEnd;
                }
            } finally {
                if (scissorEnabled) {
                    GL11.glDisable(3089);
                }

                this.clear();
            }
        }
    }

    public void clear() {
        this.nextDepth = 0.0F;
        this.commands.clear();
    }
}

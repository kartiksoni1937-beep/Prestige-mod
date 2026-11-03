package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.DrawModeBuffers;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.MeshInfo;
import it.unimi.dsi.fastutil.Pair;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferSource.class);
    private final WeakHashMap buffersByMode = new WeakHashMap();
    private final Set pendingModes = new LinkedHashSet();
    public static final boolean ASSERTIONS_DISABLED = !BufferSource.class.desiredAssertionStatus();
    private static final String MISSING_BUILDER_MESSAGE = "empty buffer but we have a record of it being requested. huh?";
    private static final long INDEX_SIZE_BYTES = 4L;

    public MappedMeshBuilder getBuffer(DrawPipeline drawMode) {
        return this.getBuffer(drawMode, 128, 128);
    }

    public MappedMeshBuilder getBuffer(DrawPipeline drawMode, int vertexCapacity, int indexCapacity) {
        this.pendingModes.add(drawMode);
        DrawModeBuffers bufferData = (DrawModeBuffers)this.buffersByMode.get(drawMode);
        if (bufferData != null) {
            if (bufferData.activeBuilder().get() != null) {
                return (MappedMeshBuilder)bufferData.activeBuilder().get();
            } else {
                MappedMeshBuilder builder = new MappedMeshBuilder(drawMode.vertexFormat, bufferData.vertexBuffer(), bufferData.indexBuffer());
                bufferData.activeBuilder().set(builder);
                return builder;
            }
        } else {
            GlBuffer vertexBuffer = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
            GlBuffer indexBuffer = (GlBuffer)RenderCore.callOnRenderThread((java.util.function.Supplier)GlBuffer::create);
            vertexBuffer.allocate(35048, (long)vertexCapacity * drawMode.vertexFormat.stride);
            indexBuffer.allocate(35048, indexCapacity * INDEX_SIZE_BYTES);
            MappedMeshBuilder newBuilder = new MappedMeshBuilder(drawMode.vertexFormat, vertexBuffer, indexBuffer);
            VertexArray vertexArray = VertexArray.forBuffer(vertexBuffer, drawMode.vertexFormat);
            this.buffersByMode.put(drawMode, new DrawModeBuffers(vertexBuffer, indexBuffer, vertexArray, new AtomicReference<>(newBuilder)));
            return newBuilder;
        }
    }

    private void draw(CameraMatrices context, DrawPipeline drawMode) {
        DrawModeBuffers bufferData = (DrawModeBuffers)this.buffersByMode.get(drawMode);
        if (!ASSERTIONS_DISABLED && bufferData == null) {
            throw new AssertionError();
        } else {
            MappedMeshBuilder builder = (MappedMeshBuilder)bufferData.activeBuilder().get();
            if (!ASSERTIONS_DISABLED && builder == null) {
                throw new AssertionError(MISSING_BUILDER_MESSAGE);
            } else if (builder == null) {
                return;
            } else {
                int indexCount = builder.getIndexCount();
                int vertexCount = builder.getVertexCount();
                builder.close();
                bufferData.activeBuilder().set(null);
                ARBVertexArrayObject.glBindVertexArray(bufferData.vertexArray().getGlId());
                drawMode.draw(context, bufferData.vertexArray(), bufferData.indexBuffer(), new MeshInfo(drawMode.vertexFormat, vertexCount, indexCount));
                ARBVertexArrayObject.glBindVertexArray(0);
            }
        }
    }

    public void flush(CameraMatrices context) {
        try {
            for (DrawPipeline drawMode : (Iterable<DrawPipeline>)(this.pendingModes)) {
                try {
                    this.draw(context, drawMode);
                } catch (Throwable throwable) {
                    LOGGER.error("Failed to draw buffered pipeline", throwable);
                }
            }
        } finally {
            this.pendingModes.clear();
        }

        this.buffersByMode.size();
    }

    public Stream streamActiveBuffers() {
        return this.pendingModes.stream().map(drawMode -> Pair.of(((dev.zprestige.prestige.render.DrawPipeline)drawMode), (MappedMeshBuilder)((DrawModeBuffers)this.buffersByMode.get(((dev.zprestige.prestige.render.DrawPipeline)drawMode))).activeBuilder().get()));
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.world.MeshBuilder;
import java.lang.ref.Cleaner.Cleanable;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public class MappedMeshBuilder implements MeshBuilder, AutoCloseable {
    public VertexLayout format;
    private final GlBuffer vertexBuffer;
    private final GlBuffer indexBuffer;
    private final Cleanable cleanable;
    private ByteBuffer vertexData;
    private ByteBuffer indexData;
    private final Vector4f transformScratch;
    private int baseVertex;

    public MappedMeshBuilder(VertexLayout initialFormat, GlBuffer vbo, GlBuffer ibo) {
        super();
        this.transformScratch = new Vector4f();
        this.baseVertex = 0;
        this.format = initialFormat;
        this.vertexBuffer = vbo;
        this.indexBuffer = ibo;
        this.vertexData = vbo.mapRange(0L, vbo.getSize().get(), 10);
        this.indexData = ibo.mapRange(0L, ibo.getSize().get(), 10);
        this.cleanable = RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> {
            vbo.unmap();
            ibo.unmap();
        }));
    }

    @Override
    public int getBaseVertex() {
        return this.baseVertex;
    }

    @Override
    public void ensureVertexCapacity(int bytesNeeded) {
        if (this.vertexData.remaining() < bytesNeeded) {
            int oldCapacity = this.vertexData.limit();
            int newCapacity = Math.max(oldCapacity << 1, oldCapacity + (bytesNeeded - this.vertexData.remaining()));
            int position = this.vertexData.position();
            ByteBuffer backup = MemoryUtil.memAlloc(position);
            this.vertexData.flip();
            MemoryUtil.memCopy(this.vertexData, backup);
            this.vertexBuffer.unmap();
            GlBuffer gpuBuffer = this.vertexBuffer;
            int usage = this.vertexBuffer.getUsage();
            long newSize = newCapacity;
            gpuBuffer.allocate(usage, newSize);
            GlBuffer remapTarget = this.vertexBuffer;
            long mapLength = newCapacity;
            this.vertexData = remapTarget.mapRange(0L, mapLength, 10);
            MemoryUtil.memCopy(backup, this.vertexData);
            this.vertexData.position(position);
            MemoryUtil.memFree(backup);
        }
    }

    @Override
    public void ensureIndexCapacity(int bytesNeeded) {
        if (this.indexData.remaining() < bytesNeeded) {
            int oldCapacity = this.indexData.limit();
            int newCapacity = Math.max(oldCapacity << 1, oldCapacity + (bytesNeeded - this.indexData.remaining()));
            int position = this.indexData.position();
            ByteBuffer backup = MemoryUtil.memAlloc(position);
            this.indexData.flip();
            MemoryUtil.memCopy(this.indexData, backup);
            this.indexBuffer.unmap();
            GlBuffer gpuBuffer = this.indexBuffer;
            int usage = this.indexBuffer.getUsage();
            long newSize = newCapacity;
            gpuBuffer.allocate(usage, newSize);
            GlBuffer remapTarget = this.indexBuffer;
            long mapLength = newCapacity;
            this.indexData = remapTarget.mapRange(0L, mapLength, 10);
            MemoryUtil.memCopy(backup, this.indexData);
            this.indexData.position(position);
            MemoryUtil.memFree(backup);
        }
    }

    public MappedMeshBuilder appendFloats(float[] values) {
        this.ensureVertexCapacity(4 * values.length);

        for (float value : values) {
            this.vertexData.putFloat(value);
        }

        return this;
    }

    public MappedMeshBuilder appendPosition(float x, float y, float z) {
        this.ensureVertexCapacity(12);
        this.vertexData.putFloat(x);
        this.vertexData.putFloat(y);
        this.vertexData.putFloat(z);
        return this;
    }

    public MappedMeshBuilder appendTransformedPosition(Matrix4f matrix, float x, float y, float z) {
        matrix.transform(x, y, z, 1.0F, this.transformScratch);
        this.ensureVertexCapacity(12);
        this.vertexData.putFloat(this.transformScratch.x);
        this.vertexData.putFloat(this.transformScratch.y);
        this.vertexData.putFloat(this.transformScratch.z);
        return this;
    }

    public MappedMeshBuilder appendInt(int value) {
        this.ensureVertexCapacity(4);
        this.vertexData.putInt(value);
        return this;
    }

    public MappedMeshBuilder appendIndices(int[] indices) {
        this.ensureIndexCapacity(4 * indices.length);

        for (int index : indices) {
            this.indexData.putInt(this.baseVertex + index);
        }

        return this;
    }

    @Override
    public int getVertexCount() {
        return this.vertexData.position() / this.format.stride;
    }

    @Override
    public int getIndexCount() {
        return this.indexData.position() / 4;
    }

    public MappedMeshBuilder clearBaseVertex() {
        this.baseVertex = 0;
        return this;
    }

    public MappedMeshBuilder withBaseVertex(int newBaseVertex) {
        this.baseVertex = newBaseVertex;
        return this;
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    public void setFormat(VertexLayout newFormat) {
        this.format = newFormat;
    }

    @Override
    public MeshBuilder begin() {
        throw new UnsupportedOperationException("removed by Strip (unresolved ZKM reference)");
    }

    @Override
    public MeshBuilder end() {
        throw new UnsupportedOperationException("removed by Strip (unresolved ZKM reference)");
    }

    @Override
    public MeshBuilder setBaseVertex(int newBaseVertex) {
        return this.withBaseVertex(newBaseVertex);
    }

    @Override
    public MeshBuilder resetBaseVertex() {
        return this.clearBaseVertex();
    }

    @Override
    public MeshBuilder putIndices(int[] indices) {
        return this.appendIndices(indices);
    }

    @Override
    public MeshBuilder putColor(int value) {
        return this.appendInt(value);
    }

    @Override
    public MeshBuilder putTransformedVertex(Matrix4f matrix, float x, float y, float z) {
        return this.appendTransformedPosition(matrix, x, y, z);
    }

    @Override
    public MeshBuilder putVertex(float x, float y, float z) {
        return this.appendPosition(x, y, z);
    }

    @Override
    public MeshBuilder putFloats(float[] values) {
        return this.appendFloats(values);
    }
}

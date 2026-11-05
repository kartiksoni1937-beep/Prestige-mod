package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.DrawBatchKey;
import dev.zprestige.prestige.world.MeshBuilder;
import org.joml.Matrix4f;

public final class GlyphQuadCommand implements DrawCommand {
    private final DrawBatchKey batchKey;
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;
    private final float minU;
    private final float minV;
    private final float maxU;
    private final float maxV;
    private final int abgrColor;
    private final Matrix4f transform;

    public GlyphQuadCommand(
        DrawBatchKey key, float x0, float y0, float x1, float y1, int argb, float u0, float v0, float u1, float v1, Matrix4f matrix
    ) {
        this.batchKey = key;
        this.left = x0;
        this.top = y0;
        this.right = x1;
        this.bottom = y1;
        this.minU = u0;
        this.minV = v0;
        this.maxU = u1;
        this.maxV = v1;
        this.transform = matrix;
        this.abgrColor = (argb >>> 24 & 0xFF) << 24 | (argb & 0xFF) << 16 | (argb >>> 8 & 0xFF) << 8 | (argb >>> 16 & 0xFF);
    }

    @Override
    public void emitVertices(MeshBuilder builder, float depth) {
        builder.beginShape();
        if (this.transform == null) {
            builder.putVertex(this.left, this.bottom, depth).putColor(this.abgrColor).putFloats(new float[]{this.minU, this.maxV}).putVertex(this.left, this.top, depth).putColor(this.abgrColor).putFloats(new float[]{this.minU, this.minV}).putVertex(this.right, this.top, depth).putColor(this.abgrColor).putFloats(new float[]{this.maxU, this.minV}).putVertex(this.right, this.bottom, depth).putColor(this.abgrColor).putFloats(new float[]{this.maxU, this.maxV});
        } else {
            builder.putTransformedVertex(this.transform, this.left, this.bottom, depth).putColor(this.abgrColor).putFloats(new float[]{this.minU, this.maxV}).putTransformedVertex(this.transform, this.left, this.top, depth).putColor(this.abgrColor).putFloats(new float[]{this.minU, this.minV}).putTransformedVertex(this.transform, this.right, this.top, depth).putColor(this.abgrColor).putFloats(new float[]{this.maxU, this.minV}).putTransformedVertex(this.transform, this.right, this.bottom, depth).putColor(this.abgrColor).putFloats(new float[]{this.maxU, this.maxV});
        }

        builder.putIndices(new int[]{0, 1, 2, 2, 3, 0}).resetBaseVertex();
    }

    @Override
    public DrawBatchKey getBatchKey() {
        return this.batchKey;
    }
}

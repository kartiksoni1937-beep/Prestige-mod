package dev.zprestige.prestige.data;

import dev.zprestige.prestige.world.MeshBuilder;
import net.minecraft.client.render.VertexConsumer;

public record OutlineCaptureConsumer(MeshBuilder writer) implements VertexConsumer {
    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        this.writer.putVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.writer.putColor(-16776961);
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        this.writer.putColor(-16776961);
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        this.writer.putFloats(new float[]{u, v});
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }
}

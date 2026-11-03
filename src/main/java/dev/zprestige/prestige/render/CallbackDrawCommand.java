package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.DrawBatchKey;
import dev.zprestige.prestige.world.MeshBuilder;
import java.util.function.BiConsumer;
import org.joml.Vector4f;

public final class CallbackDrawCommand implements DrawCommand {
    private final BiConsumer vertexEmitter;
    private final DrawBatchKey batchKey;

    public CallbackDrawCommand(DrawPipeline pipeline, Vector4f scissor, BiConsumer emitter) {
        this.vertexEmitter = emitter;
        this.batchKey = new DrawBatchKey(scissor, pipeline);
    }

    public CallbackDrawCommand(DrawPipeline pipeline, BiConsumer emitter) {
        this(pipeline, null, emitter);
    }

    @Override
    public void emitVertices(MeshBuilder builder, float depth) {
        this.vertexEmitter.accept(depth, builder);
    }

    @Override
    public DrawBatchKey getBatchKey() {
        return this.batchKey;
    }
}

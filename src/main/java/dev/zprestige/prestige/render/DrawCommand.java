package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.DrawBatchKey;

public interface DrawCommand {
    void emitVertices(dev.zprestige.prestige.world.MeshBuilder builder, float depth);

    DrawBatchKey getBatchKey();
}

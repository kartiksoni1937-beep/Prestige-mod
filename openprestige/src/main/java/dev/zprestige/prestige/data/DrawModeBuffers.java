package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlBuffer;
import dev.zprestige.prestige.render.VertexArray;
import java.util.concurrent.atomic.AtomicReference;

public record DrawModeBuffers(GlBuffer vertexBuffer, GlBuffer indexBuffer, VertexArray vertexArray, AtomicReference activeBuilder) {
}

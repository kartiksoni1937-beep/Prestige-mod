package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlBuffer;

public record VertexAttributeBinding(GlBuffer buffer, VertexElement element, int stride, long offset) {
}

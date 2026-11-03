package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.DrawPipeline;
import org.joml.Vector4f;

public record DrawBatchKey(Vector4f scissorRect, DrawPipeline drawMode) {
}

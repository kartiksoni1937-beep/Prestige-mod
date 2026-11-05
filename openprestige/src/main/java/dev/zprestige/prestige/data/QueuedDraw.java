package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.DrawCommand;

public record QueuedDraw(DrawCommand drawable, float depth) {
}

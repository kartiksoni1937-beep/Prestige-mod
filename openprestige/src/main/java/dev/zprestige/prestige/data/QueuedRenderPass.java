package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlRenderPass;
import java.util.function.Consumer;

public record QueuedRenderPass(GlRenderPass renderPass, Consumer drawCallback) {
}

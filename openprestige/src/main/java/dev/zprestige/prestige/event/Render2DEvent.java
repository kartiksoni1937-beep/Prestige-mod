package dev.zprestige.prestige.event;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.render.BufferSource;

public class Render2DEvent extends Event {
    public final BufferSource renderer;
    public final CameraMatrices context;

    public Render2DEvent(BufferSource batchRenderer, CameraMatrices renderContext) {
        this.renderer = batchRenderer;
        this.context = renderContext;
    }
}

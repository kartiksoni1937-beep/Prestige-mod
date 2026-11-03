package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public class RenderLayerAccessor {
    private final RenderLayer renderLayer;
    private static final String RENDER_SETUP_FIELD = "renderSetup";

    public RenderLayerAccessor(RenderLayer layer) {
        this.renderLayer = layer;
    }

    public RenderSetup getRenderSetup() {
        return (RenderSetup)FieldAccessor.bindMapped(this.renderLayer, RENDER_SETUP_FIELD, RenderLayer.class, RenderSetup.class).get();
    }
}

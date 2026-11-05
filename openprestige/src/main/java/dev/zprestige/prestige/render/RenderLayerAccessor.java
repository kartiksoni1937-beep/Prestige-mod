package dev.zprestige.prestige.render;

import net.minecraft.client.render.RenderLayer;

public class RenderLayerAccessor {
    private final RenderLayer renderLayer;

    public RenderLayerAccessor(RenderLayer layer) {
        this.renderLayer = layer;
    }

    /** 1.21 RenderLayer has no RenderSetup; the phase texture is read reflectively. */
    public Object getRenderSetup() {
        try {
            java.lang.reflect.Field field = RenderLayer.class.getDeclaredField("renderSetup");
            field.setAccessible(true);
            return field.get(this.renderLayer);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

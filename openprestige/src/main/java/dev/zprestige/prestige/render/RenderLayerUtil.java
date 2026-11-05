package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.OutlineCaptureConsumer;
import java.util.Optional;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;

public class RenderLayerUtil {
    /** 1.21 RenderLayer does not expose its texture id; resolved from the phase texture when available. */
    public static Optional getTextureId(RenderLayer layer) {
        Object setup = new RenderLayerAccessor(layer).getRenderSetup();
        if (setup == null) {
            return Optional.empty();
        } else {
            try {
                java.lang.reflect.Method resolve = setup.getClass().getMethod("resolveTextures");
                java.util.Map textures = (java.util.Map)resolve.invoke(setup);
                for (Object value : textures.values()) {
                    java.lang.reflect.Method view = value.getClass().getMethod("textureView");
                    Object texture = view.invoke(value);
                    java.lang.reflect.Method glId = texture.getClass().getMethod("getGlId");
                    return Optional.of((Integer)glId.invoke(texture));
                }
            } catch (Throwable ignored) {
            }
            return Optional.empty();
        }
    }

    public static VertexConsumer withOutlineBuffer(VertexConsumer consumer, int textureId) {
        return VertexConsumers.union(consumer, new OutlineCaptureConsumer(EspShaderManager.getActiveShader().getMaskBuffer(RenderCore.BUFFER_SOURCE, textureId)));
    }
}

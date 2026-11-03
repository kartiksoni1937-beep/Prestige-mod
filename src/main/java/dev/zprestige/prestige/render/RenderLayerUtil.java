package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.OutlineCaptureConsumer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.RenderSetup.Texture;
import net.minecraft.client.texture.GlTextureView;

public class RenderLayerUtil {
    public static Optional getTextureId(RenderLayer layer) {
        RenderSetup setup = new RenderLayerAccessor(layer).getRenderSetup();
        if (setup == null) {
            return Optional.empty();
        } else {
            Map textures = setup.resolveTextures();
            ArrayList textureIds = new ArrayList();

            for (Entry entry : (Iterable<Entry>)(textures.entrySet())) {
                if (((Texture)entry.getValue()).textureView() instanceof GlTextureView glView) {
                    textureIds.add(glView.texture().getGlId());
                }
            }

            return textureIds.isEmpty() ? Optional.empty() : Optional.of((Integer)textureIds.get(0));
        }
    }

    public static VertexConsumer withOutlineBuffer(VertexConsumer consumer, int textureId) {
        return VertexConsumers.union(consumer, new OutlineCaptureConsumer(EspShaderManager.getActiveShader().getMaskBuffer(RenderCore.BUFFER_SOURCE, textureId)));
    }
}

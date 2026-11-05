package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FullBrightEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * 1.21 keeps the lightmap as a {@link NativeImageBackedTexture} + {@link NativeImage};
 * there is no GpuTexture/RenderSystem device. Full bright fills the 16x16 image white and uploads it.
 */
@CTransformer({LightmapTextureManager.class})
public class LightmapTextureManagerMixin {
    @CShadow("texture")
    private NativeImageBackedTexture texture;
    @CShadow("image")
    private NativeImage image;

    @CInject(
        method = {"update"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void forceFullBrightLightmapAtUpdateHead(float tickProgress, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasLightmapListeners(Prestige.eventBus, FullBrightEvent.class)) {
            FullBrightEvent event = HookBridge.createLightmapEvent();
            if (HookBridge.postLightmapEvent(event)) {
                this.image.fillRect(0, 0, 16, 16, -1);
                this.texture.upload();
                ci.cancel();
            }
        }
    }
}

package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SkyColorEvent;
import dev.zprestige.prestige.util.HookBridge;
import java.awt.Color;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;

@CTransformer({SkyRendering.class})
public class SkyRenderingMixin {
    @CInject(
        method = {"updateRenderState"},
        target = {@CTarget("TAIL")}
    )
    private void overrideSkyColorAtUpdateRenderStateTail(ClientWorld world, float tickDelta, Camera camera, SkyRenderState state, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasSkyColorListeners(Prestige.eventBus, SkyColorEvent.class)) {
            int skyPacked = state.skyColor;
            Color initialSky = HookBridge.createSkyColor(skyPacked >> 16 & 0xFF, skyPacked >> 8 & 0xFF, skyPacked & 0xFF);
            int hzPacked = state.sunriseAndSunsetColor;
            Color initialHorizon = HookBridge.createHorizonColor(hzPacked >> 16 & 0xFF, hzPacked >> 8 & 0xFF, hzPacked & 0xFF);
            SkyColorEvent event = HookBridge.createSkyColorEvent(initialSky, initialHorizon);
            if (HookBridge.postSkyColorEvent(event)) {
                if (HookBridge.getSkyColorOrNull(event) != null) {
                    Color skyColor = HookBridge.getSkyColor(event);
                    state.skyColor = 0xFF000000 | (skyColor.getRed() & 0xFF) << 16 | (skyColor.getGreen() & 0xFF) << 8 | skyColor.getBlue() & 0xFF;
                }

                if (HookBridge.getHorizonColorOrNull(event) != null) {
                    Color horizonColor = HookBridge.getHorizonColor(event);
                    int origAlpha = hzPacked >> 24 & 0xFF;
                    state.sunriseAndSunsetColor = origAlpha << 24 | (horizonColor.getRed() & 0xFF) << 16 | (horizonColor.getGreen() & 0xFF) << 8 | horizonColor.getBlue() & 0xFF;
                }
            }
        }
    }
}

package dev.zprestige.prestige.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FogEvent;
import dev.zprestige.prestige.util.HookBridge;
import java.awt.Color;
import java.nio.ByteBuffer;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;

@CTransformer({FogRenderer.class})
public class FogRendererMixin {
    @CInject(
        method = {"applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void overrideFogBufferAtApplyFogHead(
        ByteBuffer buffer,
        int bufPos,
        Vector4f fogColor,
        float environmentalStart,
        float environmentalEnd,
        float renderDistanceStart,
        float renderDistanceEnd,
        float skyEnd,
        float cloudEnd,
        InjectionCallback ci
    ) {
        if (Prestige.eventBus != null && HookBridge.hasFogBufferListeners(Prestige.eventBus, FogEvent.class)) {
            FogEvent event = HookBridge.createFogBufferEvent(0.0F, 0.0F, Color.WHITE);
            if (HookBridge.postFogBufferEvent(event)) {
                Vector4f color = HookBridge.createFogBufferColor(
                    HookBridge.getFogColorForRed(event).getRed() / 255.0F,
                    HookBridge.getFogColorForGreen(event).getGreen() / 255.0F,
                    HookBridge.getFogColorForBlue(event).getBlue() / 255.0F,
                    HookBridge.getFogColorForAlpha(event).getAlpha() / 255.0F
                );
                Float skyOv = HookBridge.getFogSkyEndOverride(event);
                float effectiveSkyEnd = skyOv == null ? skyEnd : skyOv;
                buffer.position(bufPos);
                Std140Builder.intoBuffer(buffer)
                    .putVec4(color)
                    .putFloat(HookBridge.getFogEnvironmentalStart(event))
                    .putFloat(HookBridge.getFogEnvironmentalEnd(event))
                    .putFloat(HookBridge.getFogRenderDistanceStart(event))
                    .putFloat(HookBridge.getFogRenderDistanceEnd(event))
                    .putFloat(effectiveSkyEnd)
                    .putFloat(cloudEnd);
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"},
        target = {@CTarget("TAIL")},
        cancellable = true
    )
    private void overrideFogColorAtApplyFogTail(Camera camera, int viewDistance, RenderTickCounter tickCounter, float tickProgress, ClientWorld world, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasFogColorListeners(Prestige.eventBus, FogEvent.class)) {
            FogEvent event = HookBridge.createFogColorEvent(0.0F, 0.0F, Color.WHITE);
            if (HookBridge.postFogColorEvent(event)) {
                Color fogColor = HookBridge.getFogColor(event);
                ci.setReturnValue(HookBridge.createFogColorVector(fogColor.getRed() / 255.0F, fogColor.getGreen() / 255.0F, fogColor.getBlue() / 255.0F, fogColor.getAlpha() / 255.0F));
            }
        }
    }
}

package dev.zprestige.prestige.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.WorldRendererReloadEvent;
import dev.zprestige.prestige.render.GlStateSnapshot;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.util.HookBridge;
import java.util.List;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 1.21 world pass: {@code render(RenderTickCounter, boolean, Camera, GameRenderer, LightmapTextureManager, Matrix4f, Matrix4f)}.
 * FrameGraphBuilder/ObjectAllocator only exist from 1.21.2, so the pass hook runs at RETURN of the same method.
 */
@CTransformer({WorldRenderer.class})
public class WorldRendererMixin {
    @CShadow("capturedFrustum")
    private Frustum frustum;

    @CInject(
        method = {"render"},
        target = {@CTarget("RETURN")}
    )
    public void renderWorldPassAtRenderReturn(
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f projectionMatrix,
        Matrix4f actualRealProjMatrix
    ) {
        if (Prestige.eventBus != null) {
            List<Runnable> delayedActions = HookBridge.drainWorldRenderTasks();
            boolean renderWork = HookBridge.hasWorldRenderWork();
            boolean hudWork = HookBridge.hasHudRenderWorkInWorldPass();
            if (hudWork) {
                Render2D.projectionMatrix.set(actualRealProjMatrix);
                if (!renderWork) {
                    HookBridge.captureViewport();
                }
            }

            if (renderWork || !delayedActions.isEmpty()) {
                GlStateSnapshot cap = HookBridge.captureWorldGlState();
                boolean renderFailed = false /* VF: Semaphore variable */;

                label53: {
                    try {
                        renderFailed = true;
                        HookBridge.runWorldRenderTasks(delayedActions);
                        if (!renderWork) {
                            renderFailed = false;
                            break label53;
                        }

                        Matrix4fStack actualRealModView = RenderSystem.getModelViewStack();
                        CameraMatrices context = HookBridge.createWorldRenderContext(actualRealProjMatrix, HookBridge.copyModelViewMatrix(actualRealModView), camera);
                        HookBridge.renderWorldPass(context);
                        renderFailed = false;
                    } finally {
                        if (renderFailed) {
                            HookBridge.restoreWorldGlStateOnError(cap);
                        }
                    }

                    HookBridge.restoreWorldGlState(cap);
                    return;
                }

                HookBridge.restoreWorldGlStateAfterTasks(cap);
            }
        }
    }

    @CInject(
        method = {"setupFrustum"},
        target = {@CTarget("TAIL")}
    )
    private void postFrustumEventAtSetupFrustumTail(Vec3d vec3d, Matrix4f matrix4f, Matrix4f matrix4f2, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasFrustumListeners(Prestige.eventBus, FrustumSetupEvent.class)) {
            FrustumSetupEvent event = HookBridge.createFrustumEvent(this.frustum);
            HookBridge.postFrustumEvent(event);
        }
    }

    @CInject(
        method = {"reload"},
        target = {@CTarget("HEAD")}
    )
    private void postWorldRendererReloadEventAtHead(InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasWorldRendererReloadListeners(Prestige.eventBus, WorldRendererReloadEvent.class)) {
            HookBridge.postWorldRendererReloadEvent(HookBridge.createWorldRendererReloadEvent());
        }
    }
}

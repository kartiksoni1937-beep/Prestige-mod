package dev.zprestige.prestige.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

@CTransformer({WorldRenderer.class})
public class WorldRendererMixin {
    @CShadow("capturedFrustum")
    private Frustum frustum;

    // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
    // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
    @CInject(
        method = {"render"},
        target = {@CTarget(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V",
            shift = CTarget.Shift.AFTER
        )}
    )
    public void renderWorldPassAfterFrameGraphRun(
        ObjectAllocator allocator,
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        Matrix4f theCameraRotation,
        Matrix4f actualRealProjMatrix,
        Matrix4f projectionMatrix,
        GpuBufferSlice fogBuffer,
        Vector4f fogColor,
        boolean renderSky
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
        target = {@CTarget("RETURN")}
    )
    private void postFrustumEventAtSetupFrustumReturn(Matrix4f matrix4f, Matrix4f matrix4f2, Vec3d vec3d, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasFrustumListeners(Prestige.eventBus, FrustumSetupEvent.class)) {
            FrustumSetupEvent event = HookBridge.createFrustumEvent((Frustum)ci.getReturnValue());
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

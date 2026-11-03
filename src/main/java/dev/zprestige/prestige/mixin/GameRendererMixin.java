package dev.zprestige.prestige.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.ReachEvent;
import dev.zprestige.prestige.event.FloatingItemTickEvent;
import dev.zprestige.prestige.event.HurtCamEvent;
import dev.zprestige.prestige.render.GlStateSnapshot;
import dev.zprestige.prestige.util.HookBridge;
import java.util.List;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import org.joml.Matrix4f;

@CTransformer({GameRenderer.class})
public class GameRendererMixin {
    @CShadow("client")
    public MinecraftClient client;
    @CShadow("guiRenderer")
    private GuiRenderer guiRenderer;

    // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
    // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
    @CInject(
        method = {"render"},
        target = {@CTarget("TAIL")}
    )
    public void renderHudPassAtRenderTail(RenderTickCounter tickCounter, boolean tick) {
        List<Runnable> delayedActions = HookBridge.drainHudRenderTasks();
        boolean renderWork = Prestige.eventBus != null && HookBridge.hasHudRenderWork();
        if (!renderWork && delayedActions.isEmpty()) {
            HookBridge.advanceFrameCounter();
            HookBridge.fireIdleFrameEvent();
        } else {
            GlStateSnapshot cap = HookBridge.captureHudGlState();
            boolean renderFailed = false /* VF: Semaphore variable */;

            label53: {
                try {
                    renderFailed = true;
                    HookBridge.runHudRenderTasks(delayedActions);
                    if (!renderWork) {
                        HookBridge.advanceFrameCounterAfterTasks();
                        HookBridge.fireIdleFrameEventAfterTasks();
                        renderFailed = false;
                        break label53;
                    }

                    Window window = this.client.getWindow();
                    Matrix4f proj = HookBridge.getProjectionMatrix(
                        HookBridge.createProjectionMatrixAccessor(HookBridge.getGuiProjectionMatrix(HookBridge.createGuiRendererAccessor(this.guiRenderer))),
                        (float)window.getFramebufferWidth() / window.getScaleFactor(),
                        (float)window.getFramebufferHeight() / window.getScaleFactor()
                    );
                    CameraMatrices context = HookBridge.createHudRenderContext(proj, HookBridge.createHudModelViewMatrix().setTranslation(0.0F, 0.0F, -10000.0F), null);
                    HookBridge.renderHudPass(context);
                    renderFailed = false;
                } finally {
                    if (renderFailed) {
                        HookBridge.restoreHudGlStateOnError(cap);
                    }
                }

                HookBridge.restoreHudGlState(cap);
                return;
            }

            HookBridge.restoreHudGlStateAfterTasks(cap);
        }
    }

    @CInject(
        target = {@CTarget("HEAD")},
        method = {"renderWorld"}
    )
    private void captureCameraMatricesAtRenderWorldHead(RenderTickCounter tickCounter) {
        if (HookBridge.shouldCaptureCameraMatrices()) {
            Camera camera = this.client.gameRenderer.getCamera();
            MatrixStack matrixStack = HookBridge.createCameraMatrixStack();
            RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
            try {
                matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
                dev.zprestige.prestige.render.Render2D.modelViewMatrix.set(RenderSystem.getModelViewMatrix());
                dev.zprestige.prestige.render.Render2D.cameraRotationMatrix.set(matrixStack.peek().getPositionMatrix());
            } finally {
                RenderSystem.getModelViewStack().popMatrix();
            }
        }
    }

    @CInject(
        target = {@CTarget("HEAD")},
        method = {"tiltViewWhenHurt"},
        cancellable = true
    )
    private void cancelHurtCamAtTiltViewWhenHurtHead(MatrixStack matrices, float tickDelta, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasHurtCamListeners(Prestige.eventBus, HurtCamEvent.class)) {
            HurtCamEvent event = HookBridge.createHurtCamEvent();
            if (HookBridge.postHurtCamEvent(event)) {
                ci.cancel();
            }
        }
    }

    @CInject(
        target = {@CTarget("HEAD")},
        method = {"tick"}
    )
    private void addExtraFloatingItemTicksAtTickHead() {
        if (Prestige.eventBus != null && HookBridge.hasFloatingItemTickListeners(Prestige.eventBus, FloatingItemTickEvent.class)) {
            FloatingItemTickEvent event = HookBridge.createFloatingItemTickEvent(0);
            HookBridge.postFloatingItemTickEvent(event);

            for (int i = 0; i < HookBridge.getExtraFloatingItemTicks(event); i++) {
                HookBridge.getOverlayRenderer(HookBridge.createGameRendererAccessor((GameRenderer)(Object)this)).tickFloatingItemTimer();
            }
        }
    }

    @CInject(
        method = {"updateCrosshairTarget"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void applyReachAtUpdateCrosshairTargetHead(float tickDelta, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasReachListeners(Prestige.eventBus, ReachEvent.class)) {
            Entity entity2 = this.client.getCameraEntity();
            if (entity2 != null) {
                if (this.client.world != null && this.client.player != null) {
                    Profilers.get().push("pick");
                    this.client.targetedEntity = null;
                    double reach = HookBridge.getBaseReachDistance();
                    ReachEvent reachEvent = HookBridge.createReachEvent(0.0F);
                    boolean call = HookBridge.postReachEvent(reachEvent);
                    if (call) {
                        reach += HookBridge.getExtraReach(reachEvent);
                    }

                    this.client.crosshairTarget = entity2.raycast(reach, tickDelta, false);
                    Vec3d vec3d = entity2.getCameraPosVec(tickDelta);
                    boolean bl = false;
                    double entityReachSq = reach;
                    if (this.client.player.isCreative()) {
                        entityReachSq = 6.0;
                        reach = 6.0;
                    } else {
                        if (reach > 3.0 && !call) {
                            bl = true;
                        }

                        reach = reach;
                    }

                    entityReachSq *= entityReachSq;
                    if (this.client.crosshairTarget != null) {
                        entityReachSq = this.client.crosshairTarget.getPos().squaredDistanceTo(vec3d);
                    }

                    Vec3d vec3d2 = entity2.getRotationVec(1.0F);
                    Vec3d vec3d3 = vec3d.add(vec3d2.x * reach, vec3d2.y * reach, vec3d2.z * reach);
                    Box box = entity2.getBoundingBox().stretch(vec3d2.multiply(reach)).expand(1.0, 1.0, 1.0);
                    EntityHitResult entityHitResult = ProjectileUtil.raycast(entity2, vec3d, vec3d3, box, HookBridge.getTargetableEntityPredicate(), entityReachSq);
                    if (entityHitResult != null) {
                        Entity entity22 = entityHitResult.getEntity();
                        Vec3d vec3d4 = entityHitResult.getPos();
                        double entityDistanceSq = vec3d.squaredDistanceTo(vec3d4);
                        if (bl && entityDistanceSq > 9.0) {
                            this.client.crosshairTarget = BlockHitResult.createMissed(
                                vec3d4, Direction.getFacing(vec3d2.x, vec3d2.y, vec3d2.z), BlockPos.ofFloored(vec3d4)
                            );
                        } else if (entityDistanceSq < entityReachSq || this.client.crosshairTarget == null) {
                            this.client.crosshairTarget = entityHitResult;
                            if (entity22 instanceof LivingEntity || entity22 instanceof ItemFrameEntity) {
                                this.client.targetedEntity = entity22;
                            }
                        }
                    }

                    Profilers.get().pop();
                    ci.cancel();
                }
            }
        }
    }
}

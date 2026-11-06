package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PlayerRenderStateEvent;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.event.RenderLabelEvent;
import dev.zprestige.prestige.event.HealthIndicatorEvent;
import dev.zprestige.prestige.event.FullBrightEvent;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 1.21 has no EntityRenderState: the render state hooks live on
 * {@code render(T, float, float, MatrixStack, VertexConsumerProvider, int)} and the label
 * text is produced by {@code renderLabelIfPresent(T, Text, MatrixStack, VertexConsumerProvider, int, float)}.
 */
@CTransformer({EntityRenderer.class})
public abstract class EntityRendererMixin<T extends Entity> {
    // Guard state lives on the mixin instance, not as a field assumed to exist on EntityRenderer.
    private boolean suppressLabelInjectionRecurse;

    @CInject(
        method = {"render"},
        target = {@CTarget("HEAD")}
    )
    public void applyRenderRotationAtRenderHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity == MinecraftClient.getInstance().player) {
            if (Prestige.eventBus != null && Prestige.silentRotationManager != null && HookBridge.getRenderStateRotation(Prestige.silentRotationManager) != null && HookBridge.hasRenderStatePreListeners(Prestige.eventBus, PlayerRenderStateEvent.class)) {
                HookBridge.postRenderStatePreEvent(HookBridge.createRenderStatePreEvent(entity, EventPhase.PRE));
            }
        }
    }

    @CInject(
        method = {"render"},
        target = {@CTarget("RETURN")}
    )
    public void restoreRenderRotationAtRenderReturn(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity == MinecraftClient.getInstance().player) {
            if (Prestige.eventBus != null && Prestige.visualRotationManager != null && HookBridge.isRenderRotationApplied(Prestige.visualRotationManager) && HookBridge.hasRenderStatePostListeners(Prestige.eventBus, PlayerRenderStateEvent.class)) {
                HookBridge.postRenderStatePostEvent(HookBridge.createRenderStatePostEvent(entity, EventPhase.POST));
            }
        }
    }

    @CInject(
        method = {"getSkyLight"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void forceFullSkyLightAtReturn(T entity, BlockPos pos, InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasEntitySkyLightListeners(Prestige.eventBus, FullBrightEvent.class)) {
            FullBrightEvent event = HookBridge.createEntitySkyLightEvent();
            if (HookBridge.postEntitySkyLightEvent(event)) {
                cir.setReturnValue(15);
            }
        }
    }

    @CInject(
        method = {"renderLabelIfPresent"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void modifyNameLabelAtRenderLabelIfPresentHead(T entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta, InjectionCallback ci) {
        if (Prestige.eventBus != null && text != null && !this.suppressLabelInjectionRecurse) {
            boolean hasLabelText = HookBridge.hasLabelTextListeners(Prestige.eventBus, LabelTextEvent.class);
            boolean hasHealthIndicators = HookBridge.hasHealthIndicatorListeners(Prestige.eventBus, HealthIndicatorEvent.class);
            boolean hasRenderLabel = HookBridge.hasRenderLabelListeners(Prestige.eventBus, RenderLabelEvent.class);
            if (hasLabelText || hasHealthIndicators || hasRenderLabel) {
                Text originalDisplayName = text;
                Text displayName = originalDisplayName;
                if (entity instanceof PlayerEntity player && entity.isAlive() && (hasLabelText || hasHealthIndicators)) {
                    LabelTextEvent labelTextEvent = HookBridge.createLabelTextEvent(entity, originalDisplayName);
                    boolean labelTextCancelled = HookBridge.postLabelTextEvent(labelTextEvent);
                    displayName = HookBridge.getLabelText(labelTextEvent);
                    if (displayName == null) {
                        displayName = originalDisplayName;
                    }
                    if (!labelTextCancelled) {
                        HealthIndicatorEvent event = HookBridge.createHealthIndicatorEvent(player, "");
                        if (HookBridge.postHealthIndicatorEvent(event)) {
                            displayName = Text.of(displayName.getString() + HookBridge.getHealthIndicatorSuffix(event));
                        }
                    }
                }

                if (hasRenderLabel) {
                    RenderLabelEvent event = HookBridge.createRenderLabelEvent(originalDisplayName, displayName, entity);
                    if (HookBridge.postRenderLabelEvent(event)) {
                        ci.cancel();
                        return;
                    }
                }

                if (displayName != null && !displayName.equals(originalDisplayName) && !displayName.getString().isEmpty()) {
                    this.suppressLabelInjectionRecurse = true;
                    try {
                        this.prestige$reRenderLabel(entity, displayName, matrices, vertexConsumers, light, tickDelta);
                    } finally {
                        this.suppressLabelInjectionRecurse = false;
                    }
                    ci.cancel();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void prestige$reRenderLabel(T entity, Text displayName, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        try {
            java.lang.reflect.Method method = EntityRenderer.class.getDeclaredMethod(
                "renderLabelIfPresent", Entity.class, Text.class, MatrixStack.class, VertexConsumerProvider.class, int.class, float.class
            );
            method.setAccessible(true);
            method.invoke(this, entity, displayName, matrices, vertexConsumers, light, tickDelta);
        } catch (Throwable ignored) {
        }
    }
}

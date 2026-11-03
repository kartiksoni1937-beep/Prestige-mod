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
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@CTransformer({EntityRenderer.class})
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @CInject(
        method = {"getAndUpdateRenderState"},
        target = {@CTarget("HEAD")}
    )
    public void applyRenderRotationAtGetAndUpdateRenderStateHead(T entity, float tickDelta) {
        if (entity == MinecraftClient.getInstance().player) {
            if (Prestige.eventBus != null && Prestige.silentRotationManager != null && HookBridge.getRenderStateRotation(Prestige.silentRotationManager) != null && HookBridge.hasRenderStatePreListeners(Prestige.eventBus, PlayerRenderStateEvent.class)) {
                HookBridge.postRenderStatePreEvent(HookBridge.createRenderStatePreEvent(entity, EventPhase.PRE));
            }
        }
    }

    @CInject(
        method = {"getAndUpdateRenderState"},
        target = {@CTarget("RETURN")}
    )
    public void restoreRenderRotationAtGetAndUpdateRenderStateReturn(T entity, float tickDelta) {
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
        method = {"updateRenderState"},
        target = {@CTarget("RETURN")}
    )
    public void modifyNameLabelAtUpdateRenderStateReturn(T entity, S state, float tickDelta) {
        if (Prestige.eventBus != null && state.displayName != null) {
            boolean hasLabelText = HookBridge.hasLabelTextListeners(Prestige.eventBus, LabelTextEvent.class);
            boolean hasHealthIndicators = HookBridge.hasHealthIndicatorListeners(Prestige.eventBus, HealthIndicatorEvent.class);
            boolean hasRenderLabel = HookBridge.hasRenderLabelListeners(Prestige.eventBus, RenderLabelEvent.class);
            if (hasLabelText || hasHealthIndicators || hasRenderLabel) {
                Text originalDisplayName = state.displayName;
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
                        state.displayName = null;
                        return;
                    }
                }

                state.displayName = displayName != null && !displayName.getString().isEmpty() ? displayName : null;
            }
        }
    }
}

package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.RenderCrosshairEvent;
import dev.zprestige.prestige.event.RenderStatusEffectsEvent;
import dev.zprestige.prestige.event.OverlayMessageEvent;
import dev.zprestige.prestige.event.RenderScoreboardEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

@CTransformer({InGameHud.class})
public class InGameHudMixin {
    @CInject(
        method = {"renderStatusEffectOverlay"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void hideStatusEffectOverlayAtHead(DrawContext context, RenderTickCounter tickCounter, InjectionCallback ci) {
        RenderStatusEffectsEvent event = HookBridge.createStatusEffectOverlayEvent();
        if (HookBridge.postStatusEffectOverlayEvent(event)) {
            ci.cancel();
        }
    }

    @CInject(
        method = {"renderCrosshair"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void hideCrosshairAtHead(DrawContext context, RenderTickCounter tickCounter, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasCrosshairRenderListeners(Prestige.eventBus, RenderCrosshairEvent.class)) {
            RenderCrosshairEvent event = HookBridge.createCrosshairRenderEvent();
            if (HookBridge.postCrosshairRenderEvent(event)) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"renderScoreboardSidebar"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void hideScoreboardSidebarAtHead(DrawContext context, RenderTickCounter counter, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasScoreboardRenderListeners(Prestige.eventBus, RenderScoreboardEvent.class)) {
            if (HookBridge.postScoreboardRenderEvent(HookBridge.createScoreboardRenderEvent())) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"renderOverlayMessage"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void hideOverlayMessageAtHead(DrawContext context, RenderTickCounter tickCounter, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasOverlayMessageListeners(Prestige.eventBus, OverlayMessageEvent.class)) {
            if (HookBridge.postOverlayMessageEvent(HookBridge.createOverlayMessageEvent())) {
                ci.cancel();
            }
        }
    }
}

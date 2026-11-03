package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.ModuleManager;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;

@CTransformer({MinecraftClient.class})
public class MinecraftClientMixin {
    @CInject(
        method = {"tick"},
        target = {@CTarget(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;tick(Z)V",
            shift = CTarget.Shift.AFTER
        )},
        cancellable = true
    )
    public void postClientTickEventAfterHudTick(InjectionCallback ci) {
        if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
            ModuleManager.headTickFallback = false;
            ClientTickEvent event = HookBridge.createClientTickEvent();
            if (HookBridge.postClientTickEvent(event)) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"tick"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void postFallbackClientTickEventAtTickHead(InjectionCallback ci) {
        if (ModuleManager.headTickFallback) {
            if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
                ClientTickEvent event = HookBridge.createFallbackClientTickEvent();
                if (HookBridge.postFallbackClientTickEvent(event)) {
                    ci.cancel();
                }
            }
        }
    }

    @CInject(
        method = {"tick"},
        target = {@CTarget("HEAD")}
    )
    public void postPreTickEventAtTickHead(InjectionCallback ci) {
        HookBridge.postPreTickEvent(HookBridge.createPreTickEvent());
    }

    @CInject(
        method = {"onResolutionChanged"},
        target = {@CTarget("TAIL")}
    )
    public void postResolutionChangedEventAtTail(InjectionCallback ci) {
        HookBridge.postResolutionChangedEvent(HookBridge.createResolutionChangedEvent(MinecraftClient.getInstance().getWindow()));
    }

    @CInject(
        method = {"doItemUse"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void postItemUseEventAtDoItemUseHead(InjectionCallback ci) {
        if (HookBridge.postItemUseEvent(HookBridge.createItemUseEvent())) {
            ci.cancel();
        }
    }

    @CInject(
        method = {"doAttack"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void postDoAttackEventAtHead(InjectionCallback cir) {
        if (HookBridge.postDoAttackEvent(HookBridge.createDoAttackEvent())) {
            cir.setReturnValue(false);
        }
    }

    @CInject(
        method = {"handleBlockBreaking"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void postHandleBlockBreakingEventAtHead(boolean bl, InjectionCallback ci) {
        if (HookBridge.postBlockBreakingEvent(HookBridge.createBlockBreakingEvent())) {
            ci.cancel();
        }
    }
}

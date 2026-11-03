package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ItemBobbingEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.item.ItemStack;

@CTransformer({ItemStack.class})
public class ItemStackMixin {
    @CInject(
        method = {"getBobbingAnimationTime"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void suppressGetBobbingAnimationTimeAtHead(InjectionCallback info) {
        if (Prestige.eventBus != null && HookBridge.hasGetBobbingTimeListeners(Prestige.eventBus, ItemBobbingEvent.class)) {
            if (HookBridge.postGetBobbingTimeEvent(HookBridge.createGetBobbingTimeEvent())) {
                info.setReturnValue(0);
            }
        }
    }

    @CInject(
        method = {"setBobbingAnimationTime"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void suppressSetBobbingAnimationTimeAtHead(int bobbingAnimationTime, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasSetBobbingTimeListeners(Prestige.eventBus, ItemBobbingEvent.class)) {
            if (HookBridge.postSetBobbingTimeEvent(HookBridge.createSetBobbingTimeEvent())) {
                ci.cancel();
            }
        }
    }
}

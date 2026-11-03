package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.TimeOfDayEvent;
import dev.zprestige.prestige.event.RainGradientEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.world.World;

@CTransformer({World.class})
public class WorldMixin {
    @CInject(
        method = {"getRainGradient(F)F"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overrideRainGradientAtReturn(float delta, InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasRainGradientListeners(Prestige.eventBus, RainGradientEvent.class)) {
            if (HookBridge.postRainGradientEvent(HookBridge.createRainGradientEvent())) {
                cir.setReturnValue(0.0F);
            }
        }
    }

    @CInject(
        method = {"getTimeOfDay"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overrideTimeOfDayAtReturn(InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasTimeOfDayListeners(Prestige.eventBus, TimeOfDayEvent.class)) {
            TimeOfDayEvent event = HookBridge.createTimeOfDayEvent((Long)cir.getReturnValue());
            if (HookBridge.postTimeOfDayEvent(event)) {
                cir.setReturnValue(HookBridge.getTimeOfDay(event));
            }
        }
    }
}

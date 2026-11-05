package dev.zprestige.prestige.ui.mixin;

import dev.zprestige.prestige.engine.EngineState;
import dev.zprestige.prestige.event.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts the events posted on the client EventBus per event type (EngineState.events); read with the "state engine" dev command. */
@Mixin(targets = "dev.zprestige.prestige.EventBus", remap = false)
public abstract class EventBusStatsMixin {
    // the descriptor must match EventBus.post(Event) exactly: Mixin resolves it by string, not through the compiler
    @Inject(method = "post(Ldev/zprestige/prestige/event/Event;)Z", at = @At("HEAD"), remap = false, require = 1)
    private void engine$count(Event event, CallbackInfoReturnable<Boolean> cir) {
        if (event != null) EngineState.onEvent(event.getClass());
    }
}

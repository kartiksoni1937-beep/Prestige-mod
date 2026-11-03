package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.KeyboardInputTickEvent;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.Vec2f;

@CTransformer({KeyboardInput.class})
public class KeyboardInputMixin extends Input {
    @CInject(
        method = {"tick"},
        target = {@CTarget("HEAD")}
    )
    private void postInputTickPreEventAtTickHead(InjectionCallback ci) {
        KeyboardInputTickEvent event = HookBridge.createInputTickPreEvent(EventPhase.PRE);
        HookBridge.postInputTickPreEvent(event);
    }

    @CInject(
        method = {"tick"},
        target = {@CTarget("RETURN")}
    )
    private void postInputTickPostEventAtTickReturn(InjectionCallback ci) {
        KeyboardInputTickEvent keyboardTickEvent = HookBridge.createInputTickPostEvent(EventPhase.POST);
        HookBridge.postInputTickPostEvent(keyboardTickEvent);
    }

    @CInject(
        method = {"tick"},
        target = {@CTarget("RETURN")}
    )
    private void blockInputDuringSwapAtTickReturn(InjectionCallback ci) {
        if (Prestige.inventorySwapQueue != null) {
            if (HookBridge.shouldBlockMovementInput(Prestige.inventorySwapQueue)) {
                this.playerInput = HookBridge.createPlayerInput(false, false, false, false, false, false, false);
                this.movementVector = Vec2f.ZERO;
            } else if (HookBridge.shouldRestoreSprint(Prestige.inventorySwapQueue) && this.hasForwardMovement()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.player.isSprinting()) {
                    client.player.setSprinting(true);
                }
            }
        }
    }
}

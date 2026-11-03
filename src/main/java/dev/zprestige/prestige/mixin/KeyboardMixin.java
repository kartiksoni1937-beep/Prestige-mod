package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.event.KeyEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;

@CTransformer({Keyboard.class})
public class KeyboardMixin {
    @CShadow("client")
    public MinecraftClient client;

    @CInject(
        method = {"onKey"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void postKeyEventAtHead(long window, int action, KeyInput input, InjectionCallback ci) {
        int key = input.getKeycode();
        if (key != -1) {
            KeyEvent event = HookBridge.createKeyEvent(key, action);
            if (HookBridge.postKeyEvent(event)) {
                ci.cancel();
            }
        }
    }
}

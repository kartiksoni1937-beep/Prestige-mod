package dev.zprestige.prestige.inject.hooks;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.Keyboard;

/** Injection-mode twin of ui.mixin.KeyboardHandlerMixin: swallows keyboard input while the overlay is visible. */
@CTransformer({Keyboard.class})
public class KeyboardHooks {
    @CInject(method = {"onKey"}, target = {@CTarget("HEAD")}, cancellable = true)
    private void key(long window, int key, int scancode, int action, int modifiers, InjectionCallback ci) {
        if (Overlay.onKey(key, action)) ci.cancel();
    }

    @CInject(method = {"onChar"}, target = {@CTarget("HEAD")}, cancellable = true)
    private void character(long window, int codePoint, int modifiers, InjectionCallback ci) {
        if (Overlay.onChar(codePoint)) ci.cancel();
    }
}

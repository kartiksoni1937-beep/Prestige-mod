package dev.zprestige.prestige.inject.hooks;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.Mouse;

/** Injection-mode twin of ui.mixin.MouseHandlerMixin: swallows mouse input while the overlay is visible. */
@CTransformer({Mouse.class})
public class MouseHooks {
    @CInject(method = {"onMouseButton"}, target = {@CTarget("HEAD")}, cancellable = true)
    private void button(long window, int button, int action, int mods, InjectionCallback ci) {
        if (Overlay.onMouseButton(button, action)) ci.cancel();
    }

    @CInject(method = {"onMouseScroll"}, target = {@CTarget("HEAD")}, cancellable = true)
    private void scroll(long window, double horizontal, double vertical, InjectionCallback ci) {
        if (Overlay.onScroll(vertical)) ci.cancel();
    }

    @CInject(method = {"onCursorPos"}, target = {@CTarget("HEAD")}, cancellable = true)
    private void move(long window, double x, double y, InjectionCallback ci) {
        if (Overlay.onMouseMove()) ci.cancel();
    }
}

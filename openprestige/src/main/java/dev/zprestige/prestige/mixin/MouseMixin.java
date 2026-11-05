package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.MouseUpdateEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.Mouse;

@CTransformer({Mouse.class})
public class MouseMixin {
    @CShadow("cursorDeltaX")
    public double cursorDeltaX;
    @CShadow("cursorDeltaY")
    public double cursorDeltaY;

    @CInject(
        method = {"updateMouse"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void postMouseMoveEventAtUpdateMouseHead(double timeDelta, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasMouseMoveListeners(Prestige.eventBus, MouseUpdateEvent.class)) {
            MouseUpdateEvent event = HookBridge.createMouseMoveEvent(this.cursorDeltaX, this.cursorDeltaY);
            if (HookBridge.postMouseMoveEvent(event)) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"onMouseButton"},
        target = {@CTarget("HEAD")}
    )
    private void postMouseButtonEventAtHead(long window, int button, int action, int mods, InjectionCallback ci) {
        if (button != -1) {
            HookBridge.postMouseButtonEvent(HookBridge.createMouseButtonEvent(button, action));
        }
    }
}

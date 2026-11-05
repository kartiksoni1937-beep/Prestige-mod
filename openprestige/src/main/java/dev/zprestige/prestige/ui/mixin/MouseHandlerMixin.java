package dev.zprestige.prestige.ui.mixin;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swallows mouse input while the overlay is visible. */
@Mixin(Mouse.class)
public abstract class MouseHandlerMixin {
    /** onMouseButton(window, button, action, mods) */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void prestigeui$button(long window, int button, int action, int mods, CallbackInfo ci) {
        if (Overlay.onMouseButton(button, action)) ci.cancel();
    }

    /** onMouseScroll(window, horizontal, vertical) */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void prestigeui$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Overlay.onScroll(vertical)) ci.cancel();
    }

    /** onCursorPos(window, x, y) */
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void prestigeui$move(long window, double x, double y, CallbackInfo ci) {
        if (Overlay.onMouseMove()) ci.cancel();
    }
}

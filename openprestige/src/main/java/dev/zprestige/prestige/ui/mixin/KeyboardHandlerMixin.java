package dev.zprestige.prestige.ui.mixin;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swallows keyboard input while the overlay is visible. */
@Mixin(Keyboard.class)
public abstract class KeyboardHandlerMixin {
    /** onKey(window, key, scancode, action, modifiers) */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void prestigeui$key(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (Overlay.onKey(key, action)) ci.cancel();
    }

    /** onChar(window, codePoint, modifiers) */
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void prestigeui$char(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (Overlay.onChar(codePoint)) ci.cancel();
    }
}

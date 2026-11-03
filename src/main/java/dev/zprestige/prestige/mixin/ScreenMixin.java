package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.event.ScreenBackgroundEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

@CTransformer({Screen.class})
public class ScreenMixin {
    @CInject(
        method = {"renderBackground"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void hideScreenBackgroundAtHead(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks, InjectionCallback ci) {
        ScreenBackgroundEvent event = HookBridge.createScreenBackgroundEvent();
        if (HookBridge.postScreenBackgroundEvent(event)) {
            ci.cancel();
        }
    }

    @CInject(
        method = {"renderBackgroundTexture"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private static void hideBackgroundTextureAtHead(DrawContext drawContext, Identifier texture, int x, int y, float u, float v, int width, int height, InjectionCallback ci) {
        ScreenBackgroundEvent event = HookBridge.createBackgroundTextureEvent();
        if (HookBridge.postBackgroundTextureEvent(event)) {
            ci.cancel();
        }
    }
}

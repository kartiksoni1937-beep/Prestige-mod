package dev.zprestige.prestige.inject.hooks;

import dev.zprestige.prestige.ui.mc.Overlay;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;

/** Injection-mode twin of ui.mixin.RenderSystemMixin: draws the overlay at the head of RenderSystem.flipFrame (Mojang-named, never remapped). */
@CTransformer(name = "com.mojang.blaze3d.systems.RenderSystem")
public class RenderSystemHooks {
    @CInject(method = {"flipFrame"}, target = {@CTarget("HEAD")})
    private static void overlay(InjectionCallback ci) {
        Overlay.render();
    }
}

package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.util.HookBridge;
import java.util.Optional;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.util.Identifier;

@CTransformer({ReloadableResourceManagerImpl.class})
public class ReloadableResourceManagerImplMixin {
    @CInject(
        method = {"getResource"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void serveCustomResourcesAtGetResourceHead(Identifier identifier, InjectionCallback callbackInfoReturnable) {
        if (identifier.getNamespace().equals("prestige")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadPrestigeResource(identifier)));
        }

        if (identifier.getPath().equals("shaders/post/glow.json")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadGlowPostJson("assets/prestige/shaders/post/glow.json")));
        }

        if (identifier.getPath().equals("shaders/program/glow.json")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadGlowProgramJson("assets/prestige/shaders/program/glow.json")));
        }

        if (identifier.getPath().equals("shaders/program/glow.fsh")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadGlowFragmentShader("assets/prestige/shaders/program/glow.fsh")));
        }

        if (identifier.getPath().equals("shaders/core/blur.json")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadBlurShaderJson("assets/minecraft/shaders/core/blur.json")));
        }

        if (identifier.getPath().equals("shaders/core/blur.fsh")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadBlurFragmentShader("assets/minecraft/shaders/core/blur.fsh")));
        }

        if (identifier.getPath().equals("shaders/core/rounded_rect.json")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadRoundedRectShaderJson("assets/minecraft/shaders/core/rounded_rect.json")));
        }

        if (identifier.getPath().equals("shaders/core/rounded_rect.fsh")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadRoundedRectFragmentShader("assets/minecraft/shaders/core/rounded_rect.fsh")));
        }

        if (identifier.getPath().equals("shaders/core/rounded_rect_glow.json")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadRoundedRectGlowShaderJson("assets/minecraft/shaders/core/rounded_rect_glow.json")));
        }

        if (identifier.getPath().equals("shaders/core/rounded_rect_glow.fsh")) {
            callbackInfoReturnable.setReturnValue(Optional.of(HookBridge.loadRoundedRectGlowFragmentShader("assets/minecraft/shaders/core/rounded_rect_glow.fsh")));
        }
    }
}

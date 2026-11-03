package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.render.EspShaderManager;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

@CTransformer({LivingEntityRenderer.class})
public class LivingEntityRendererMixin {
    @CShadow("model")
    private EntityModel<?> model;

    @CInject(
        method = {"isVisible"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void forceVisibleForEspAtIsVisibleHead(LivingEntityRenderState state, InjectionCallback ci) {
        if (EspShaderManager.enabled && EspShaderManager.renderInvisible) {
            if (this.model != null) {
                if (HookBridge.isEspModelForVisibility(this.model)) {
                    ci.setReturnValue(true);
                }
            }
        }
    }
}

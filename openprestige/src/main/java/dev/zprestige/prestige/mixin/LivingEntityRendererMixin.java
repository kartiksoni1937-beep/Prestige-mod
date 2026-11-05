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
import net.minecraft.entity.LivingEntity;

/**
 * 1.21 signature: {@code protected boolean isVisible(T livingEntity)}.
 */
@CTransformer({LivingEntityRenderer.class})
public class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @CShadow("model")
    private M model;

    @CInject(
        method = {"isVisible"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void forceVisibleForEspAtIsVisibleHead(T entity, InjectionCallback ci) {
        if (EspShaderManager.enabled && EspShaderManager.renderInvisible) {
            if (this.model != null) {
                if (HookBridge.isEspModelForVisibility(this.model)) {
                    ci.setReturnValue(true);
                }
            }
        }
    }
}

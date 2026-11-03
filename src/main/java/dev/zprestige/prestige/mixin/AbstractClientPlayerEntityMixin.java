package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SkinTexturesEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;

@CTransformer({AbstractClientPlayerEntity.class})
public class AbstractClientPlayerEntityMixin {
    @CInject(
        method = {"getSkin"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overridePlayerSkinAtGetSkinReturn(InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasPlayerSkinListeners(Prestige.eventBus, SkinTexturesEvent.class)) {
            SkinTexturesEvent event = HookBridge.createPlayerSkinEvent((SkinTextures)ci.getReturnValue(), ((AbstractClientPlayerEntity)(Object)this).getName().getString());
            if (HookBridge.postPlayerSkinEvent(event)) {
                ci.setReturnValue(HookBridge.getPlayerSkinTextures(event));
            }
        }
    }
}

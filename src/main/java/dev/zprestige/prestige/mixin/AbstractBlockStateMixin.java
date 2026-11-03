package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.BlockVisibilityEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

@CTransformer({AbstractBlockState.class})
public class AbstractBlockStateMixin {
    @CInject(
        method = {"getAmbientOcclusionLightLevel(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overrideAmbientOcclusionLightLevelAtReturn(BlockView blockView, BlockPos blockPos, InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasAmbientOcclusionListeners(Prestige.eventBus, BlockVisibilityEvent.class)) {
            BlockVisibilityEvent event = HookBridge.createAmbientOcclusionEvent(null);
            if (HookBridge.postAmbientOcclusionEvent(event)) {
                cir.setReturnValue(1.0F);
            }
        }
    }
}

package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.BlockVisibilityEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;

@CTransformer({ChunkOcclusionDataBuilder.class})
public class ChunkOcclusionDataBuilderMixin {
    @CInject(
        method = {"markClosed"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void skipChunkOcclusionAtMarkClosedHead(BlockPos pos, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasChunkOcclusionListeners(Prestige.eventBus, BlockVisibilityEvent.class)) {
            if (HookBridge.postChunkOcclusionEvent(HookBridge.createChunkOcclusionEvent(null))) {
                ci.cancel();
            }
        }
    }
}

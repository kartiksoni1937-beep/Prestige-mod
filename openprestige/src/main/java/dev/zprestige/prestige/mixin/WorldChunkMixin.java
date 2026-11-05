package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.BlockStateUpdateEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

@CTransformer({WorldChunk.class})
public class WorldChunkMixin {
    @CShadow("world")
    public World world;

    @CInject(
        method = {"setBlockState"},
        target = {@CTarget("TAIL")}
    )
    private void postBlockUpdateEventAtSetBlockStateTail(BlockPos pos, BlockState state, int flags, InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasBlockUpdateListeners(Prestige.eventBus, BlockStateUpdateEvent.class)) {
            if (this.world.isClient()) {
                BlockStateUpdateEvent event = HookBridge.createBlockUpdateEvent(pos, state);
                HookBridge.postBlockUpdateEvent(event);
            }
        }
    }
}

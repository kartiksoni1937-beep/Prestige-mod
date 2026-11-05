package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.BlockVisibilityEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

@CTransformer({Block.class})
public class BlockMixin {
    @CInject(
        method = {"shouldDrawSide"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public static void overrideShouldDrawSideAtHead(BlockState state, BlockState otherState, Direction side, InjectionCallback cir) {
        if (Prestige.eventBus != null && HookBridge.hasDrawSideListeners(Prestige.eventBus, BlockVisibilityEvent.class)) {
            BlockVisibilityEvent event = HookBridge.createDrawSideEvent(state.getBlock());
            if (HookBridge.postDrawSideEvent(event)) {
                cir.setReturnValue(HookBridge.shouldDrawSide(event));
            }
        }
    }
}

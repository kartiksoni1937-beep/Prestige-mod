package dev.zprestige.prestige.event;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class BlockStateUpdateEvent extends Event {
    private final BlockPos pos;
    private final BlockState state;

    public BlockStateUpdateEvent(BlockPos blockPos, BlockState newState) {
        this.pos = blockPos;
        this.state = newState;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getState() {
        return this.state;
    }
}

package dev.zprestige.prestige.world;

import net.minecraft.block.BlockState;

public abstract class HitParticlePhysics {
    public void updateMotion(dev.zprestige.prestige.math.HitParticle particle, boolean physics) {
    }

    public boolean isPassable(BlockState state) {
        return state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty();
    }
}

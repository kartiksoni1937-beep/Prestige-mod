package dev.zprestige.prestige.math;

import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.world.HitParticlePhysics;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class LegacyParticlePhysics extends HitParticlePhysics {
    @Override
    public void updateMotion(HitParticle particle, boolean physicsEnabled) {
        if (!physicsEnabled || MinecraftAccess.mc.world == null) {
            this.applyLegacyMotion(particle);
        } else {
            if (!this.isPassable(MinecraftAccess.mc.world.getBlockState(BlockPos.ofFloored(particle.pos.x, particle.pos.y, particle.pos.z + particle.velocity.z)))) {
                particle.velocity = new Vec3d(particle.velocity.x, particle.velocity.y, particle.velocity.z * -0.8);
            }

            if (!this.isPassable(MinecraftAccess.mc.world.getBlockState(BlockPos.ofFloored(particle.pos.x, particle.pos.y + particle.velocity.y, particle.pos.z)))) {
                particle.velocity = new Vec3d(particle.velocity.x * 0.999F, particle.velocity.y * -0.6, particle.velocity.z * 0.999F);
            }

            if (!this.isPassable(MinecraftAccess.mc.world.getBlockState(BlockPos.ofFloored(particle.pos.x + particle.velocity.x, particle.pos.y, particle.pos.z)))) {
                particle.velocity = new Vec3d(particle.velocity.x * -0.8, particle.velocity.y, particle.velocity.z);
            }

            this.applyLegacyMotion(particle);
        }
    }

    private void applyLegacyMotion(HitParticle particle) {
        particle.pos = particle.pos.add(particle.velocity);
        particle.velocity = new Vec3d(particle.velocity.x / 0.999998F, particle.velocity.y - 1.5E-6, particle.velocity.z / 0.999998F);
    }
}

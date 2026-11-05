package dev.zprestige.prestige.data;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

public record ExplosionDamageKey(LivingEntity target, double x, double y, double z, boolean predictMovement, BlockPos obsidianPos, boolean ignoreTerrain) {
}

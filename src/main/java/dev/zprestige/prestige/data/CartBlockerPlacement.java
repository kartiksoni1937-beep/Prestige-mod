package dev.zprestige.prestige.data;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public record CartBlockerPlacement(BlockPos pos, BlockHitResult hitResult, double damage) {
}

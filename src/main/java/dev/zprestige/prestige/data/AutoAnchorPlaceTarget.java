package dev.zprestige.prestige.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record AutoAnchorPlaceTarget(BlockPos supportPos, Vec3d hitVec) {
}

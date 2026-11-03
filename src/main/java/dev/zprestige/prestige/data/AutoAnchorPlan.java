package dev.zprestige.prestige.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record AutoAnchorPlan(BlockPos anchorPos, BlockPos anchorSupportPos, Vec3d anchorHitVec, boolean anchorExists, BlockPos blockerPos, BlockPos blockerSupportPos, Vec3d blockerHitVec) {
}

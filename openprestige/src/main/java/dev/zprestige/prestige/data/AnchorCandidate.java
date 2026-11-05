package dev.zprestige.prestige.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record AnchorCandidate(BlockPos anchorPos, BlockPos supportPos, Vec3d hitVec, boolean anchorExists, int charges, double distance, boolean sameLevel, boolean adjacent, int scanIndex, double targetDamageEstimate, double predictedDamageEstimate, double score) {
}

package dev.zprestige.prestige.math;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ObbyCrystalCandidate {
    public BlockPos basePos;
    public BlockPos supportPos;
    public Direction placeSide;
    public Vec3d hitVec;
    public boolean needsObsidian;
    public boolean hasCrystal;
    public double score;

    public ObbyCrystalCandidate(BlockPos base, BlockPos support, Direction side, Vec3d aimPos, boolean placeObsidian, boolean crystalPresent, double candidateScore) {
        this.basePos = base;
        this.supportPos = support;
        this.placeSide = side;
        this.hitVec = aimPos;
        this.needsObsidian = placeObsidian;
        this.hasCrystal = crystalPresent;
        this.score = candidateScore;
    }
}

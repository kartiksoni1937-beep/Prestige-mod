package dev.zprestige.prestige.math;

import net.minecraft.util.math.Vec3d;

public final class MotionState {
    public Vec3d pos;
    public Vec3d velocity;
    public boolean onGround;

    public MotionState(Vec3d position, Vec3d motion, boolean grounded) {
        this.pos = position;
        this.velocity = motion;
        this.onGround = grounded;
    }
}

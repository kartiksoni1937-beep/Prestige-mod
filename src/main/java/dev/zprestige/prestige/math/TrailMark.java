package dev.zprestige.prestige.math;

import net.minecraft.util.math.Vec3d;

public class TrailMark {
    public Vec3d pos;
    public long spawnTime;
    public int lifetimeMs;
    public String mode;
    public float yawRadians;
    public boolean leftFoot;
    public Vec3d velocity;
    public double randomOffset;
    public float colorBlend;

    public TrailMark(Vec3d position, String trailMode, int lifetime, float yawDegrees, boolean left, Vec3d motion) {
        this.pos = position;
        this.mode = trailMode;
        this.spawnTime = System.currentTimeMillis();
        this.lifetimeMs = lifetime;
        this.yawRadians = (float)Math.toRadians(yawDegrees);
        this.leftFoot = left;
        this.velocity = motion;
        this.randomOffset = Math.random();
        this.colorBlend = (float)Math.random();
    }
}

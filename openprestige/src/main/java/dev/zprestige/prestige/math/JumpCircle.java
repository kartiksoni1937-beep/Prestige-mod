package dev.zprestige.prestige.math;

import net.minecraft.util.math.Vec3d;

public class JumpCircle {
    public Vec3d pos;
    public long spawnTime;
    public int lifetimeMs;

    public JumpCircle(Vec3d position, int lifetime) {
        this.pos = position;
        this.spawnTime = System.currentTimeMillis();
        this.lifetimeMs = lifetime;
    }
}

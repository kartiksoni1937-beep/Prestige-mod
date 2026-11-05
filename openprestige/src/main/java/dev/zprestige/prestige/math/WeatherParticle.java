package dev.zprestige.prestige.math;

import net.minecraft.util.math.Vec3d;

public class WeatherParticle {
    public Vec3d pos;
    public Vec3d velocity;
    public float age;
    public int maxAge;
    public double phase;
    public float colorBlend;

    public WeatherParticle(Vec3d position, Vec3d motion, int lifetime) {
        this.pos = position;
        this.velocity = motion;
        this.age = 0.0F;
        this.maxAge = lifetime;
        this.phase = Math.random() * Math.PI * 2.0;
        this.colorBlend = (float)Math.random();
    }

    public void advance(float delta) {
        this.pos = this.pos.add(this.velocity.multiply(delta));
        this.age += delta;
    }
}

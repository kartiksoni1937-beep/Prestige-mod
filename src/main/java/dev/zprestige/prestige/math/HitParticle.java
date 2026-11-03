package dev.zprestige.prestige.math;

import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.AnimationMode;
import dev.zprestige.prestige.world.HitParticlePhysics;
import net.minecraft.util.math.Vec3d;

public class HitParticle implements MinecraftAccess {
    private final StopWatch ageTimer;
    public Vec3d origin;
    public Vec3d pos;
    public Vec3d velocity;
    public Vec3d prevPos;
    public final HitParticlePhysics motionModel;
    public final AnimationMode mode;

    public HitParticle(Vec3d originPos, Vec3d startPos, AnimationMode animationMode) {
        super();
        this.ageTimer = new StopWatch();
        this.origin = originPos;
        this.pos = startPos;
        this.prevPos = startPos;
        this.velocity = new Vec3d((Math.random() * 0.5 - 0.25) * 0.01, Math.random() * 0.25 * 0.01, (Math.random() * 0.5 - 0.25) * 0.01);
        this.mode = animationMode;
        this.motionModel = this.createMotionModel();
        this.ageTimer.reset();
    }

    private HitParticlePhysics createMotionModel() {
        return (HitParticlePhysics)(switch (this.mode) {
            case LEGACY -> new LegacyParticlePhysics();
            case POP -> new PopParticlePhysics();
        });
    }

    public void update(boolean physicsEnabled) {
        this.prevPos = this.pos;
        this.motionModel.updateMotion(this, physicsEnabled);
    }

    public float getLifeProgress(float lifetimeMs) {
        return Math.min(1.0F, this.ageTimer.getElapsed() / lifetimeMs);
    }

    public Vec3d getPrevPos() {
        return this.prevPos;
    }

    public Vec3d getPos() {
        return this.pos;
    }

    public Vec3d getVelocity() {
        return this.velocity;
    }

    public StopWatch getAgeTimer() {
        return this.ageTimer;
    }

    public AnimationMode getMode() {
        return this.mode;
    }
}

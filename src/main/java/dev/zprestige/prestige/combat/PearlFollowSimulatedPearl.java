package dev.zprestige.prestige.combat;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class PearlFollowSimulatedPearl extends PersistentProjectileEntity {
    public boolean landed;

    public PearlFollowSimulatedPearl(World worldIn, LivingEntity shooter, ItemStack pearlStack) {
        super(EntityType.ARROW, shooter, worldIn, pearlStack, pearlStack);
    }

    @Override
    public void tick() {
        HitResult hitResult = ProjectileUtil.getCollision(this, candidate -> this.canHit(candidate));
        if (hitResult.getType() != Type.MISS) {
            this.onCollision(hitResult);
        } else {
            this.tickBlockCollision();
            if (!this.landed) {
                Vec3d motion = this.getVelocity();
                double nextX = this.getX() + motion.x;
                double nextY = this.getY() + motion.y;
                double nextZ = this.getZ() + motion.z;
                this.updateRotation();
                this.setVelocity(motion.multiply(this.isTouchingWater() ? 0.8F : 0.99F));
                if (!this.hasNoGravity()) {
                    Vec3d currentMotion = this.getVelocity();
                    this.setVelocity(currentMotion.x, currentMotion.y - 0.03, currentMotion.z);
                }

                this.setPosition(nextX, nextY, nextZ);
            }
        }
    }

    @Override
    public void onBlockHit(BlockHitResult blockHitResult) {
        this.landed = true;
        this.setPosition(blockHitResult.getPos());
    }

    @Override
    public void onEntityHit(EntityHitResult entityHitResult) {
        this.landed = true;
        this.setPosition(entityHitResult.getPos());
    }

    @Override
    public ItemStack asItemStack() {
        return null;
    }

    @Override
    public ItemStack getDefaultItemStack() {
        return null;
    }

    @Override
    public void setVelocity(double x, double y, double z, float power, float uncertainty) {
        Vec3d scaledMotion = new Vec3d(x, y, z).normalize().multiply(power);
        this.setVelocity(scaledMotion);
        this.setYaw((float)(MathHelper.atan2(scaledMotion.x, scaledMotion.z) * 180.0F / (float)Math.PI));
        this.setPitch((float)(MathHelper.atan2(scaledMotion.y, scaledMotion.horizontalLength()) * 180.0F / (float)Math.PI));
        this.lastYaw = this.getYaw();
        this.lastPitch = this.getPitch();
    }
}

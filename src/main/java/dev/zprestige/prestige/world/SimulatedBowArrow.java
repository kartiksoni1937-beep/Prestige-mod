package dev.zprestige.prestige.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class SimulatedBowArrow extends PersistentProjectileEntity {
    public boolean hitEntity = false;
    public boolean inFlight = false;

    public SimulatedBowArrow(EntityType arrowType, LivingEntity shooter, World simulationWorld, ItemStack arrowStack) {
        super(arrowType, shooter, simulationWorld, arrowStack, arrowStack);
    }

    @Override
    public void tick() {
        Vec3d motion = this.getVelocity();
        if (this.lastPitch == 0.0F && this.lastYaw == 0.0F) {
            this.setYaw((float)(MathHelper.atan2(motion.x, motion.z) * 180.0F / (float)Math.PI));
            this.setPitch((float)(MathHelper.atan2(motion.y, motion.horizontalLength()) * 180.0F / (float)Math.PI));
            this.lastYaw = this.getYaw();
            this.lastPitch = this.getPitch();
        }

        BlockPos currentBlockPos = this.getBlockPos();
        World currentWorld = this.getEntityWorld();
        BlockState currentState = currentWorld.getBlockState(currentBlockPos);
        if (!currentState.isAir()) {
            VoxelShape collisionShape = currentState.getCollisionShape(currentWorld, currentBlockPos);
            if (!collisionShape.isEmpty()) {
                for (Box shapeBox : collisionShape.getBoundingBoxes()) {
                    if (shapeBox.offset(currentBlockPos).contains(PositionUtil.getEntityPos(this))) {
                        this.setOnGround(true);
                        break;
                    }
                }
            }
        }

        if (this.shake > 0) {
            this.shake--;
        }

        if (this.isTouchingWaterOrRain() || currentState.isOf(Blocks.POWDER_SNOW)) {
            this.extinguish();
        }

        if (this.isOnGround()) {
            this.inFlight = false;
        } else {
            this.inGroundTime = 0;
            Vec3d startPos = PositionUtil.getEntityPos(this);
            Vec3d endPos = startPos.add(motion);
            BlockHitResult blockHit = currentWorld.raycast(new RaycastContext(startPos, endPos, ShapeType.COLLIDER, FluidHandling.NONE, this));
            if (blockHit.getType() != Type.MISS) {
                endPos = blockHit.getPos();
            }

            EntityHitResult entityHit = this.getEntityCollision(startPos, endPos);
            if (entityHit != null) {
                this.onCollision(entityHit);
                this.velocityDirty = true;
            }
        }

        motion = this.getVelocity();
        double motionX = motion.x;
        double motionY = motion.y;
        double motionZ = motion.z;
        double nextX = this.getX() + motionX;
        double nextY = this.getY() + motionY;
        double nextZ = this.getZ() + motionZ;
        double horizontalMotion = motion.horizontalLength();
        this.setYaw((float)(MathHelper.atan2(motionX, motionZ) * 180.0F / (float)Math.PI));
        this.setPitch((float)(MathHelper.atan2(motionY, horizontalMotion) * 180.0F / (float)Math.PI));
        this.setPitch(updateRotation(this.lastPitch, this.getPitch()));
        this.setYaw(updateRotation(this.lastYaw, this.getYaw()));
        float drag = 0.99F;
        if (this.isTouchingWater()) {
            drag = this.getDragInWater();
        }

        this.setVelocity(motion.multiply(drag));
        if (!this.hasNoGravity()) {
            Vec3d currentMotion = this.getVelocity();
            this.setVelocity(currentMotion.x, currentMotion.y - 0.05F, currentMotion.z);
        }

        this.setPosition(nextX, nextY, nextZ);
        this.tickBlockCollision();
    }

    @Override
    public void setVelocity(double x, double y, double z, float power, float uncertainty) {
        Vec3d launchVelocity = new Vec3d(x, y, z).normalize().multiply(power);
        this.setVelocity(launchVelocity);
        this.setYaw((float)(MathHelper.atan2(launchVelocity.x, launchVelocity.z) * 180.0F / (float)Math.PI));
        this.setPitch((float)(MathHelper.atan2(launchVelocity.y, launchVelocity.horizontalLength()) * 180.0F / (float)Math.PI));
        this.lastYaw = this.getYaw();
        this.lastPitch = this.getPitch();
    }

    @Override
    public void onEntityHit(EntityHitResult entityHitResult) {
        this.inFlight = false;
        this.hitEntity = true;
    }

    @Override
    public void onBlockHit(BlockHitResult blockHitResult) {
        this.inFlight = false;
    }

    @Override
    public ItemStack asItemStack() {
        return null;
    }

    @Override
    public ItemStack getDefaultItemStack() {
        return null;
    }
}

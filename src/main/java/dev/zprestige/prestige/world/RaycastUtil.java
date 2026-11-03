package dev.zprestige.prestige.world;

import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class RaycastUtil implements MinecraftAccess {
    public static Vec3d getVisiblePoint(BlockPos pos) {
        Vec3d[] offsets = new Vec3d[]{
            new Vec3d(0.5, 1.0, 0.5),
            new Vec3d(0.5, 0.5, 0.0),
            new Vec3d(0.0, 0.5, 0.5),
            new Vec3d(1.0, 0.5, 0.5),
            new Vec3d(0.5, 0.5, 1.0),
            new Vec3d(1.0, 0.0, 1.0),
            new Vec3d(0.0, 0.0, 1.0),
            new Vec3d(1.0, 0.0, 0.0),
            new Vec3d(0.0, 0.0, 0.0),
            new Vec3d(1.0, 1.0, 1.0),
            new Vec3d(0.0, 1.0, 1.0),
            new Vec3d(1.0, 1.0, 0.0),
            new Vec3d(0.0, 1.0, 0.0)
        };
        Vec3d bestPoint = null;

        for (int i = 0; i < offsets.length; i++) {
            Vec3d offset = offsets[i];
            if (i > 4) {
                break;
            }

            Vec3d point = new Vec3d(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
            if (!(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(point)) > 4.25)) {
                HitResult hitResult = raycastFromEyes(point);
                if (hitResult != null && hitResult.getPos().distanceTo(point) < 0.1F) {
                    if (ProjectileUtil.raycast(
                        mc.player,
                        mc.player.getCameraPosVec(1.0F),
                        point,
                        mc.player.getBoundingBox().stretch(point.subtract(PositionUtil.getPlayerPos())).expand(1.0, 1.0, 1.0),
                        entity -> !entity.isSpectator() && entity.canHit(),
                        point.distanceTo(mc.player.getCameraPosVec(1.0F))
                    ) == null && (bestPoint == null || mc.player.getEyePos().distanceTo(point) < mc.player.getEyePos().distanceTo(bestPoint))) {
                        if (i == 0) {
                            return point;
                        }

                        bestPoint = point;
                    }
                }
            }
        }

        if (bestPoint != null) {
            return bestPoint;
        } else {
            for (Vec3d fallbackOffset : offsets) {
                Vec3d fallbackPoint = new Vec3d(pos.getX() + fallbackOffset.x, pos.getY() + fallbackOffset.y, pos.getZ() + fallbackOffset.z);
                if (!(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(fallbackPoint)) > 4.25)) {
                    HitResult fallbackHit = raycastFromEyes(fallbackPoint);
                    if (fallbackHit != null && fallbackHit.getPos().distanceTo(fallbackPoint) < 0.1F) {
                        if (ProjectileUtil.raycast(
                            mc.player,
                            mc.player.getCameraPosVec(1.0F),
                            fallbackPoint,
                            mc.player.getBoundingBox().stretch(fallbackPoint.subtract(PositionUtil.getPlayerPos())).expand(1.0, 1.0, 1.0),
                            fallbackEntity -> !fallbackEntity.isSpectator() && fallbackEntity.canHit(),
                            fallbackPoint.distanceTo(mc.player.getCameraPosVec(1.0F))
                        ) == null && (bestPoint == null || mc.player.getEyePos().distanceTo(fallbackPoint) < mc.player.getEyePos().distanceTo(bestPoint))) {
                            bestPoint = fallbackPoint;
                        }
                    }
                }
            }

            return bestPoint;
        }
    }

    public static Vec3d getVisibleTopPoint(BlockPos pos) {
        if (mc.player.getEyePos().getY() < pos.getY() + 1.0) {
            return null;
        } else {
            Vec3d[] offsets = new Vec3d[]{
                new Vec3d(0.5, 1.0, 0.5), new Vec3d(0.1, 1.0, 0.1), new Vec3d(0.9, 1.0, 0.1), new Vec3d(0.1, 1.0, 0.9), new Vec3d(0.9, 1.0, 0.9)
            };
            Vec3d bestPoint = null;

            for (int i = 0; i < offsets.length; i++) {
                Vec3d offset = offsets[i];
                Vec3d point = new Vec3d(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
                if (!(mc.player.getEyePos().distanceTo(point) > 4.25)) {
                    HitResult hitResult = raycastFromEyes(point);
                    if (hitResult != null && hitResult.getPos().distanceTo(point) < 0.1F) {
                        if (ProjectileUtil.raycast(
                            mc.player,
                            mc.player.getCameraPosVec(1.0F),
                            point,
                            mc.player.getBoundingBox().stretch(point.subtract(PositionUtil.getPlayerPos())).expand(1.0, 1.0, 1.0),
                            entity -> !entity.isSpectator() && entity.canHit(),
                            point.distanceTo(mc.player.getCameraPosVec(1.0F))
                        ) == null) {
                            if (i == 0) {
                                return point;
                            }

                            if (bestPoint == null || mc.player.getEyePos().distanceTo(point) < mc.player.getEyePos().distanceTo(bestPoint)) {
                                bestPoint = point;
                            }
                        }
                    }
                }
            }

            return bestPoint;
        }
    }

    private static HitResult raycastFromEyes(Vec3d target) {
        return mc.world.raycast(new RaycastContext(mc.player.getEyePos(), target, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
    }
}

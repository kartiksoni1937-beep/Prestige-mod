package dev.zprestige.prestige.data;

import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.util.math.Vec3d;

public record CartThreat(TntMinecartEntity cart, Vec3d explosionPos, double horizontalSpeed, double damage) {
}

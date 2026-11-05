package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.module.combat.AutoObbyCrystal;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class WindChargeAura extends Module implements RotationProvider {
    public WindChargeAura() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting reach;
    private BoolSetting humanizedAim;
    private FloatSetting maxYaw;
    private BoolSetting inAirOnly;
    private StopWatch attackTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player.isUsingItem()) {
            return null;
        } else if (this.inAirOnly.getValue() && mc.player.isOnGround()) {
            return null;
        } else if (AutoObbyCrystal.sequenceActive) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else {
            boolean onCooldown = !this.attackTimer.hasElapsed(100.0F);
            Entity windCharge = this.findClosestWindCharge();
            if (windCharge == null) {
                return null;
            } else {
                Vec3d center = windCharge.getBoundingBox().getCenter();
                Rotation aimRotation = Prestige.silentRotationManager.getRotationTowards(center);
                if (this.humanizedAim.getValue() && !aimRotation.isClamped()) {
                    aimRotation = RotationUtil.addRandomJitter(aimRotation, center);
                }

                if (aimRotation.getYaw() - mc.player.getYaw() > this.maxYaw.getValue() * 2.0F
                    || aimRotation.getYaw() - mc.player.getYaw() < -this.maxYaw.getValue() * 2.0F) {
                    return null;
                } else if (!mc.player.canSee(windCharge)) {
                    return null;
                } else if (this.getEyeDistanceToHitbox(windCharge) > this.reach.getValue().floatValue()) {
                    return null;
                } else if (onCooldown) {
                    return null;
                } else if (aimRotation.isClamped()) {
                    return aimRotation;
                } else {
                    PlayerInteractions.attackEntity(windCharge);
                    this.attackTimer.reset();
                    return aimRotation;
                }
            }
        }
    }

    private Entity findClosestWindCharge() {
        WindChargeEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof WindChargeEntity windCharge && windCharge.isAlive() && windCharge.getOwner() != mc.player) {
                Vec3d center = windCharge.getBoundingBox().getCenter();
                double distanceSq = mc.player.squaredDistanceTo(center.x, center.y, center.z);
                if (!(Math.sqrt(distanceSq) > 5.0) && distanceSq < closestDistanceSq) {
                    closest = windCharge;
                    closestDistanceSq = distanceSq;
                }
            }
        }

        return closest;
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private double getEyeDistanceToHitbox(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double closestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        double deltaX = eyePos.x - closestX;
        double deltaY = eyePos.y - closestY;
        double deltaZ = eyePos.z - closestZ;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }
}

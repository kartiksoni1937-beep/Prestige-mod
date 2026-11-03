package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.AutoAttackEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class KillAura extends Module implements RotationProvider {
    public static KillAura instance;
    private FloatSetting reach;
    private BoolSetting stickyTargeting;
    private BoolSetting humanizedAim;
    private BoolSetting playerOnly;
    private BoolSetting weaponOnly;
    private EnumSetting cooldownMode;
    private RangeSetting cooldownRange;
    private FloatSetting missChance;
    private FloatSetting maxYaw;
    private FloatSetting targetFov;
    private EnumSetting onShielding;
    private MultiSetting aimPoints;
    private BoolSetting renderDot;
    private FloatSetting dotSize;
    private ColorSetting dotColor;
    private StopWatch attackTimer = new StopWatch();
    private StopWatch releaseUseTimer = new StopWatch();
    private Entity currentTarget;
    private static final String[] AIM_POINT_NAMES = new String[]{"Head", "Neck", "Chest", "Waist", "Thighs", "Legs"};
    private static final double[] AIM_POINT_HEIGHTS = new double[]{0.95, 0.85, 0.7, 0.55, 0.35, 0.1};

    public KillAura() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        instance = this;
        this.cooldownRange.visibleWhen((Predicate<Float>)ignored -> this.cooldownMode.getValue().equals("Range"));
        this.dotColor.visibleWhen((Predicate<Color>)ignored -> this.renderDot.getValue());
        this.dotSize.visibleWhen((Predicate<Float>)ignored -> this.renderDot.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player.isUsingItem()) {
            return null;
        } else if (AutoObbyCrystal.sequenceActive) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (!InventoryUtil.isSword(mc.player.getMainHandStack())
            && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)
            && this.weaponOnly.getValue()) {
            return null;
        } else {
            float cooldownThreshold = switch (this.cooldownMode.getValue()) {
                case "Vanilla" -> 1.0F;
                case "Range" -> this.cooldownRange.randomInRange() / 100.0F;
                default -> this.cooldownRange.getLow() / 100.0F;
            };
            boolean attackNotReady = !this.attackTimer.hasElapsed(100.0F) || mc.player.getAttackCooldownProgress(0.0F) < cooldownThreshold || !mc.player.isOnGround() && mc.player.fallDistance <= 0.0;
            if (this.currentTarget != null) {
                if (!this.currentTarget.isAlive()) {
                    this.currentTarget = null;
                    return null;
                }

                Vec3d targetEyePos = this.currentTarget.getEyePos();
                if (Math.sqrt(mc.player.squaredDistanceTo(targetEyePos.x, targetEyePos.y, targetEyePos.z)) > 5.0) {
                    this.currentTarget = null;
                }

                Rotation rotationToTarget = RotationUtil.getRotationTo(targetEyePos);
                if (!RotationUtil.isWithinAngle(new Rotation(mc.player.getYaw(), mc.player.getPitch()), rotationToTarget, this.maxYaw.getValue() * 2.0F)) {
                    this.currentTarget = null;
                }
            }

            if (!this.stickyTargeting.getValue() || this.currentTarget == null) {
                this.currentTarget = this.findTarget(this.playerOnly.getValue(), this.targetFov.getValue());
            }

            if (this.currentTarget == null) {
                return null;
            } else {
                Vec3d aimPoint = this.getAimPoint(this.currentTarget);
                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                if (this.humanizedAim.getValue() && !rotation.isClamped()) {
                    rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                }

                if (rotation.getYaw() - mc.player.getYaw() > this.maxYaw.getValue() * 2.0F
                    || rotation.getYaw() - mc.player.getYaw() < -this.maxYaw.getValue() * 2.0F) {
                    return null;
                } else if (!mc.player.canSee(this.currentTarget)) {
                    return null;
                } else if (BlockUtil.isCobwebInLineOfSight(this.currentTarget)) {
                    return null;
                } else if (this.getHitboxDistance(this.currentTarget) > this.reach.getValue().floatValue()) {
                    return null;
                } else if (attackNotReady) {
                    return null;
                } else if (rotation.isClamped()) {
                    return rotation;
                } else {
                    if (this.missChance.getValue() > 0.0F) {
                        if (RandomUtil.randomFloat(0.0F, 100.0F) < this.missChance.getValue()) {
                            this.attackTimer.reset();
                            return null;
                        }
                    }

                    new AutoAttackEvent().post();
                    if (this.currentTarget instanceof PlayerEntity shieldingPlayer && TargetSelector.isBlockingWithShield(shieldingPlayer)) {
                        String shieldMode = this.onShielding.getValue();
                        if (shieldMode.equals("Skip")) {
                            return null;
                        }

                        if (shieldMode.equals("Prefer Axe") && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
                            int axeSlot = this.findAxeSlot();
                            if (axeSlot != -1) {
                                Prestige.silentSlotManager.spoofSlot(axeSlot);
                            }
                        }
                    }

                    PlayerInteractions.attackEntity(this.currentTarget);
                    this.attackTimer.reset();
                    return rotation;
                }
            }
        }
    }

    private int findAxeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }

        return -1;
    }

    private Entity findTarget(boolean onlyPlayers, float maxFov) {
        Entity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Rotation playerRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        for (Entity entity : mc.world.getEntities()) {
            if (entity != mc.player
                && entity.isAlive()
                && (!onlyPlayers || TargetSelector.isPlayer(entity))
                && Prestige.botFilter.isValidTarget(entity)
                && (!(entity instanceof PlayerEntity) || !Prestige.friendManager.isFriend(entity.getName().getString()))) {
                Vec3d eyePos = entity.getEyePos();
                double distanceSq = mc.player.squaredDistanceTo(eyePos.x, eyePos.y, eyePos.z);
                if (!(Math.sqrt(distanceSq) > 5.0)) {
                    if (maxFov < 360.0F) {
                        if (!RotationUtil.isWithinAngle(playerRotation, RotationUtil.getRotationToPoint((float)eyePos.x, (float)eyePos.y, (float)eyePos.z), maxFov)) {
                            continue;
                        }
                    }

                    if (distanceSq < closestDistanceSq) {
                        closest = entity;
                        closestDistanceSq = distanceSq;
                    }
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

    private double getHitboxDistance(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double nearestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double nearestY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double nearestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        double dx = eyePos.x - nearestX;
        double dy = eyePos.y - nearestY;
        double dz = eyePos.z - nearestZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vec3d getAimPoint(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double boxHeight = box.maxY - box.minY;
        if (boxHeight <= 0.0) {
            return entity.getEyePos();
        } else {
            double nearestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
            double nearestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
            double horizontalDistSq = (eyePos.x - nearestX) * (eyePos.x - nearestX) + (eyePos.z - nearestZ) * (eyePos.z - nearestZ);
            double bestDistSq = Double.POSITIVE_INFINITY;
            Vec3d bestPoint = null;

            for (int index = 0; index < AIM_POINT_NAMES.length; index++) {
                if (this.aimPoints.isSelected(AIM_POINT_NAMES[index])) {
                    double pointY = box.minY + AIM_POINT_HEIGHTS[index] * boxHeight;
                    double dy = eyePos.y - pointY;
                    double distSq = horizontalDistSq + dy * dy;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestPoint = new Vec3d(nearestX, pointY, nearestZ);
                    }
                }
            }

            return bestPoint != null ? bestPoint : entity.getEyePos();
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerActionC2SPacket actionPacket && actionPacket.getAction() == Action.RELEASE_USE_ITEM) {
            this.releaseUseTimer.reset();
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.renderDot.getValue()) {
            Entity target = this.currentTarget;
            if (target != null && target.isAlive()) {
                Vec3d lastPos = PositionUtil.getLastPos(target);
                float tickDelta = Render2D.getTickDelta();
                double eyeHeight = target.getEyeY() - target.getY();
                double renderX = lastPos.x + (target.getX() - lastPos.x) * tickDelta;
                double renderY = lastPos.y + (target.getY() - lastPos.y) * tickDelta + eyeHeight;
                double renderZ = lastPos.z + (target.getZ() - lastPos.z) * tickDelta;
                float radius = this.dotSize.getValue();
                float minX = (float)(renderX - radius);
                float minY = (float)(renderY - radius);
                float minZ = (float)(renderZ - radius);
                float maxX = (float)(renderX + radius);
                float maxY = (float)(renderY + radius);
                float maxZ = (float)(renderZ + radius);
                Color fillColor = this.dotColor.getValue();
                Color outlineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), Math.min(255, fillColor.getAlpha() + 55));
                ShapeRenderer.drawFilledBox(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, fillColor);
                ShapeRenderer.drawBoxOutline(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, outlineColor);
            }
        }
    }
}

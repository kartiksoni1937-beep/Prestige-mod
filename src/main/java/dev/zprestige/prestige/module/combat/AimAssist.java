package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    private EnumSetting mode;
    private EnumSetting target;
    private FloatSetting fov;
    private FloatSetting range;
    private BoolSetting stickyTargeting;
    private BoolSetting throughWalls;
    private BoolSetting onlyOnRotate;
    private BoolSetting stopOnBlockBreak;
    private BoolSetting weaponOnly;
    private BoolSetting crystalOnly;
    private BoolSetting requireLeftClick;
    private BoolSetting requireRightClick;
    private FloatSetting reactionDelay;
    private FloatSetting speed;
    private FloatSetting randomness;
    private BoolSetting smoothen;
    private FloatSetting smoothness;
    private BoolSetting predictMovement;
    private FloatSetting predictMultiply;
    private BoolSetting verticalOnly;
    private BoolSetting stopOnHitbox;
    private Entity currentTarget;
    private StopWatch reactionTimer = new StopWatch();
    private float rolledReactionDelay = 0.0F;
    private double[] lastMousePos = new double[]{0.0, 0.0};
    private float smoothedYawDelta = 0.0F;
    private float smoothedPitchDelta = 0.0F;

    public AimAssist() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.weaponOnly.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Players"));
        this.requireLeftClick.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Players"));
        this.predictMultiply
            .visibleWhen(
                (Predicate<Float>)ignored -> this.predictMovement.getValue() && this.mode.getValue().equals("Players")
            );
        this.crystalOnly.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystals"));
        this.verticalOnly.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystals"));
        this.predictMovement.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Players"));
        this.requireRightClick.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystals"));
        this.smoothness.visibleWhen((Predicate<Float>)ignored -> this.smoothen.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!AutoObbyCrystal.sequenceActive) {
                if (!this.stopOnBlockBreak.getValue() || mc.crosshairTarget == null || mc.crosshairTarget.getType() != Type.BLOCK || !mc.options.attackKey.isPressed()) {
                    if (this.mode.getValue().equals("Players")) {
                        if (this.weaponOnly.getValue()
                            && !InventoryUtil.isSword(mc.player.getMainHandStack())
                            && !(InventoryUtil.getHeldItem() instanceof AxeItem)) {
                            return;
                        }

                        if (this.requireLeftClick.getValue() && !mc.options.attackKey.isPressed()) {
                            return;
                        }
                    }

                    if (this.mode.getValue().equals("Crystals")) {
                        if (this.crystalOnly.getValue() && !InventoryUtil.isHolding(Items.END_CRYSTAL)) {
                            return;
                        }

                        if (this.requireRightClick.getValue() && !mc.options.useKey.isPressed()) {
                            return;
                        }
                    }

                    Entity previousTarget = this.currentTarget;
                    if (this.currentTarget != null) {
                        if (!this.currentTarget.isAlive()) {
                            this.currentTarget = null;
                        } else {
                            Vec3d targetEyePos = this.currentTarget.getEyePos();
                            if (Math.sqrt(mc.player.squaredDistanceTo(targetEyePos.x, targetEyePos.y, targetEyePos.z)) > this.range.getValue().floatValue()) {
                                this.currentTarget = null;
                            } else {
                                if (this.fov.getValue() < 360.0F) {
                                    Rotation rotationToTarget = RotationUtil.getRotationTo(targetEyePos);
                                    if (!RotationUtil.isWithinAngle(new Rotation(mc.player.getYaw(), mc.player.getPitch()), rotationToTarget, this.fov.getValue())) {
                                        this.currentTarget = null;
                                    }
                                }

                                if (this.currentTarget != null && !this.throughWalls.getValue() && !mc.player.canSee(this.currentTarget)) {
                                    this.currentTarget = null;
                                }
                            }
                        }
                    }

                    if (!this.stickyTargeting.getValue() || this.currentTarget == null) {
                        this.currentTarget = this.findTarget();
                    }

                    if (this.currentTarget == null || !this.currentTarget.equals(previousTarget)) {
                        this.reactionTimer.reset();
                        float baseDelay = this.reactionDelay.getValue();
                        this.rolledReactionDelay = baseDelay * RandomUtil.randomFloat(0.75F, 1.25F);
                        this.smoothedYawDelta = 0.0F;
                        this.smoothedPitchDelta = 0.0F;
                    }

                    if (this.onlyOnRotate.getValue()) {
                        double lastMouseX = this.lastMousePos[0];
                        double lastMouseY = this.lastMousePos[1];
                        this.lastMousePos[0] = mc.mouse.getX();
                        this.lastMousePos[1] = mc.mouse.getY();
                        if (lastMouseX == this.lastMousePos[0] && lastMouseY == this.lastMousePos[1]) {
                            return;
                        }
                    }

                    if (this.reactionTimer.hasElapsed(this.rolledReactionDelay)) {
                        if (!this.stopOnHitbox.getValue()
                            || this.currentTarget == null
                            || mc.crosshairTarget == null
                            || mc.crosshairTarget.getType() != Type.ENTITY
                            || !((EntityHitResult)mc.crosshairTarget).getEntity().equals(this.currentTarget)) {
                            if (this.currentTarget != null) {
                                Vec3d aimPos = this.currentTarget.getEyePos();
                                if (this.currentTarget instanceof EndCrystalEntity) {
                                    aimPos = PositionUtil.getEntityPos(this.currentTarget);
                                }

                                Vec3d predictionOffset = new Vec3d(this.currentTarget.getX() - this.currentTarget.lastX, this.currentTarget.getY() - this.currentTarget.lastY, this.currentTarget.getZ() - this.currentTarget.lastZ)
                                    .multiply(this.predictMultiply.getValue().floatValue());
                                if (this.predictMovement.getValue() && this.mode.getValue().equals("Players")) {
                                    aimPos = aimPos.add(predictionOffset);
                                }

                                Rotation targetRotation = RotationUtil.getRotationTo(aimPos);
                                float yawDelta = targetRotation.getYaw() - mc.player.getYaw();
                                float pitchDelta = targetRotation.getPitch() - mc.player.getPitch();

                                yawDelta = MathHelper.wrapDegrees(yawDelta);

                                if (!(this.fov.getValue() < 360.0F)
                                    || !(Math.abs(yawDelta) > this.fov.getValue() / 2.0F) && !(Math.abs(pitchDelta) > this.fov.getValue() / 2.0F)) {
                                    float fps = Math.max(mc.getCurrentFps(), 1);
                                    float maxStep = this.speed.getValue() * 2.0F / fps;
                                    float jitterFactor = 1.0F - RandomUtil.randomFloat(0.0F, this.randomness.getValue() / 100.0F) * 0.3F;
                                    maxStep *= jitterFactor;
                                    float smoothingFactor = Math.min(this.speed.getValue() / 100.0F * 0.15F, 1.0F);
                                    if (this.smoothen.getValue()) {
                                        float glideFactor = Math.max(0.02F, 1.0F - (this.smoothness.getValue() / 100.0F));
                                        smoothingFactor = Math.max(0.005F, glideFactor * 0.18F);
                                        maxStep *= glideFactor;
                                    }

                                    this.smoothedYawDelta = this.smoothedYawDelta + (yawDelta - this.smoothedYawDelta) * smoothingFactor;
                                    this.smoothedPitchDelta = this.smoothedPitchDelta + (pitchDelta - this.smoothedPitchDelta) * smoothingFactor;
                                    if (!(this.mode.getValue().equals("Crystals") && this.verticalOnly.getValue())) {
                                        float yawStep = Math.min(Math.abs(this.smoothedYawDelta), maxStep) * Math.signum(this.smoothedYawDelta);
                                        if (Math.abs(yawDelta) <= maxStep) {
                                            mc.player.setYaw(targetRotation.getYaw());
                                        } else {
                                            mc.player.setYaw(mc.player.getYaw() + yawStep);
                                        }
                                    }

                                    Box targetBox = this.currentTarget.getBoundingBox();
                                    Rotation bottomRotation = RotationUtil.getRotationToPoint((float)(targetBox.minX + (targetBox.maxX - targetBox.minX) / 2.0), (float)(targetBox.minY + predictionOffset.getY()), (float)(targetBox.minZ + (targetBox.maxZ - targetBox.minZ) / 2.0));
                                    Rotation topRotation = RotationUtil.getRotationToPoint((float)(targetBox.minX + (targetBox.maxX - targetBox.minX) / 2.0), (float)(targetBox.maxY + predictionOffset.getY()), (float)(targetBox.minZ + (targetBox.maxZ - targetBox.minZ) / 2.0));
                                    float bottomPitch = bottomRotation.getPitch();
                                    float topPitch = topRotation.getPitch();
                                    if (mc.player.getPitch() > bottomPitch || mc.player.getPitch() < topPitch) {
                                        float pitchStep = Math.min(Math.abs(this.smoothedPitchDelta), maxStep) * Math.signum(this.smoothedPitchDelta);
                                        if (Math.abs(pitchDelta) <= maxStep) {
                                            mc.player.setPitch(targetRotation.getPitch());
                                        } else {
                                            mc.player.setPitch(mc.player.getPitch() + pitchStep);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Entity findTarget() {
        String priority = this.target.getValue();
        Entity bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        Rotation playerRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive()) {
                boolean isPlayer = TargetSelector.isPlayer(entity);
                if ((isPlayer || entity instanceof EndCrystalEntity)
                    && (!isPlayer || this.mode.getValue().equals("Players"))
                    && (!(entity instanceof EndCrystalEntity) || this.mode.getValue().equals("Crystals"))
                    && Prestige.botFilter.isValidTarget(entity)
                    && (!(entity instanceof PlayerEntity) || !Prestige.friendManager.isFriend(entity.getName().getString()))) {
                    Vec3d eyePos = entity.getEyePos();
                    double distanceSq = mc.player.squaredDistanceTo(eyePos.x, eyePos.y, eyePos.z);
                    if (!(Math.sqrt(distanceSq) > this.range.getValue().floatValue())) {
                        Rotation rotation = RotationUtil.getRotationToPoint((float)eyePos.x, (float)eyePos.y, (float)eyePos.z);
                        if (this.fov.getValue() < 360.0F) {
                            if (!RotationUtil.isWithinAngle(playerRotation, rotation, this.fov.getValue())) {
                                continue;
                            }
                        }

                        if (this.throughWalls.getValue() || mc.player.canSee(entity)) {
                            double score = this.scoreTarget(entity, distanceSq, rotation, priority);
                            if (score < bestScore) {
                                bestTarget = entity;
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }

        return bestTarget;
    }

    private double scoreTarget(Entity entity, double distanceSq, Rotation rotation, String priority) {
        switch (priority) {
            case "Crosshair":
                return RotationUtil.getAngleFromPlayerRotation(rotation);
            case "Lowest HP":
                if (entity instanceof PlayerEntity hpTarget) {
                    return (hpTarget.getHealth() + hpTarget.getAbsorptionAmount()) * 1000.0 + distanceSq;
                }

                return distanceSq;
            case "Lowest Armor":
                if (entity instanceof PlayerEntity armorTarget) {
                    return armorTarget.getArmor() * 1000.0 + distanceSq;
                }

                return distanceSq;
            case "Closest":
            default:
                return distanceSq;
        }
    }
}

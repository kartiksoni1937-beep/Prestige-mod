package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.BoundingBoxEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.RotationUtil;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Hitboxes extends Module implements RotationProvider {
    private BoolSetting playerOnly;
    private FloatSetting expand;
    private BoolSetting renderHitboxes;
    private FloatSetting distance;
    private EnumSetting antiCheat;
    private BoolSetting ignoreWalls;
    private BoolSetting skipShielding;
    private boolean hitPending = false;
    private boolean sendingAttack = false;

    public Hitboxes() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.ignoreWalls.visibleWhen((Predicate<Boolean>)ignored -> this.antiCheat.getValue().equals("Grim/Vulcan"));
        this.skipShielding.visibleWhen((Predicate<Boolean>)ignored -> this.antiCheat.getValue().equals("Grim/Vulcan"));
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.renderHitboxes.getValue()) {
            PlayerEntity closestEnemy = TargetSelector.getClosestEnemy();
            float maxDistance = this.distance.getValue();
            boolean grimVulcanMode = this.antiCheat.getValue().equals("Grim/Vulcan");
            double expansion = grimVulcanMode ? this.expand.getValue().floatValue() / 5.0 : 0.0;

            for (PlayerEntity player : mc.world.getPlayers()) {
                float playerDistance = mc.player.distanceTo(player);
                if (!(playerDistance > maxDistance) && Prestige.botFilter.isValidTarget(player)) {
                    Box box = grimVulcanMode ? player.getBoundingBox().expand(expansion) : player.getBoundingBox();
                    ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)box.minX, (float)box.minY, (float)box.minZ, (float)box.maxX, (float)box.maxY, (float)box.maxZ, playerDistance <= 3.0F && grimVulcanMode && closestEnemy == player ? Color.GREEN : Color.RED);
                }
            }
        }
    }

    @Subscribe
    public void onBoundingBox(BoundingBoxEvent event) {
        if (this.antiCheat.getValue().equals("None")) {
            if (!event.getEntity().equals(mc.player)) {
                if (!this.playerOnly.getValue() || event.getEntity() instanceof PlayerEntity) {
                    if (!(event.getEntity() instanceof PlayerEntity)
                        || Prestige.botFilter.isValidTarget((PlayerEntity)event.getEntity())) {
                        if (!(mc.player.distanceTo(event.getEntity()) > this.distance.getValue())) {
                            event.setBox(event.getBox().expand(this.expand.getValue().floatValue() / 5.0));
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (event.getPhase() == EventPhase.PRE) {
            if (this.antiCheat.getValue().equals("Grim/Vulcan")) {
                if (this.sendingAttack) {
                    event.setSkipPost(true);
                } else {
                    HitResult crosshairHit = mc.crosshairTarget;
                    if (crosshairHit != null && crosshairHit.getType() == Type.MISS) {
                        PlayerEntity enemy = TargetSelector.getClosestEnemy();
                        if (enemy != null && !(mc.player.distanceTo(enemy) > 3.0F) && (this.ignoreWalls.getValue() || mc.player.canSee(enemy))) {
                            if (!this.skipShielding.getValue() || !this.isRaisingShield(enemy)) {
                                if (!enemy.getBoundingBox().expand(this.expand.getValue().floatValue() / 5.0).raycast(mc.player.getEyePos(), crosshairHit.getPos()).isEmpty()) {
                                    this.hitPending = true;
                                    event.cancel();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (!this.antiCheat.getValue().equals("Grim/Vulcan")) {
            return null;
        } else if (!this.hitPending) {
            return null;
        } else {
            HitResult crosshairHit = mc.crosshairTarget;
            if (crosshairHit != null && crosshairHit.getType() == Type.MISS) {
                PlayerEntity enemy = TargetSelector.getClosestEnemy();
                if (enemy != null && !(mc.player.distanceTo(enemy) > 3.0F) && (this.ignoreWalls.getValue() || mc.player.canSee(enemy))) {
                    if (this.skipShielding.getValue() && this.isRaisingShield(enemy)) {
                        this.hitPending = false;
                        return null;
                    } else {
                        Box expandedBox = enemy.getBoundingBox().expand(this.expand.getValue().floatValue() / 5.0);
                        Vec3d missPos = crosshairHit.getPos();
                        Vec3d aimPoint = new Vec3d(
                            MathHelper.clamp(missPos.x, expandedBox.minX, expandedBox.maxX),
                            MathHelper.clamp(missPos.y, expandedBox.minY, expandedBox.maxY),
                            MathHelper.clamp(missPos.z, expandedBox.minZ, expandedBox.maxZ)
                        );
                        Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                        rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                        if (rotation.isClamped()) {
                            return rotation;
                        } else {
                            this.hitPending = false;
                            this.sendingAttack = true;
                            PlayerInteractions.attackEntity(enemy);
                            this.sendingAttack = false;
                            return rotation;
                        }
                    }
                } else {
                    this.hitPending = false;
                    return null;
                }
            } else {
                this.hitPending = false;
                return null;
            }
        }
    }

    private boolean isRaisingShield(PlayerEntity player) {
        return player != null && player.isUsingItem() && player.getActiveItem().isOf(Items.SHIELD);
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
    }
}

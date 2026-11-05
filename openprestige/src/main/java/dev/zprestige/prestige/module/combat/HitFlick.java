package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.type.HitFlickState;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public class HitFlick extends Module implements RotationProvider {
    public HitFlick() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting playersOnly;
    private BoolSetting requireSprint;
    private FloatSetting minAngle;
    private FloatSetting maxAngle;
    private EnumSetting direction;
    private HitFlickState state = HitFlickState.IDLE;
    private Entity pendingTarget;
    private float flickYaw;
    private boolean sendingAttack;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.state = HitFlickState.IDLE;
        this.pendingTarget = null;
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.state = HitFlickState.IDLE;
        this.pendingTarget = null;
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (!this.sendingAttack) {
            if (mc.player != null && mc.world != null) {
                if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
                    if (this.state == HitFlickState.IDLE) {
                        if (!this.requireSprint.getValue() || mc.player.isSprinting()) {
                            Entity target = event.getTarget();
                            if (target != null && target != mc.player) {
                                if (!this.playersOnly.getValue() || target instanceof PlayerEntity) {
                                    if (!(target instanceof PlayerEntity player && Prestige.friendManager.isFriend(player.getName().getString()))) {
                                        if (Prestige.botFilter.isValidTarget(target)) {
                                            String directionMode = this.direction.getValue();
                                            float flickSign;
                                            if (directionMode.equals("Left")) {
                                                flickSign = -1.0F;
                                            } else if (directionMode.equals("Right")) {
                                                flickSign = 1.0F;
                                            } else {
                                                flickSign = Math.random() < 0.5 ? -1.0F : 1.0F;
                                            }

                                            float lowAngle = Math.min(this.minAngle.getValue(), this.maxAngle.getValue());
                                            float highAngle = Math.max(this.minAngle.getValue(), this.maxAngle.getValue());
                                            float flickAngle = lowAngle + (float)(Math.random() * (highAngle - lowAngle));
                                            this.flickYaw = mc.player.getYaw() + flickSign * flickAngle;
                                            this.pendingTarget = target;
                                            this.state = HitFlickState.FLICK;
                                            event.cancel();
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

    @Override
    public Rotation provideRotation() {
        if (this.state == HitFlickState.IDLE) {
            return null;
        } else if (this.state == HitFlickState.FLICK) {
            float yaw = this.flickYaw + (float)(Math.random() * 3.0 - 1.5);
            float pitch = MathHelper.clamp(mc.player.getPitch() + (float)(Math.random() * 2.0 - 1.0), -89.9F, 89.9F);
            this.state = HitFlickState.ATTACK;
            return new Rotation(yaw, pitch);
        } else {
            this.state = HitFlickState.IDLE;
            Entity target = this.pendingTarget;
            this.pendingTarget = null;
            if (target != null && target.isAlive()) {
                Rotation rotationToTarget = RotationUtil.getRotationTo(target.getEyePos());
                float attackYaw = rotationToTarget.getYaw() + (float)(Math.random() * 3.0 - 1.5);
                float attackPitch = MathHelper.clamp(rotationToTarget.getPitch() + (float)(Math.random() * 2.0 - 1.0), -89.9F, 89.9F);
                this.sendingAttack = true;
                PlayerInteractions.attackEntity(target);
                this.sendingAttack = false;
                return new Rotation(attackYaw, attackPitch);
            } else {
                return null;
            }
        }
    }
}

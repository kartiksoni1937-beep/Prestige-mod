package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.ShieldBreakEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class AutoShield extends Module {
    private FloatSetting targetDistance;
    private FloatSetting fov;
    private FloatSetting movementThreshold;
    private FloatSetting swordCooldownCorrection;
    private BoolSetting disableTracker;
    private BoolSetting swapToSword;
    private StopWatch targetSwingTimer = new StopWatch();
    private StopWatch attackTimer = new StopWatch();
    private StopWatch shieldDisableTimer = new StopWatch();
    private boolean shieldDisabled = false;
    public static boolean shieldOnCooldown = false;

    public AutoShield() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.swapToSword.visibleWhen((Predicate<Boolean>)ignored -> this.disableTracker.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (this.shieldDisabled) {
            if (!this.shieldDisableTimer.hasElapsed(5000.0F)) {
                this.restoreUseKeyState();
                return;
            }

            this.shieldDisabled = false;
            shieldOnCooldown = false;
        }

        PlayerEntity target = TargetSelector.getClosestEnemyInFov(this.fov.getValue());
        if (this.attackTimer.hasElapsed(100.0F)) {
            if (target == null) {
                this.restoreUseKeyState();
            } else if (mc.player.distanceTo(target) > this.targetDistance.getValue()) {
                this.restoreUseKeyState();
            } else if (mc.player.getMainHandStack().getItem() == Items.SHIELD
                || mc.player.getOffHandStack().getItem() == Items.SHIELD && !PlayerInteractions.isHoldingUsableItem()) {
                if (PositionUtil.isPlayerGliding()) {
                    this.restoreUseKeyState();
                } else if (target.getMainHandStack().getItem() instanceof AxeItem) {
                    this.releaseUseKey();
                } else if (!InventoryUtil.isSword(target.getMainHandStack())
                    && !InventoryUtil.isMace(target.getMainHandStack())) {
                    this.releaseUseKey();
                } else {
                    boolean targetCooldownReady = this.targetSwingTimer.hasElapsed(625.0F * (0.94F - this.swordCooldownCorrection.getValue() / 100.0F));
                    if (!mc.player.isOnGround() && mc.player.fallDistance > 0.0 && mc.player.getAttackCooldownProgress(0.0F) > 0.94F) {
                        this.releaseUseKey();
                    } else if (!targetCooldownReady || !this.isApproaching(target) && !(mc.player.distanceTo(target) <= 3.0F)) {
                        if (!mc.player.isOnGround() && mc.player.fallDistance <= 0.0) {
                            this.pressUseKey();
                        } else {
                            this.releaseUseKey();
                        }
                    } else {
                        this.pressUseKey();
                    }
                }
            } else {
                this.restoreUseKeyState();
            }
        }
    }

    @Subscribe
    public void onShieldBreak(ShieldBreakEvent event) {
        if (this.disableTracker.getValue()) {
            if (event.entity == mc.player) {
                this.shieldDisableTimer.reset();
                this.shieldDisabled = true;
                shieldOnCooldown = true;
                this.restoreUseKeyState();
                if (this.swapToSword.getValue()) {
                    for (int slot = 0; slot < 9; slot++) {
                        if (InventoryUtil.isSword(mc.player.getInventory().getStack(slot))) {
                            InventoryUtil.selectSlot(slot);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.world != null) {
            if (event.getPacket() instanceof PlayerRespawnS2CPacket) {
                this.shieldDisabled = false;
                shieldOnCooldown = false;
            } else {
                if (event.getPacket() instanceof EntityAnimationS2CPacket) {
                    int entityId = ((EntityAnimationS2CPacket)event.getPacket()).getEntityId();
                    Entity swingingEntity = mc.world.getEntityById(entityId);
                    if (swingingEntity instanceof PlayerEntity) {
                        PlayerEntity closestEnemy = TargetSelector.getClosestEnemyInFov(this.fov.getValue());
                        if (closestEnemy == null) {
                            return;
                        }

                        if (closestEnemy.equals(swingingEntity)) {
                            this.targetSwingTimer.reset();
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (mc.player.isUsingItem()) {
            event.cancel();
        }

        this.attackTimer.reset();
    }

    private boolean isApproaching(PlayerEntity enemy) {
        Vec3d velocity = new Vec3d(enemy.getX() - enemy.lastX, enemy.getY() - enemy.lastY, enemy.getZ() - enemy.lastZ);
        if (velocity.length() < this.movementThreshold.getValue() / 10.0F) {
            return false;
        } else {
            Vec3d toSelf = PositionUtil.getPlayerPos().subtract(PositionUtil.getEntityPos(enemy)).normalize();
            return velocity.normalize().dotProduct(toSelf) > 0.0;
        }
    }

    @Override
    public void onDisable() {
        this.shieldDisabled = false;
        shieldOnCooldown = false;
        this.restoreUseKeyState();
    }

    private void pressUseKey() {
        mc.options.useKey.setPressed(true);
    }

    private void releaseUseKey() {
        mc.options.useKey.setPressed(false);
    }

    private void restoreUseKeyState() {
        int keyCode = new KeyBindingAccessor(mc.options.useKey).getBoundKey().getCode();
        boolean physicallyPressed;
        if (keyCode < 8) {
            physicallyPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), keyCode) == 1;
        } else {
            physicallyPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
        }

        mc.options.useKey.setPressed(physicallyPressed);
    }
}

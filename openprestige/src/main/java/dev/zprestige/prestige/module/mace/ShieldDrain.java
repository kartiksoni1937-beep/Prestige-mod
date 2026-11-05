package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public class ShieldDrain extends Module {
    private FloatSetting horizontalTargetDist;
    private FloatSetting maxRange;
    private FloatSetting minFallDistance;
    private BoolSetting onlyOnCrosshair;
    private BoolSetting requireMace;
    private BoolSetting pauseOnGround;
    private IntSetting clicksPerTick;
    private BoolSetting autoSwap;
    private BoolSetting silent;
    private int previousSlot = -1;
    private static boolean drainingThisTick;

    public ShieldDrain() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.autoSwap.visibleWhen((Predicate<Boolean>)ignoredAutoSwap -> !this.requireMace.getValue());
        this.silent
            .visibleWhen((Predicate<Boolean>)ignoredSilent -> !this.requireMace.getValue() && this.autoSwap.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    public static boolean isDraining() {
        return drainingThisTick;
    }

    @Override
    public void onEnable() {
        this.previousSlot = -1;
    }

    @Override
    public void onDisable() {
        drainingThisTick = false;
        this.restorePreviousSlot();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        drainingThisTick = false;
        if (mc.player != null && mc.world != null && mc.currentScreen == null && mc.isWindowFocused()) {
            if (!mc.player.isUsingItem()) {
                if (this.pauseOnGround.getValue() && mc.player.isOnGround()) {
                    this.restorePreviousSlot();
                } else if (mc.player.fallDistance < this.minFallDistance.getValue().floatValue()) {
                    this.restorePreviousSlot();
                } else {
                    PlayerEntity target = this.findShieldTarget();
                    if (target == null) {
                        this.restorePreviousSlot();
                    } else if (mc.player.distanceTo(target) > this.maxRange.getValue()) {
                        this.restorePreviousSlot();
                    } else {
                        if (!InventoryUtil.isMace(mc.player.getMainHandStack())) {
                            if (this.requireMace.getValue()) {
                                return;
                            }

                            if (!this.autoSwap.getValue()) {
                                return;
                            }

                            Integer maceSlot = InventoryUtil.findMaceSlot();
                            if (maceSlot == null) {
                                return;
                            }

                            if (this.silent.getValue() && this.previousSlot == -1) {
                                this.previousSlot = InventoryUtil.getSelectedSlot();
                            }

                            InventoryUtil.selectSlot(maceSlot);
                        }

                        drainingThisTick = true;
                        int clicks = this.clicksPerTick.getValue();

                        for (int clickIndex = 0; clickIndex < clicks; clickIndex++) {
                            PlayerInteractions.attackEntity(target);
                        }
                    }
                }
            }
        }
    }

    private PlayerEntity findShieldTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult crosshairHit
            && crosshairHit.getEntity() instanceof PlayerEntity crosshairPlayer
            && TargetSelector.isBlockingWithShield(crosshairPlayer)
            && !Prestige.friendManager.isFriend(crosshairPlayer.getName().getString())) {
            return crosshairPlayer;
        } else if (this.onlyOnCrosshair.getValue()) {
            return null;
        } else {
            return this.findClosestShielder(this.horizontalTargetDist.getValue());
        }
    }

    private PlayerEntity findClosestShielder(float maxDistance) {
        PlayerEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive()
                && !player.equals(mc.player)
                && !Prestige.friendManager.isFriend(player.getName().getString())
                && TargetSelector.isBlockingWithShield(player)) {
                Vec3d levelPos = new Vec3d(player.getX(), mc.player.getY(), player.getZ());
                double distanceSq = mc.player.squaredDistanceTo(levelPos);
                if (!(distanceSq > maxDistance * maxDistance) && distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = player;
                }
            }
        }

        return closest;
    }

    private void restorePreviousSlot() {
        if (this.previousSlot != -1) {
            InventoryUtil.selectSlot(this.previousSlot);
            this.previousSlot = -1;
        }
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.AutoAttackEvent;
import dev.zprestige.prestige.event.ExtendedReachAttackEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.inventory.DeathLootDetector;
import dev.zprestige.prestige.module.spear.ExtendedReach;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.MouseAccessor;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.util.SimulatedClickManager;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import org.lwjgl.glfw.GLFW;

public class Triggerbot extends Module {
    private MultiSetting targets;
    private BoolSetting stickyTarget;
    private FloatSetting stickyRange;
    private MultiSetting swordSettings;
    private MultiSetting crystalSettings;
    private BoolSetting requireLeftClick;
    private BoolSetting pauseInScreen;
    private BoolSetting ignoreFriends;
    private BoolSetting debugTargets;
    private RangeSetting delay;
    private RangeSetting cooldown;
    private BoolSetting tpsSync;
    private FloatSetting missChance;
    private RangeSetting customCooldown;
    private StopWatch delayTimer = new StopWatch();
    private StopWatch customCooldownTimer = new StopWatch();
    private int stickyTargetId = -1;
    private int trackedTargetId = -1;
    private boolean trackedTargetMissed = false;
    private boolean trackedViaExtendedReach = false;
    private long lastTickNanos = System.nanoTime();
    private double estimatedTps = 20.0;
    private int lastDebugEntityId = -1;
    private long lastDebugTime = 0L;

    public Triggerbot() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.cooldown
            .visibleWhen(
                (Predicate<Float>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Players")
            );
        this.missChance
            .visibleWhen(
                (Predicate<Float>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Players")
            );
        this.crystalSettings
            .visibleWhen(
                (Predicate<String>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Crystals")
            );
        this.tpsSync
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Players")
            );
        this.stickyTarget
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Players")
            );
        this.stickyRange
            .visibleWhen(
                (Predicate<Float>)ignored -> this.stickyTarget.getValue()
                        && (this.targets.isSelected("Any") || this.targets.isSelected("Players"))
            );
        this.customCooldown
            .visibleWhen(
                (Predicate<Float>)ignored -> (
                            this.targets.isSelected("Any") || this.targets.isSelected("Players")
                        )
                        && !this.swordSettings.isSelected("Weapon Only")
            );
        this.swordSettings
            .visibleWhen(
                (Predicate<String>)ignored -> this.targets.isSelected("Any")
                        || this.targets.isSelected("Players")
            );
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    public void updateTpsEstimate() {
        long now = System.nanoTime();
        long elapsedNanos = now - this.lastTickNanos;
        if (elapsedNanos > 0L) {
            this.estimatedTps = Math.min(20.0, 1.0 / (elapsedNanos / 1.0E9));
        }

        this.lastTickNanos = now;
    }

    private double applyTpsSync(float threshold) {
        return threshold + (1.0F - threshold) * ((20.0 - Math.clamp(this.estimatedTps, 10.0, 20.0)) / 10.0);
    }

    @Override
    public void onDisable() {
        this.stickyTargetId = -1;
        this.resetMissTracking();
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.stickyTargetId = -1;
        this.resetMissTracking();
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerRespawnS2CPacket) {
            this.stickyTargetId = -1;
            this.resetMissTracking();
        }
    }

    private void resetMissTracking() {
        this.trackedTargetId = -1;
        this.trackedTargetMissed = false;
        this.trackedViaExtendedReach = false;
    }

    private void updateTrackedTarget(Entity target, boolean viaExtendedReach) {
        if (!TargetSelector.isPlayer(target)) {
            this.resetMissTracking();
        } else {
            if (target.getId() != this.trackedTargetId || viaExtendedReach != this.trackedViaExtendedReach) {
                this.trackedTargetId = target.getId();
                this.trackedTargetMissed = false;
                this.trackedViaExtendedReach = viaExtendedReach;
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.updateTpsEstimate();
        if (mc.player != null && mc.world != null) {
            if (this.stickyTargetId != -1) {
                Entity stickyEntity = mc.world.getEntityById(this.stickyTargetId);
                if (stickyEntity == null || !stickyEntity.isAlive() || mc.player.distanceTo(stickyEntity) > this.stickyRange.getValue()) {
                    this.stickyTargetId = -1;
                }
            }

            if (mc.crosshairTarget == null) {
                this.resetMissTracking();
            } else if ((!this.pauseInScreen.getValue() || mc.currentScreen == null) && mc.isWindowFocused() && !mc.player.isUsingItem()) {
                if (!PlayerInteractions.isBlocking()) {
                    if (this.requireLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) != 1) {
                        this.resetMissTracking();
                    } else {
                        Entity retainedReachTarget = null;
                        if (this.trackedTargetMissed) {
                            if (this.trackedViaExtendedReach) {
                                retainedReachTarget = ExtendedReach.findExtendedReachTarget();
                                if (retainedReachTarget == null || retainedReachTarget.getId() != this.trackedTargetId) {
                                    this.resetMissTracking();
                                    retainedReachTarget = null;
                                }
                            } else if (!(mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity().getId() == this.trackedTargetId)) {
                                this.resetMissTracking();
                            }
                        }

                        if (this.delayTimer.hasElapsedRandom(this.delay)) {
                            Entity reachTarget = retainedReachTarget != null ? retainedReachTarget : ExtendedReach.findExtendedReachTarget();
                            HitResult crosshairHit = mc.crosshairTarget;
                            if ((crosshairHit == null || !crosshairHit.getType().equals(Type.ENTITY)) && reachTarget == null) {
                                this.resetMissTracking();
                                this.delay.reroll();
                                this.delayTimer.reset();
                            } else {
                                Entity target = reachTarget != null ? reachTarget : ((EntityHitResult)crosshairHit).getEntity();
                                this.updateTrackedTarget(target, Boolean.valueOf((reachTarget != null ? 1 : 0) != 0));
                                if (!this.isAttackable(target)) {
                                    this.resetMissTracking();
                                    if (this.debugTargets.getValue()) {
                                        this.logRejection(target);
                                    }
                                } else {
                                    boolean holdingWeapon = InventoryUtil.isSword(mc.player.getMainHandStack())
                                        || mc.player.getMainHandStack().getItem() instanceof AxeItem
                                        || InventoryUtil.isMace(mc.player.getMainHandStack());
                                    if (!(target instanceof PlayerEntity)
                                            && (!this.targets.isSelected("Any") || target instanceof EndCrystalEntity)
                                        || !this.swordSettings.isSelected("Weapon Only")
                                        || holdingWeapon) {
                                        if (!(target instanceof EndCrystalEntity)
                                            || !this.crystalSettings.isSelected("Crystal Only")
                                            || mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) {
                                            float cooldownThreshold = this.cooldown.randomInRange() / 100.0F;
                                            if (this.tpsSync.getValue()) {
                                                cooldownThreshold = (float)this.applyTpsSync(cooldownThreshold);
                                            }

                                            if (!(target instanceof EndCrystalEntity)) {
                                                if (this.swordSettings.isSelected("Weapon Only")) {
                                                    if (mc.player.getAttackCooldownProgress(0.0F) < cooldownThreshold) {
                                                        return;
                                                    }
                                                } else if (!this.customCooldownTimer.hasElapsedRandom(this.customCooldown)) {
                                                    return;
                                                }
                                            }

                                            if (!this.crystalSettings.isSelected("On Ground Only")
                                                || !(target instanceof EndCrystalEntity)
                                                || mc.player.isOnGround()) {
                                                if (!(target instanceof PlayerEntity)
                                                    || !TargetSelector.isBlockingWithShield((PlayerEntity)target)
                                                    || mc.player.getMainHandStack().getItem() instanceof AxeItem) {
                                                    if (target instanceof PlayerEntity
                                                        && holdingWeapon
                                                        && !mc.player.isOnGround()
                                                        && mc.player.fallDistance > 0.0
                                                        && mc.player.getVelocity().y <= 0.0) {
                                                    } else {
                                                    }

                                                    if (!this.swordSettings.isSelected("Criticals Timing")
                                                        || !(target instanceof PlayerEntity)
                                                        || !holdingWeapon
                                                        || mc.player.isOnGround()
                                                        || !(mc.player.fallDistance <= 0.0) && !(mc.player.getVelocity().y >= 0.0)) {
                                                        if (!this.swordSettings.isSelected("Crit Only")
                                                            || !(target instanceof PlayerEntity)
                                                            || !holdingWeapon
                                                            || !mc.player.isOnGround() && !(mc.player.fallDistance <= 0.0) && !(mc.player.getVelocity().y >= 0.0)) {
                                                            if (!this.crystalSettings.isSelected("Pause On Kill")
                                                                || !(target instanceof EndCrystalEntity)
                                                                || !DeathLootDetector.isLootNearby(target.getBlockPos())) {
                                                                if (this.stickyTarget.getValue() && target instanceof PlayerEntity) {
                                                                    int targetId = target.getId();
                                                                    if (this.stickyTargetId == -1) {
                                                                        this.stickyTargetId = targetId;
                                                                    } else if (targetId != this.stickyTargetId) {
                                                                        return;
                                                                    }
                                                                }

                                                                if (target instanceof PlayerEntity) {
                                                                    if (this.missChance.getValue() <= 0.0F) {
                                                                        this.trackedTargetMissed = false;
                                                                    } else {
                                                                        if (this.trackedTargetMissed) {
                                                                            return;
                                                                        }

                                                                        if (RandomUtil.randomFloat(0.0F, 100.0F) < this.missChance.getValue()) {
                                                                            this.trackedTargetMissed = true;
                                                                            this.delay.reroll();
                                                                            this.delayTimer.reset();
                                                                            return;
                                                                        }
                                                                    }
                                                                }

                                                                if (target instanceof PlayerEntity) {
                                                                    new AutoAttackEvent().post();
                                                                }

                                                                if (reachTarget != null) {
                                                                    new ExtendedReachAttackEvent().post();
                                                                    new MouseAccessor(mc.mouse).invokeMouseButton(mc.getWindow().getHandle(), SimulatedClickManager.attackButton, 1, 0);
                                                                    new MouseAccessor(mc.mouse).invokeMouseButton(mc.getWindow().getHandle(), SimulatedClickManager.attackButton, 0, 0);
                                                                } else {
                                                                    PlayerInteractions.attackEntity(target);
                                                                }

                                                                this.delay.reroll();
                                                                this.delayTimer.reset();
                                                                this.customCooldown.reroll();
                                                                this.customCooldownTimer.reset();
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
                    }
                }
            }
        } else {
            this.resetMissTracking();
        }
    }

    private boolean isAttackable(Entity entity) {
        if (this.ignoreFriends.getValue() && entity instanceof PlayerEntity player && Prestige.friendManager.isFriend(player.getName().getString())) {
            return false;
        } else {
            return !(entity instanceof PlayerEntity playerTarget && (playerTarget.isSpectator() || playerTarget.isDead() || playerTarget.isRemoved() || playerTarget.getAbilities().invulnerable))
                ? entity != null
                    && entity != mc.player
                    && (this.swordSettings.isSelected("Attack Invisibles") || !entity.isInvisible())
                    && (
                        this.targets.isSelected("Any")
                            || this.targets.isSelected("Players") && TargetSelector.isPlayer(entity)
                            || this.targets.isSelected("Crystals") && entity instanceof EndCrystalEntity
                    )
                : false;
        }
    }

    private void logRejection(Entity entity) {
        long now = System.currentTimeMillis();
        if (entity.getId() != this.lastDebugEntityId || now - this.lastDebugTime >= 3000L) {
            this.lastDebugEntityId = entity.getId();
            this.lastDebugTime = now;
            String entityName = entity.getName().getString();
            String className = entity.getClass().getSimpleName();
            StringBuilder reasons = new StringBuilder();
            if (!(entity instanceof PlayerEntity player)) {
                reasons.append("not-a-player-type ");
            } else {
                if (this.ignoreFriends.getValue() && Prestige.friendManager.isFriend(entityName)) {
                    reasons.append("friend ");
                }

                if (player.isSpectator()) {
                    reasons.append("spectator ");
                }

                if (player.isDead()) {
                    reasons.append("dead ");
                }

                if (player.isRemoved()) {
                    reasons.append("removed ");
                }

                if (player.getAbilities().invulnerable) {
                    reasons.append("invulnerable ");
                }

                if (!this.targets.isSelected("Any") && !this.targets.isSelected("Players")) {
                    reasons.append("targets-filter ");
                }

                reasons.append("uuid-v").append(player.getUuid().version()).append(' ');
            }

            if (entity instanceof EndCrystalEntity && !this.targets.isSelected("Crystals") && !this.targets.isSelected("Any")) {
                reasons.append("crystals-off ");
            }

            if (!this.swordSettings.isSelected("Attack Invisibles") && entity.isInvisible()) {
                reasons.append("invisible ");
            }

            if (reasons.length() == 0) {
                reasons.append("unknown");
            }

            mc.player.sendMessage(Text.literal("§7[Triggerbot] §f" + entityName + " §8(" + className + ") §crejected:§7 " + String.valueOf(reasons)), false);
        }
    }
}

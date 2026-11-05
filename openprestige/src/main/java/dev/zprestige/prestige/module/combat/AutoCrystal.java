package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.data.QueuedCrystalAttack;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.inventory.DeathLootDetector;
import dev.zprestige.prestige.math.AutoCrystalRenderer;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AutoCrystal extends Module {
    public static boolean active = false;
    private BoolSetting inAir;
    private BoolSetting damageTick;
    private BoolSetting pauseOnKill;
    private RangeSetting delay;
    private BoolSetting autoSwitch;
    private BoolSetting headBob;
    public BoolSetting render;
    public ColorSetting color;
    private BoolSetting alwaysBreakLast;
    private StopWatch delayTimer = new StopWatch();
    private BlockPos lastPlacePos = null;
    private ArrayDeque queuedAttacks = new ArrayDeque();
    private boolean wasSprinting;
    private float lastCrystalDamage = 0.0F;
    private AutoCrystalRenderer crystalRenderer = new AutoCrystalRenderer(this);
    private boolean pendingDisable = false;
    private int pendingDisableTicks = 0;

    public AutoCrystal() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.color.visibleWhen((Predicate<Color>)ignored -> this.render.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        active = true;
        this.pendingDisable = false;
        this.pendingDisableTicks = 0;
        this.crystalRenderer.reset();
        Prestige.eventBus.register(this.crystalRenderer);
    }

    @Override
    public void onDisable() {
        active = false;
        this.pendingDisable = false;
        this.queuedAttacks.clear();
        this.wasSprinting = false;
        Prestige.eventBus.unregister(this.crystalRenderer);
        this.crystalRenderer.reset();
        this.lastPlacePos = null;
    }

    @Override
    public void toggle() {
        if (this.isEnabled()) {
            if (!this.pendingDisable && this.alwaysBreakLast.getValue() && this.getCrosshairCrystal() != null) {
                this.pendingDisable = true;
                this.pendingDisableTicks = 0;
                return;
            }

            this.pendingDisable = false;
        }

        super.toggle();
    }

    private Entity getCrosshairCrystal() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.ENTITY) {
            Entity entity = ((EntityHitResult)mc.crosshairTarget).getEntity();
            if (!(entity instanceof EndCrystalEntity) && !(entity instanceof SlimeEntity) && !(entity instanceof MagmaCubeEntity)) {
                return null;
            } else {
                return !entity.isAlive() ? null : entity;
            }
        } else {
            return null;
        }
    }

    private void breakLastCrystal(SendMovementPacketsEvent event) {
        if (event.getPhase() != EventPhase.POST) {
            Entity crystal = this.getCrosshairCrystal();
            if (crystal == null || ++this.pendingDisableTicks > 20) {
                this.pendingDisable = false;
                super.toggle();
            } else if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem() && !Prestige.silentRotationManager.hasActedThisTick()) {
                event.cancel();
                this.attackCrystal(crystal, event.getYaw(), event.getPitch());
                this.lastPlacePos = null;
                this.crystalRenderer.setTargetPos(crystal.getBlockPos().down());
            }
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.getKey() == 9998 && mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)) {
            event.cancel();
        }
    }

    private void attackCrystal(Entity crystal, float yaw, float pitch) {
        if (this.wasSprinting) {
            if (this.queuedAttacks.size() < 4 && this.queuedAttacks.stream().noneMatch(queuedAttack -> ((dev.zprestige.prestige.data.QueuedCrystalAttack)queuedAttack).entity() == crystal)) {
                this.queuedAttacks.add(new QueuedCrystalAttack(crystal, yaw, pitch));
            }
        } else {
            PlayerInteractions.attackEntity(crystal);
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (!this.queuedAttacks.isEmpty()) {
            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                QueuedCrystalAttack attack = (QueuedCrystalAttack)this.queuedAttacks.poll();
                if (attack != null && attack.entity().isAlive()) {
                    Vec3d eyePos = mc.player.getEyePos();
                    Optional hitPoint = attack.entity().getBoundingBox().expand(0.1).raycast(eyePos, eyePos.add(Vec3d.fromPolar(attack.pitch(), attack.yaw()).multiply(3.0)));
                    if (!hitPoint.isEmpty() && !(eyePos.distanceTo((Vec3d)hitPoint.get()) > 2.9)) {
                        PlayerInteractions.attackEntity(attack.entity());
                    }
                }
            }
        }
    }

    private static Vec3d getClosestPoint(Box box, Vec3d point) {
        Vec3d clamped = new Vec3d(MathHelper.clamp(point.x, box.minX, box.maxX), MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ));
        return clamped.equals(point) ? box.getCenter() : clamped;
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (event.getPhase() == EventPhase.POST) {
            this.wasSprinting = mc.player != null && mc.player.isSprinting();
        } else if (this.pendingDisable) {
            this.breakLastCrystal(event);
        } else {
            if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem() && (mc.player.isOnGround() || this.inAir.getValue())) {
                if (this.delayTimer.hasElapsed(this.delay.getValue()) && !AutoHitCrystal.active && !AutoObbyCrystal.sequenceActive && event.getPhase() != EventPhase.POST) {
                    if (Prestige.silentRotationManager.hasActedThisTick()) {
                        return;
                    }

                    if (mc.crosshairTarget == null) {
                        return;
                    }

                    event.cancel();
                    if (!InventoryUtil.isHolding(Items.END_CRYSTAL)) {
                        if (this.autoSwitch.getValue()) {
                            Integer crystalSlot = InventoryUtil.findHotbarSlot(Items.END_CRYSTAL);
                            if (crystalSlot == null) {
                                return;
                            }

                            InventoryUtil.selectSlot(crystalSlot);
                            this.delayTimer.reset();
                            return;
                        }

                        return;
                    }

                    if (!this.delayTimer.hasElapsedRandom(this.delay)) {
                        return;
                    }

                    this.resetDelay();
                    PlayerEntity enemy = TargetSelector.getClosestEnemy();
                    if (this.passesDamageTick(enemy)) {
                        if (mc.crosshairTarget.getType() == Type.ENTITY) {
                            EntityHitResult entityHit = (EntityHitResult)mc.crosshairTarget;
                            if (entityHit.getEntity() instanceof EndCrystalEntity
                                || entityHit.getEntity() instanceof SlimeEntity
                                || entityHit.getEntity() instanceof MagmaCubeEntity) {
                                if (this.pauseOnKill.getValue() && DeathLootDetector.isLootNearby(entityHit.getEntity().getBlockPos())) {
                                    return;
                                }

                                this.attackCrystal(entityHit.getEntity(), event.getYaw(), event.getPitch());
                                if (enemy != null) {
                                    this.lastCrystalDamage = (float)Prestige.damageCalculator
                                        .getCrystalDamage(enemy, PositionUtil.getEntityPos(entityHit.getEntity()));
                                }

                                this.lastPlacePos = null;
                                this.crystalRenderer.setTargetPos(entityHit.getEntity().getBlockPos().down());
                            }
                        } else if (this.headBob.getValue()) {
                            BlockPos basePos = this.lastPlacePos;
                            if (basePos == null && mc.crosshairTarget.getType() == Type.BLOCK) {
                                basePos = ((BlockHitResult)mc.crosshairTarget).getBlockPos();
                            }

                            if (basePos == null) {
                                return;
                            }

                            for (Entity entity : mc.world.getEntities()) {
                                if (entity instanceof EndCrystalEntity) {
                                    Vec3d crystalPos = PositionUtil.getEntityPos(entity);
                                    if (crystalPos.distanceTo(basePos.up().toCenterPos()) < 1.0) {
                                        Box box = entity.getBoundingBox();
                                        Vec3d eyePos = mc.player.getEyePos();
                                        double dx = Math.max(box.minX - eyePos.x, Math.max(0.0, eyePos.x - box.maxX));
                                        double dy = Math.max(box.minY - eyePos.y, Math.max(0.0, eyePos.y - box.maxY));
                                        double dz = Math.max(box.minZ - eyePos.z, Math.max(0.0, eyePos.z - box.maxZ));
                                        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= 2.7F
                                            && (!this.pauseOnKill.getValue() || !DeathLootDetector.isLootNearby(entity.getBlockPos()))) {
                                            Vec3d closestPoint = getClosestPoint(box, eyePos);
                                            Vec3d aimPoint = closestPoint.add(box.getCenter().subtract(closestPoint).multiply(0.3));
                                            float pitch = MathHelper.clamp(RotationUtil.getRotationToPoint((float)aimPoint.x, (float)aimPoint.y, (float)aimPoint.z).getPitch(), -89.9F, 89.9F);
                                            Vec3d lookVec = Vec3d.fromPolar(pitch, event.getYaw());
                                            Optional hitPoint = box.expand(0.1).raycast(eyePos, eyePos.add(lookVec.multiply(3.0)));
                                            if (!hitPoint.isEmpty() && !(eyePos.distanceTo((Vec3d)hitPoint.get()) > 2.9)) {
                                                event.setPitch(pitch);
                                                this.attackCrystal(entity, event.getYaw(), pitch);
                                                if (enemy != null) {
                                                    this.lastCrystalDamage = (float)Prestige.damageCalculator.getCrystalDamage(enemy, crystalPos);
                                                }

                                                this.lastPlacePos = null;
                                                this.crystalRenderer.setTargetPos(entity.getBlockPos().down());
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (mc.crosshairTarget.getType() == Type.BLOCK) {
                        BlockHitResult blockHit = (BlockHitResult)mc.crosshairTarget;
                        if (!BlockUtil.isBlock(blockHit.getBlockPos(), Blocks.BEDROCK)
                            && !BlockUtil.isBlock(blockHit.getBlockPos(), Blocks.OBSIDIAN)) {
                            return;
                        }

                        if (BlockUtil.hasEntityAt(blockHit.getBlockPos().up())
                            || !BlockUtil.canPlaceCrystal(blockHit.getBlockPos())) {
                            return;
                        }

                        if (!InventoryUtil.isHolding(Items.END_CRYSTAL)) {
                            return;
                        }

                        PlayerInteractions.interactBlock(blockHit);
                        this.lastPlacePos = blockHit.getBlockPos();
                        this.crystalRenderer.setTargetPos(blockHit.getBlockPos());
                    }

                    return;
                }
            }
        }
    }

    private void resetDelay() {
        this.delay.reroll();
        this.delayTimer.reset();
    }

    private boolean passesDamageTick(PlayerEntity target) {
        if (this.damageTick.getValue() && target != null) {
            if (target.hurtTime == 0) {
                this.lastCrystalDamage = 0.0F;
            }

            Vec3d crystalPos = Vec3d.ZERO;
            if (mc.crosshairTarget instanceof BlockHitResult) {
                crystalPos = mc.crosshairTarget.getPos().add(0.0, 1.0, 0.0);
            } else if (mc.crosshairTarget instanceof EntityHitResult) {
                crystalPos = PositionUtil.getEntityPos(((EntityHitResult)mc.crosshairTarget).getEntity());
            }

            return !(((float)Prestige.damageCalculator.getCrystalDamage(target, crystalPos)) < this.lastCrystalDamage);
        } else {
            return true;
        }
    }
}

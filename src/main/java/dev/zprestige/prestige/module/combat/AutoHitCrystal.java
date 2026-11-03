package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.inventory.DeathLootDetector;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AutoHitCrystal extends Module {
    public static boolean active = false;
    private BoolSetting stopInAir;
    private BoolSetting swordOnly;
    private BoolSetting pauseOnKill;
    private RangeSetting delay;
    private BoolSetting perfectTiming;
    private EnumSetting crystalMode;
    private RangeSetting crystalDelay;
    private BoolSetting silentRotations;
    private StopWatch delayTimer = new StopWatch();
    private int stage = 0;

    public AutoHitCrystal() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.crystalDelay
            .visibleWhen(
                (Predicate<Float>)ignored -> this.crystalMode.getValue().equals("Double Tap") || this.crystalMode.getValue().equals("Infinite")
            );
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public boolean isPersistent() {
        return "Infinite".equals(this.crystalMode.getValue());
    }

    @Override
    public void onEnable() {
        if (this.getKey() == 9998 && PlayerInteractions.isHoldingUsableItem()) {
            this.toggle();
        } else {
            if (!this.crystalMode.getValue().equals("None")) {
                active = true;
            }
        }
    }

    @Override
    public void onDisable() {
        active = false;
        this.stage = 0;
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.getKey() == 9998
            && (mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL) || mc.player.getMainHandStack().getItem().equals(Blocks.OBSIDIAN.asItem()))) {
            event.cancel();
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (mc.currentScreen == null
            && mc.isWindowFocused()
            && this.delayTimer.hasElapsedRandom(this.delay)
            && (!this.stopInAir.getValue() || mc.player.isOnGround())
            && !mc.player.isUsingItem()) {
            if (this.getKey() == 9998 && PlayerInteractions.isHoldingUsableItem()) {
                active = false;
            } else if (!InventoryUtil.isHolding(Items.NETHERITE_SPEAR)) {
                if (!Prestige.silentRotationManager.hasActedThisTick()) {
                    PlayerEntity enemy = TargetSelector.getClosestEnemy();
                    if (enemy != null && enemy.distanceTo(mc.player) > 10.0F) {
                        enemy = null;
                    }

                    if (this.stage == 0 && this.swordOnly.getValue() && !InventoryUtil.isSword(mc.player.getMainHandStack())) {
                        this.toggle();
                    } else {
                        HitResult hitResult = mc.crosshairTarget;
                        if (hitResult != null) {
                            if (!this.crystalMode.getValue().equals("None")) {
                                active = true;
                            }

                            switch (this.stage) {
                                case 0:
                                    if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystalEntity) {
                                        if (this.crystalMode.getValue().equals("None")) {
                                            active = false;
                                            return;
                                        }

                                        if (this.pauseOnKill.getValue() && DeathLootDetector.isLootNearby(entityHit.getEntity().getBlockPos())) {
                                            active = false;
                                            return;
                                        }

                                        if (this.perfectTiming.getValue() && enemy != null && enemy.hurtTime != 0) {
                                            return;
                                        }

                                        PlayerInteractions.attackEntity(entityHit.getEntity());
                                        this.delay.reroll();
                                        this.delayTimer.reset();
                                        active = false;
                                        return;
                                    }

                                    if (!(hitResult instanceof BlockHitResult) || !BlockUtil.isClickableHit(hitResult)) {
                                        active = false;
                                        return;
                                    }

                                    if (mc.world.getBlockState(((BlockHitResult)hitResult).getBlockPos()).getBlock() == Blocks.OBSIDIAN) {
                                        this.stage = 2;
                                        return;
                                    }

                                    if (!InventoryUtil.switchToBlock(Blocks.OBSIDIAN)) {
                                        active = false;
                                        return;
                                    }

                                    this.nextStage();
                                    break;
                                case 1:
                                    if (!(hitResult instanceof BlockHitResult)) {
                                        active = false;
                                        return;
                                    }

                                    if (!BlockUtil.isClickableHit(hitResult)) {
                                        active = false;
                                        return;
                                    }

                                    if (BlockUtil.hasEntityAt(((BlockHitResult)hitResult).getBlockPos().offset(((BlockHitResult)hitResult).getSide()))) {
                                        active = false;
                                        return;
                                    }

                                    if (mc.world.getBlockState(((BlockHitResult)hitResult).getBlockPos()).getBlock() == Blocks.OBSIDIAN) {
                                        active = false;
                                        this.nextStage();
                                        return;
                                    }

                                    if (BlockUtil.hasEntityAt(((BlockHitResult)hitResult).getBlockPos())) {
                                        active = false;
                                        return;
                                    }

                                    if (!InventoryUtil.runWithBlock(Blocks.OBSIDIAN, () -> PlayerInteractions.interactBlock((BlockHitResult)hitResult), false)) {
                                        return;
                                    }

                                    this.nextStage();
                                    break;
                                case 2:
                                    if (!InventoryUtil.switchToItem(Items.END_CRYSTAL)) {
                                        return;
                                    }

                                    this.nextStage();
                                    break;
                                case 3:
                                case 5:
                                    if (this.crystalMode.getValue().equals("None")) {
                                        this.toggle();
                                        return;
                                    }

                                    if (this.stage == 5 && !this.delayTimer.hasElapsedRandom(this.crystalDelay)) {
                                        return;
                                    }

                                    if (!(hitResult instanceof BlockHitResult)) {
                                        return;
                                    }

                                    BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
                                    if (mc.world.getBlockState(blockPos).getBlock() != Blocks.OBSIDIAN) {
                                        return;
                                    }

                                    if (BlockUtil.hasEntityAt(blockPos)) {
                                        return;
                                    }

                                    if (!InventoryUtil.runWithItem(Items.END_CRYSTAL, () -> PlayerInteractions.interactBlock((BlockHitResult)hitResult), false)) {
                                        return;
                                    }

                                    this.nextStage();
                                    break;
                                case 4:
                                    if (this.perfectTiming.getValue() && enemy != null && enemy.hurtTime != 0) {
                                        return;
                                    }

                                    if (mc.crosshairTarget.getType() == Type.ENTITY && ((EntityHitResult)hitResult).getEntity() instanceof EndCrystalEntity) {
                                        if (this.pauseOnKill.getValue()
                                            && DeathLootDetector.isLootNearby(((EntityHitResult)hitResult).getEntity().getBlockPos())) {
                                            return;
                                        }

                                        PlayerInteractions.attackEntity(((EntityHitResult)hitResult).getEntity());
                                        if (!this.crystalMode.getValue().equals("Double Tap") && !this.crystalMode.getValue().equals("Infinite")) {
                                            this.toggle();
                                        } else {
                                            this.nextStage();
                                        }

                                        return;
                                    }

                                    if (this.silentRotations.getValue()) {
                                        this.breakCrystalSilently(event);
                                    }
                                    break;
                                case 6:
                                    if (!this.delayTimer.hasElapsedRandom(this.crystalDelay)) {
                                        return;
                                    }

                                    if (this.perfectTiming.getValue() && enemy != null && enemy.hurtTime != 0) {
                                        return;
                                    }

                                    if (mc.crosshairTarget.getType() == Type.ENTITY && ((EntityHitResult)hitResult).getEntity() instanceof EndCrystalEntity) {
                                        if (this.pauseOnKill.getValue()
                                            && DeathLootDetector.isLootNearby(((EntityHitResult)hitResult).getEntity().getBlockPos())) {
                                            return;
                                        }

                                        PlayerInteractions.attackEntity(((EntityHitResult)hitResult).getEntity());
                                        if (this.crystalMode.getValue().equals("Infinite")) {
                                            this.stage = 5;
                                            this.delay.reroll();
                                            this.crystalDelay.reroll();
                                            this.delayTimer.reset();
                                        } else {
                                            this.toggle();
                                        }

                                        return;
                                    }

                                    if (this.silentRotations.getValue()) {
                                        this.breakCrystalSilently(event);
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    private void breakCrystalSilently(SendMovementPacketsEvent event) {
        BlockPos obsidianPos = null;
        if (mc.crosshairTarget.getType() == Type.BLOCK) {
            obsidianPos = ((BlockHitResult)mc.crosshairTarget).getBlockPos();
        }

        if (obsidianPos != null && mc.world.getBlockState(obsidianPos).getBlock() == Blocks.OBSIDIAN) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity) {
                    Vec3d crystalPos = PositionUtil.getEntityPos(entity);
                    if (crystalPos.distanceTo(obsidianPos.up().toCenterPos()) < 1.0) {
                        Box box = entity.getBoundingBox();
                        Vec3d eyePos = mc.player.getEyePos();
                        double dx = Math.max(box.minX - eyePos.x, Math.max(0.0, eyePos.x - box.maxX));
                        double dy = Math.max(box.minY - eyePos.y, Math.max(0.0, eyePos.y - box.maxY));
                        double dz = Math.max(box.minZ - eyePos.z, Math.max(0.0, eyePos.z - box.maxZ));
                        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= 2.7F && (!this.pauseOnKill.getValue() || !DeathLootDetector.isLootNearby(entity.getBlockPos()))) {
                            event.setPitch(RotationUtil.getRotationToPoint((float)crystalPos.x, (float)(crystalPos.y + 0.45F), (float)crystalPos.z).getPitch());
                            PlayerInteractions.attackEntity(entity);
                            if (this.crystalMode.getValue().equals("Infinite")) {
                                this.stage = 5;
                                this.delay.reroll();
                                this.delayTimer.reset();
                                return;
                            } else {
                                if (this.stage != 6 && this.crystalMode.getValue().equals("Double Tap")) {
                                    this.nextStage();
                                } else {
                                    this.toggle();
                                }

                                return;
                            }
                        }
                    }
                }
            }
        } else {
            this.toggle();
        }
    }

    private void nextStage() {
        this.crystalDelay.reroll();
        this.delay.reroll();
        this.stage++;
        this.delayTimer.reset();
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;

public class SwapHit extends Module {
    public SwapHit() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting totemOnly;
    private FloatSetting swapDelay;
    private FloatSetting swapBackDelay;
    private BoolSetting shieldAware;
    private BoolSetting preferAxe;
    private StopWatch swapTimer = new StopWatch();
    private StopWatch swapBackTimer = new StopWatch();
    private int previousSlot = -1;
    private int swapStage = 0;

    @Override
    public void onEnable() {
        this.previousSlot = -1;
        this.swapStage = 0;
    }

    @Override
    public void onDisable() {
        this.previousSlot = -1;
        this.swapStage = 0;
    }

    private int findSwordSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (InventoryUtil.isSword(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private int findAxeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (InventoryUtil.isAxe(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (mc.player != null && mc.world != null) {
            if (mc.isWindowFocused() && mc.currentScreen == null) {
                if (!InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    if (!this.totemOnly.getValue() || mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                            if (entityHit.getEntity() instanceof PlayerEntity target) {
                                int weaponSlot;
                                if (this.shieldAware.getValue() && TargetSelector.isBlockingWithShield(target)) {
                                    if (!this.preferAxe.getValue()) {
                                        return;
                                    }

                                    int axeSlot = this.findAxeSlot();
                                    if (axeSlot == -1) {
                                        return;
                                    }

                                    weaponSlot = axeSlot;
                                } else {
                                    int swordSlot = this.findSwordSlot();
                                    if (swordSlot == -1) {
                                        return;
                                    }

                                    weaponSlot = swordSlot;
                                }

                                event.cancel();
                                this.previousSlot = InventoryUtil.getSelectedSlot();
                                InventoryUtil.selectSlot(weaponSlot);
                                this.swapStage = 1;
                                this.swapTimer.reset();
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.swapStage != 0) {
            if (mc.player != null && mc.world != null) {
                if (this.swapStage == 1) {
                    if (!this.swapTimer.hasElapsed(this.swapDelay.getValue())) {
                        return;
                    }

                    if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                        Entity target = entityHit.getEntity();
                        if (target != null) {
                            PlayerInteractions.attackEntity(target);
                        }
                    }

                    this.swapStage = 2;
                    this.swapBackTimer.reset();
                } else if (this.swapStage == 2) {
                    if (!this.swapBackTimer.hasElapsed(this.swapBackDelay.getValue())) {
                        return;
                    }

                    if (this.previousSlot != -1) {
                        InventoryUtil.selectSlot(this.previousSlot);
                        this.previousSlot = -1;
                    }

                    this.swapStage = 0;
                }
            } else {
                this.swapStage = 0;
                this.previousSlot = -1;
            }
        }
    }
}

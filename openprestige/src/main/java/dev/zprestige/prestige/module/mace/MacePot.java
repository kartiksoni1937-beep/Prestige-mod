package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;

public class MacePot extends Module implements RotationProvider {
    public MacePot() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting delay;
    private BoolSetting autoDisable;
    private FloatSetting pitch;
    private StopWatch throwTimer = new StopWatch();
    private StopWatch swapTimer = new StopWatch();
    private StopWatch speedPotTimer = new StopWatch();
    private StopWatch strengthPotTimer = new StopWatch();
    private Integer swappedInventorySlot = null;
    private Integer swappedHotbarSlot = null;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return Boolean.FALSE.equals(this.autoDisable.getValue());
    }

    @Override
    public Rotation provideRotation() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!this.throwTimer.hasElapsed(this.delay.getValue())) {
                return null;
            } else {
                boolean hasSpeed = mc.player.hasStatusEffect(StatusEffects.SPEED);
                boolean hasStrength = mc.player.hasStatusEffect(StatusEffects.STRENGTH);
                if (mc.player.isOnGround()) {
                    if (!hasSpeed) {
                        if (this.speedPotTimer.hasElapsed(300.0F)) {
                            Integer speedHotbarSlot = InventoryUtil.findSplashPotion(0, 9, StatusEffects.SPEED);
                            if (speedHotbarSlot != null) {
                                this.throwTimer.reset();
                                this.speedPotTimer.reset();
                                return this.throwPotion(speedHotbarSlot);
                            }

                            Integer speedInventorySlot = InventoryUtil.findSplashPotion(0, 36, StatusEffects.SPEED);
                            if (speedInventorySlot != null) {
                                this.swapPotionIntoHotbar(speedInventorySlot);
                                this.throwTimer.reset();
                                return null;
                            }
                        }
                    }

                    if (!hasStrength) {
                        if (this.strengthPotTimer.hasElapsed(300.0F)) {
                            Integer strengthHotbarSlot = InventoryUtil.findSplashPotion(0, 9, StatusEffects.STRENGTH);
                            if (strengthHotbarSlot != null) {
                                this.throwTimer.reset();
                                this.strengthPotTimer.reset();
                                return this.throwPotion(strengthHotbarSlot);
                            }

                            Integer strengthInventorySlot = InventoryUtil.findSplashPotion(0, 36, StatusEffects.STRENGTH);
                            if (strengthInventorySlot != null) {
                                this.swapPotionIntoHotbar(strengthInventorySlot);
                                this.throwTimer.reset();
                                return null;
                            }
                        }
                    }
                }

                if (hasSpeed && hasStrength && this.swappedInventorySlot != null && this.swappedHotbarSlot != null) {
                    InventoryUtil.swapSlot(this.swappedInventorySlot, this.swappedHotbarSlot);
                    this.swappedInventorySlot = null;
                    if (this.autoDisable.getValue()) {
                        this.toggle();
                    }

                    return null;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private void swapPotionIntoHotbar(int inventorySlot) {
        if (this.swappedInventorySlot == null && this.swappedHotbarSlot == null) {
            this.swappedInventorySlot = inventorySlot;
            this.swappedHotbarSlot = InventoryUtil.getSelectedSlot();
        }

        this.swapTimer.reset();
        InventoryUtil.swapSlot(inventorySlot, InventoryUtil.getSelectedSlot());
    }

    private Rotation throwPotion(int slot) {
        float throwPitch = this.pitch.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat();
        InventoryUtil.runWithSlot(slot, () -> {
            float originalPitch = mc.player.getPitch();
            mc.player.setPitch(this.pitch.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat());
            PlayerInteractions.interactItem(Hand.MAIN_HAND);
            mc.player.setPitch(originalPitch);
        }, true);
        return new Rotation(mc.player.getYaw(), throwPitch);
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.swappedInventorySlot = null;
    }
}

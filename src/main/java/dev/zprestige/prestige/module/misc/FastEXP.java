package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.function.Predicate;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import org.lwjgl.glfw.GLFW;

public class FastEXP extends Module implements RotationProvider {
    private EnumSetting mode;
    private RangeSetting delay;
    private BoolSetting rotateDown;
    private BoolSetting fromInventory;
    private BoolSetting swapBack;
    private StopWatch throwTimer = new StopWatch();
    private int previousSlot = -1;
    private int swappedInventorySlot = -1;

    public FastEXP() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.fromInventory.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
        this.swapBack.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Automatic"));
        this.rotateDown.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Manual"));
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.isHoldingExpBottle()) {
            event.cancel();
        }
    }

    @Override
    public Rotation provideRotation() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            return this.mode.getValue().equals("Automatic") ? this.runAutomaticMode() : this.runManualMode();
        } else {
            return null;
        }
    }

    private Rotation runManualMode() {
        if (this.isHoldingExpBottle() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) == 1) {
            if (this.throwTimer.hasElapsedRandom(this.delay)) {
                float originalPitch = mc.player.getPitch();
                if (this.rotateDown.getValue()) {
                    mc.player.setPitch(90.0F);
                }

                if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.BLOCK) {
                    PlayerInteractions.interactBlock((BlockHitResult)mc.crosshairTarget);
                }

                PlayerInteractions.interactItem(Hand.MAIN_HAND);
                this.delay.reroll();
                this.throwTimer.reset();
                if (this.rotateDown.getValue()) {
                    mc.player.setPitch(originalPitch);
                }
            }

            return this.rotateDown.getValue() ? new Rotation(mc.player.getYaw(), 90.0F) : null;
        } else {
            return null;
        }
    }

    private Rotation runAutomaticMode() {
        if (!this.hasDamagedMendingGear()) {
            this.restorePreviousSlot();
            return null;
        } else if (!this.isHoldingExpBottle()) {
            if (this.previousSlot != -1 && Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
                return null;
            } else if (this.previousSlot != -1) {
                this.restorePreviousSlot();
                return null;
            } else if (!this.throwTimer.hasElapsedRandom(this.delay)) {
                return null;
            } else {
                this.switchToExpBottles();
                this.throwTimer.reset();
                this.delay.reroll();
                return null;
            }
        } else if (!this.throwTimer.hasElapsedRandom(this.delay)) {
            return new Rotation(mc.player.getYaw(), 90.0F);
        } else {
            float originalPitch = mc.player.getPitch();
            mc.player.setPitch(90.0F);
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.BLOCK) {
                PlayerInteractions.interactBlock((BlockHitResult)mc.crosshairTarget);
            }

            PlayerInteractions.interactItem(Hand.MAIN_HAND);
            mc.player.setPitch(originalPitch);
            this.delay.reroll();
            this.throwTimer.reset();
            return new Rotation(mc.player.getYaw(), 90.0F);
        }
    }

    private boolean switchToExpBottles() {
        int hotbarSlot = this.findHotbarExpSlot();
        if (hotbarSlot != -1) {
            this.previousSlot = InventoryUtil.getSelectedSlot();
            this.swappedInventorySlot = -1;
            InventoryUtil.selectSlot(hotbarSlot);
            return true;
        } else if (!this.fromInventory.getValue()) {
            return false;
        } else {
            int inventorySlot = this.findInventoryExpSlot();
            if (inventorySlot == -1) {
                return false;
            } else {
                int selectedSlot = InventoryUtil.getSelectedSlot();
                this.previousSlot = selectedSlot;
                this.swappedInventorySlot = inventorySlot;
                InventoryUtil.swapSlot(inventorySlot, selectedSlot);
                return true;
            }
        }
    }

    private void restorePreviousSlot() {
        if (this.previousSlot != -1) {
            if (this.swapBack.getValue()) {
                if (this.swappedInventorySlot != -1) {
                    InventoryUtil.swapSlot(this.swappedInventorySlot, this.previousSlot);
                } else {
                    InventoryUtil.selectSlot(this.previousSlot);
                }
            }

            this.previousSlot = -1;
            this.swappedInventorySlot = -1;
        }
    }

    private int findHotbarExpSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.EXPERIENCE_BOTTLE) {
                return slot;
            }
        }

        return -1;
    }

    private int findInventoryExpSlot() {
        for (int slot = 9; slot <= 35; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.EXPERIENCE_BOTTLE) {
                return slot;
            }
        }

        return -1;
    }

    private boolean hasDamagedMendingGear() {
        for (EquipmentSlot armorSlot : AttributeModifierSlot.ARMOR) {
            if (needsMending(mc.player.getEquippedStack(armorSlot))) {
                return true;
            }
        }

        return needsMending(mc.player.getOffHandStack());
    }

    private static boolean needsMending(ItemStack stack) {
        if (!stack.isEmpty() && stack.isDamaged()) {
            for (Entry entry : stack.getEnchantments().getEnchantmentEntries()) {
                if (((RegistryEntry)entry.getKey()).matchesKey(Enchantments.MENDING)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isHoldingExpBottle() {
        return mc.player != null && mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE;
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        if (this.previousSlot != -1 && mc.player != null && mc.world != null && mc.currentScreen == null) {
            this.restorePreviousSlot();
        }

        this.previousSlot = -1;
        this.swappedInventorySlot = -1;
    }
}

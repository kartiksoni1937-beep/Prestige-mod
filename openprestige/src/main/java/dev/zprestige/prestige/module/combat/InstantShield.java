package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import java.util.Set;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public class InstantShield extends Module {
    public InstantShield() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private KeybindSetting swapKey;
    private BoolSetting fromInventory;
    private boolean swapKeyWasDown = false;
    private int previousSlot = -1;
    private int shieldSlot = -1;
    private int shieldInventorySlot = -1;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            if (!mc.player.isAlive()) {
                this.resetSlots();
                this.restoreUseKeyState();
            } else {
                if (this.shieldSlot != -1
                    && mc.player.getInventory().getStack(this.shieldSlot).getItem() != Items.SHIELD
                    && (Prestige.inventorySwapQueue == null || !Prestige.inventorySwapQueue.hasPendingSwaps())) {
                    this.resetSlots();
                }

                boolean swapKeyDown = this.swapKey.getValue() != -1 && this.swapKey.isPressed();
                boolean swapKeyPressed = swapKeyDown && !this.swapKeyWasDown;
                boolean swapKeyReleased = !swapKeyDown && this.swapKeyWasDown;
                this.swapKeyWasDown = swapKeyDown;
                if (mc.currentScreen == null) {
                    if (this.swapKey.isHoldMode()) {
                        if (swapKeyPressed && this.previousSlot == -1) {
                            this.swapToShield();
                        } else if (swapKeyReleased && this.previousSlot != -1) {
                            this.restorePreviousSlot();
                        }
                    } else if (swapKeyPressed) {
                        if (this.previousSlot != -1) {
                            this.restorePreviousSlot();
                        } else {
                            this.swapToShield();
                        }
                    }
                }

                if (mc.currentScreen == null && this.shieldInventorySlot != -1 && this.shieldSlot != -1 && InventoryUtil.getSelectedSlot() != this.shieldSlot) {
                    InventoryUtil.swapSlot(this.shieldInventorySlot, this.shieldSlot);
                    this.resetSlots();
                }

                if (mc.currentScreen == null && this.canBlockWithShield()) {
                    mc.options.useKey.setPressed(true);
                } else {
                    this.restoreUseKeyState();
                }
            }
        }
    }

    private void swapToShield() {
        if (mc.player.getMainHandStack().getItem() != Items.SHIELD) {
            for (int slot = 0; slot < 9; slot++) {
                if (mc.player.getInventory().getStack(slot).getItem() == Items.SHIELD) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    this.shieldSlot = slot;
                    this.shieldInventorySlot = -1;
                    InventoryUtil.selectSlot(slot);
                    return;
                }
            }

            if (this.fromInventory.getValue()) {
                for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
                    if (mc.player.getInventory().getStack(inventorySlot).getItem() == Items.SHIELD) {
                        int selectedSlot = InventoryUtil.getSelectedSlot();
                        this.previousSlot = selectedSlot;
                        this.shieldSlot = selectedSlot;
                        this.shieldInventorySlot = inventorySlot;
                        InventoryUtil.swapSlot(inventorySlot, selectedSlot);
                        return;
                    }
                }
            }
        }
    }

    private void restorePreviousSlot() {
        if (this.shieldInventorySlot != -1) {
            InventoryUtil.swapSlot(this.shieldInventorySlot, this.shieldSlot);
        } else {
            InventoryUtil.selectSlot(this.previousSlot);
        }

        this.resetSlots();
    }

    private void resetSlots() {
        this.previousSlot = -1;
        this.shieldSlot = -1;
        this.shieldInventorySlot = -1;
    }

    private boolean canBlockWithShield() {
        return mc.player.getMainHandStack().getItem() == Items.SHIELD
            ? true
            : mc.player.getOffHandStack().getItem() == Items.SHIELD && !PlayerInteractions.isHoldingUsableItem();
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

    @Override
    public void onDisable() {
        this.restoreUseKeyState();
        this.resetSlots();
        this.swapKeyWasDown = false;
    }
}

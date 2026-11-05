package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import java.util.Set;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public class InstantCrossbow extends Module {
    public InstantCrossbow() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting fromInventory;
    private int previousSlot = -1;
    private int crossbowSlot = -1;
    private int crossbowInventorySlot = -1;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC, Gamemode.Crystal);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        if (mc.player != null && mc.world != null) {
            this.swapToCrossbow();
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            if (!mc.player.isAlive()) {
                this.resetSlots();
                this.restoreUseKeyState();
            } else {
                boolean swapsPending = Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps();
                if (this.crossbowSlot != -1 && !swapsPending && mc.player.getInventory().getStack(this.crossbowSlot).getItem() != Items.CROSSBOW) {
                    this.resetSlots();
                }

                if (mc.currentScreen != null) {
                    this.restoreUseKeyState();
                } else {
                    ItemStack mainHandStack = mc.player.getMainHandStack();
                    if (mainHandStack.getItem() != Items.CROSSBOW && swapsPending && this.crossbowInventorySlot != -1) {
                        this.restoreUseKeyState();
                    } else if (mainHandStack.getItem() != Items.CROSSBOW) {
                        this.restoreUseKeyState();
                        if (this.crossbowInventorySlot != -1 && this.crossbowSlot != -1) {
                            InventoryUtil.swapSlot(this.crossbowInventorySlot, this.crossbowSlot);
                        }

                        this.resetSlots();
                        if (this.isEnabled()) {
                            this.toggle();
                        }
                    } else if (CrossbowItem.isCharged(mainHandStack)) {
                        this.restoreUseKeyState();
                    } else {
                        int pullTime = Math.max(1, CrossbowItem.getPullTime(mainHandStack, mc.player));
                        if (mc.player.isUsingItem() && mc.player.getItemUseTime() >= pullTime) {
                            this.restoreUseKeyState();
                        } else {
                            mc.options.useKey.setPressed(true);
                        }
                    }
                }
            }
        }
    }

    private void swapToCrossbow() {
        if (mc.player.getMainHandStack().getItem() != Items.CROSSBOW) {
            for (int slot = 0; slot < 9; slot++) {
                if (mc.player.getInventory().getStack(slot).getItem() == Items.CROSSBOW) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    this.crossbowSlot = slot;
                    this.crossbowInventorySlot = -1;
                    InventoryUtil.selectSlot(slot);
                    return;
                }
            }

            if (this.fromInventory.getValue()) {
                for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
                    if (mc.player.getInventory().getStack(inventorySlot).getItem() == Items.CROSSBOW) {
                        int selectedSlot = InventoryUtil.getSelectedSlot();
                        this.previousSlot = selectedSlot;
                        this.crossbowSlot = selectedSlot;
                        this.crossbowInventorySlot = inventorySlot;
                        InventoryUtil.swapSlot(inventorySlot, selectedSlot);
                        return;
                    }
                }
            }
        }
    }

    private void restorePreviousSlot() {
        if (this.crossbowInventorySlot != -1) {
            InventoryUtil.swapSlot(this.crossbowInventorySlot, this.crossbowSlot);
        } else if (this.previousSlot != -1) {
            InventoryUtil.selectSlot(this.previousSlot);
        }

        this.resetSlots();
    }

    private void resetSlots() {
        this.previousSlot = -1;
        this.crossbowSlot = -1;
        this.crossbowInventorySlot = -1;
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
        if (mc.player != null && mc.world != null && mc.player.isAlive()) {
            this.restorePreviousSlot();
        } else {
            this.resetSlots();
        }

        this.restoreUseKeyState();
    }
}

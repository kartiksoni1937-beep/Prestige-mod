package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class Refill extends Module {
    public Refill() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private KeybindSetting refillKey;
    private IntSetting threshold;
    private IntSetting delay;
    private StopWatch refillTimer = new StopWatch();

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            StopWatch timer = this.refillTimer;
            float delayMs = this.delay.getValue().intValue();
            if (timer.hasElapsed(delayMs)) {
                if (this.isRefillAllowed()) {
                    for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                        ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
                        if (!stack.isEmpty()
                            && stack.getMaxCount() > 1
                            && stack.getCount() < stack.getMaxCount()
                            && stack.getCount() <= this.threshold.getValue()) {
                            int sourceSlot = this.findMatchingStackSlot(stack);
                            if (sourceSlot != -1) {
                                int hotbarSlotId = 36 + hotbarSlot;
                                InventoryUtil.clickSlot(sourceSlot, 0, SlotActionType.PICKUP);
                                InventoryUtil.clickSlot(hotbarSlotId, 0, SlotActionType.PICKUP);
                                InventoryUtil.clickSlot(sourceSlot, 0, SlotActionType.PICKUP);
                                this.refillTimer.reset();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRefillAllowed() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            return false;
        }

        if (this.mode.getValue().equals("Always")) {
            return mc.currentScreen instanceof InventoryScreen;
        } else {
            int keyCode = this.refillKey.getValue();
            return keyCode != -1 && GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
        }
    }

    private int findMatchingStackSlot(ItemStack hotbarStack) {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == hotbarStack.getItem()) {
                return slot;
            }
        }

        return -1;
    }
}

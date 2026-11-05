package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class ChestStealer extends Module {
    public ChestStealer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting delay;
    private BoolSetting autoClose;
    private KeybindSetting activateKey;
    private StopWatch stealTimer = new StopWatch();

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.activateKey.getValue() != -1 && GLFW.glfwGetKey(mc.getWindow().getHandle(), this.activateKey.getValue()) == 1) {
            if (this.stealTimer.hasElapsed(this.delay.getValue())) {
                if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
                    for (int slotId = 0; slotId < containerHandler.getInventory().size(); slotId++) {
                        if (containerHandler.getSlot(slotId).hasStack()) {
                            InventoryUtil.clickContainerSlot(slotId, 0, SlotActionType.QUICK_MOVE);
                            this.stealTimer.reset();
                            this.delay.reroll();
                            return;
                        }
                    }

                    if (this.isContainerEmpty(containerHandler) && this.autoClose.getValue()) {
                        mc.player.closeHandledScreen();
                    }
                }
            }
        }
    }

    private boolean isContainerEmpty(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getInventory().size() == 90 ? 54 : 27;

        for (int slotId = 0; slotId < containerSlots; slotId++) {
            if (handler.getSlot(slotId).hasStack()) {
                return false;
            }
        }

        return true;
    }
}

package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Closes a storage window once every container slot is taken, so a chest that just got filled
 * (for example by Auto Bone Block Filler) does not have to be closed by hand before opening the next one.
 *
 * <p>Only the container's own slots are counted; the player's half of the window is ignored, which means a chest
 * with free space never closes no matter how full the player inventory is. Nothing is clicked or moved here.
 */
public class AutoClose extends Module {
    public AutoClose() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private IntSetting delay;

    private final StopWatch full = new StopWatch();

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof HandledScreen<?>)) {
            this.full.reset();
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!this.isStorage(handler) || !this.isFull(handler)) {
            this.full.reset();
            return;
        }
        if (!this.full.hasElapsed(this.delay.getValue())) {
            return;
        }

        // closeHandledScreen tells the server the window is shut; just dropping the screen would leave it open.
        mc.player.closeHandledScreen();
        this.full.reset();
    }

    /** Storage windows only: chests, barrels, ender chests, shulker boxes, hoppers, dispensers and droppers. */
    private boolean isStorage(ScreenHandler handler) {
        return handler instanceof GenericContainerScreenHandler
            || handler instanceof ShulkerBoxScreenHandler
            || handler instanceof HopperScreenHandler
            || handler instanceof Generic3x3ContainerScreenHandler;
    }

    private boolean isFull(ScreenHandler handler) {
        int slots = 0;

        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                continue;
            }
            slots++;
            if (!slot.hasStack()) {
                return false;
            }
        }

        return slots > 0;
    }
}

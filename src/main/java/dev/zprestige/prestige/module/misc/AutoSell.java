package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.TextSetting;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Runs the server's sell window on a loop: sends the sell command, shift-clicks the whole player
 * inventory into the window, closes it, and starts again after the configured interval.
 */
public class AutoSell extends Module {
    private static final int IDLE = 0;
    private static final int WAITING = 1;
    private static final int SELLING = 2;

    public AutoSell() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private TextSetting command;
    private IntSetting interval;
    private IntSetting clicks;
    private IntSetting delay;
    private IntSetting openTimeout;
    private BoolSetting close;

    private final StopWatch cycle = new StopWatch();
    private final StopWatch opening = new StopWatch();
    private final StopWatch batch = new StopWatch();
    private int state;
    private boolean opened;
    private int movesThisCycle;

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            this.state = IDLE;
            return;
        }

        if (this.state == IDLE) {
            this.idle();
        } else if (this.state == WAITING) {
            this.waiting();
        } else if (this.state == SELLING) {
            this.selling();
        } else {
            this.state = IDLE;
        }
    }

    private void idle() {
        // Never fire the command while any other window is open, and wait out the cycle timer.
        if (this.isGuiOpen() || !this.cycle.hasElapsed(this.interval.getValue())) {
            return;
        }

        String value = this.command.getValue();
        if (value == null || value.isBlank()) {
            return;
        }

        String name = value.startsWith("/") ? value.substring(1) : value;
        mc.getNetworkHandler().sendChatCommand(name);
        this.opened = true;
        this.movesThisCycle = 0;
        this.state = WAITING;
        this.opening.reset();
    }

    private void waiting() {
        if (this.isGuiOpen()) {
            this.state = SELLING;
            this.batch.reset();
            return;
        }

        // The server never answered the command; try again next cycle instead of hanging here.
        if (this.opening.hasElapsed(this.openTimeout.getValue())) {
            this.finishCycle();
        }
    }

    private void selling() {
        if (!this.isGuiOpen()) {
            this.finishCycle();
            return;
        }
        if (!this.batch.hasElapsed(this.delay.getValue())) {
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        int budget = this.clicks.getValue();
        int moved = 0;

        for (Slot slot : handler.slots) {
            if (moved >= budget) {
                break;
            }
            // Only the player's own slots; the window's slots belong to the server.
            if (slot.inventory != mc.player.getInventory() || !slot.hasStack()) {
                continue;
            }
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
        }

        this.movesThisCycle += moved;

        // A window that keeps bouncing items back must not be clicked forever: give up, close and
        // wait for the next cycle. Four full inventory passes is far more than a real sell needs.
        if (moved == 0 || this.movesThisCycle >= 144) {
            if (this.close.getValue()) {
                mc.player.closeHandledScreen();
            }
            this.finishCycle();
        } else {
            this.batch.reset();
        }
    }

    /** The player's own inventory is not a sell window, so it never counts as one. */
    private boolean isGuiOpen() {
        return mc.currentScreen instanceof HandledScreen<?>
                && !(mc.currentScreen instanceof InventoryScreen)
                && mc.player.currentScreenHandler != null;
    }

    private void finishCycle() {
        this.state = IDLE;
        this.opened = false;
        this.cycle.reset();
    }

    @Override
    public void onDisable() {
        if (this.opened && this.isGuiOpen()) {
            mc.player.closeHandledScreen();
        }
        this.opened = false;
        this.state = IDLE;
    }
}

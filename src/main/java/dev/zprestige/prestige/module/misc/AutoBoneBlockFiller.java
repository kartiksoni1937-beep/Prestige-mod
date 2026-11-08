package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Moves every bone block out of the player inventory into whatever chest window is open.
 *
 * <p>The window is rescanned every frame, so it keeps pace with a server that accepts the moves
 * slowly, and it stops as soon as the inventory has no bone blocks left or the chest is full.
 */
public class AutoBoneBlockFiller extends Module {
    public AutoBoneBlockFiller() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private IntSetting clicks;
    private IntSetting delay;

    private final StopWatch batch = new StopWatch();

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest)) {
            return;
        }
        if (!this.batch.hasElapsed(this.delay.getValue())) {
            return;
        }

        int containerSlots = chest.getRows() * 9;
        int budget = this.clicks.getValue();
        int moved = 0;

        for (Slot slot : chest.slots) {
            if (moved >= budget || this.emptySlots(chest, containerSlots) == 0) {
                break;
            }
            // Only the player's own slots can be filled into the chest.
            if (slot.inventory != mc.player.getInventory() || !slot.hasStack()) {
                continue;
            }
            if (slot.getStack().getItem() != Items.BONE_BLOCK) {
                continue;
            }
            mc.interactionManager.clickSlot(chest.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
        }

        if (moved > 0) {
            this.batch.reset();
        }
    }

    private int emptySlots(GenericContainerScreenHandler handler, int containerSlots) {
        int empty = 0;

        for (int slotId = 0; slotId < containerSlots; slotId++) {
            if (!handler.getSlot(slotId).hasStack()) {
                empty++;
            }
        }

        return empty;
    }
}

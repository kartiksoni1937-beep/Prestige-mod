package dev.zprestige.prestige.render;

import dev.zprestige.prestige.inventory.InventoryUtil;

public class QueuedSlotSelect implements Runnable {
    public final int slot;
    public final SilentRotationManager rotationManager;

    public QueuedSlotSelect(SilentRotationManager owner, int hotbarSlot) {
        this.slot = hotbarSlot;
        this.rotationManager = owner;
    }

    @Override
    public void run() {
        if (MinecraftAccess.mc.player == null) {
            return;
        }

        if (this.rotationManager.actedThisTick) {
            this.rotationManager.scheduledTasks.add(this);
            return;
        }

        InventoryUtil.selectSlot(this.slot);
        this.rotationManager.actedThisTick = true;
    }
}

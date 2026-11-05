package dev.zprestige.prestige.render;

public final class QueuedSwapClick {
    public final Object owner;
    public final int slotId;
    public final int hotbarSlot;
    public final Runnable callback;

    public QueuedSwapClick(Object requester, int slot, int button, Runnable onComplete) {
        this.owner = requester;
        this.slotId = slot;
        this.hotbarSlot = button;
        this.callback = onComplete;
    }
}

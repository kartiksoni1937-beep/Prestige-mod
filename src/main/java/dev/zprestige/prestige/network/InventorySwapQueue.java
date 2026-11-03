package dev.zprestige.prestige.network;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.QueuedSwapClick;
import dev.zprestige.prestige.type.SwapQueueResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;

public class InventorySwapQueue implements MinecraftAccess {
    public static boolean queueingEnabled = true;
    private final ArrayDeque pendingSwaps;
    private final List pendingCallbacks;
    private volatile boolean serverSprinting;
    private volatile boolean serverMoving;
    private boolean wasSprinting;
    private boolean holdingInput;
    private boolean resumeSprint;
    private int waitTicks;

    public InventorySwapQueue() {
        super();
        this.pendingSwaps = new ArrayDeque();
        this.pendingCallbacks = new ArrayList();
        Prestige.eventBus.register(this);
    }

    public SwapQueueResult requestSwap(int slot, int button) {
        return this.requestSwap(null, slot, button, null);
    }

    public SwapQueueResult requestSwapThen(int slot, int button, Runnable callback) {
        return this.requestSwap(null, slot, button, callback);
    }

    public SwapQueueResult requestSwapFor(Object owner, int slot, int button) {
        return this.requestSwap(owner, slot, button, null);
    }

    public SwapQueueResult requestSwap(Object owner, int slot, int button, Runnable callback) {
        if (Prestige.selfDestructed || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return SwapQueueResult.REJECTED;
        } else if (!queueingEnabled) {
            InventoryUtil.clickSlot(slot, button, SlotActionType.SWAP);
            return SwapQueueResult.IMMEDIATE;
        } else if (mc.isOnThread() && this.isServerStationary()) {
            InventoryUtil.clickSlot(slot, button, SlotActionType.SWAP);
            return SwapQueueResult.IMMEDIATE;
        } else {
            synchronized (this) {
                this.pendingSwaps.add(new QueuedSwapClick(owner, slot, button, callback));
                this.waitTicks = 0;
            }

            return SwapQueueResult.QUEUED;
        }
    }

    public int cancelSwaps(Object owner) {
        if (owner == null) {
            return 0;
        } else {
            synchronized (this) {
                int sizeBefore = this.pendingSwaps.size();
                this.pendingSwaps.removeIf(request -> ((dev.zprestige.prestige.render.QueuedSwapClick)request).owner == owner);
                int removed = sizeBefore - this.pendingSwaps.size();
                if (removed > 0 && this.pendingSwaps.isEmpty()) {
                    this.waitTicks = 0;
                    if (this.holdingInput && this.wasSprinting) {
                        this.resumeSprint = true;
                    }
                }

                return removed;
            }
        }
    }

    // $VF: Could not create synchronized statement, marking monitor enters and exits
    // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
    public boolean hasPendingSwaps() {
        synchronized (this){} // $VF: monitorenter 

        try {
            // $VF: monitorexit
            return !this.pendingSwaps.isEmpty();
        } finally {
            // $VF: monitorexit
        }
    }

    public boolean shouldHoldInput() {
        if (!Prestige.selfDestructed && queueingEnabled) {
            boolean pending;
            synchronized (this) {
                pending = !this.pendingSwaps.isEmpty();
            }

            if (pending) {
                if (!this.holdingInput && mc.player != null) {
                    this.wasSprinting = mc.player.isSprinting();
                }

                this.holdingInput = true;
            } else if (this.holdingInput) {
                this.holdingInput = false;
                this.wasSprinting = false;
            }

            return pending;
        } else {
            return false;
        }
    }

    public boolean consumeResumeSprint() {
        if (!Prestige.selfDestructed && this.resumeSprint) {
            this.resumeSprint = false;
            return true;
        } else {
            return false;
        }
    }

    public void onPacketSent(Packet packet) {
        if (packet instanceof ClientCommandC2SPacket commandPacket) {
            if (commandPacket.getMode() == Mode.START_SPRINTING) {
                this.serverSprinting = true;
            } else if (commandPacket.getMode() == Mode.STOP_SPRINTING) {
                this.serverSprinting = false;
            }
        }

        if (packet instanceof PlayerInputC2SPacket inputPacket) {
            PlayerInput input = inputPacket.input();
            this.serverMoving = input.forward() || input.backward() || input.left() || input.right() || input.jump();
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        synchronized (this) {
            this.pendingSwaps.clear();
            this.waitTicks = 0;
        }

        this.pendingCallbacks.clear();
        this.serverSprinting = false;
        this.serverMoving = false;
        this.wasSprinting = false;
        this.holdingInput = false;
        this.resumeSprint = false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (!Prestige.selfDestructed) {
            if (!this.pendingCallbacks.isEmpty()) {
                ArrayList callbacks = new ArrayList(this.pendingCallbacks);
                this.pendingCallbacks.clear();

                for (Runnable callback : (Iterable<Runnable>)(callbacks)) {
                    try {
                        callback.run();
                    } catch (Throwable ignored) {
                    }
                }
            }

            boolean pending;
            synchronized (this) {
                pending = !this.pendingSwaps.isEmpty();
                if (!pending) {
                    this.waitTicks = 0;
                }
            }

            if (pending && mc.player != null && mc.world != null) {
                if (this.isServerStationary()) {
                    this.flushQueue();
                } else {
                    synchronized (this) {
                        this.waitTicks++;
                        if (this.waitTicks > 5) {
                            this.pendingSwaps.clear();
                            this.waitTicks = 0;
                        }
                    }
                }
            }
        }
    }

    // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
    // $VF: Could not create synchronized statement, marking monitor enters and exits
    // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
    private void flushQueue() {
        synchronized (this){} // $VF: monitorenter 
        boolean monitorHeld = false /* VF: Semaphore variable */;

        ArrayList batch;
        try {
            monitorHeld = true;
            if (this.pendingSwaps.isEmpty()) {
                // $VF: monitorexit
                return;
            }

            batch = new ArrayList(this.pendingSwaps);
            this.pendingSwaps.clear();
            this.waitTicks = 0;
            // $VF: monitorexit
            monitorHeld = false;
        } finally {
            if (monitorHeld) {
                // $VF: monitorexit
            }
        }

        for (QueuedSwapClick request : (Iterable<QueuedSwapClick>)(batch)) {
            InventoryUtil.clickSlot(request.slotId, request.hotbarSlot, SlotActionType.SWAP);
            if (request.callback != null) {
                this.pendingCallbacks.add(request.callback);
            }
        }

        if (this.wasSprinting) {
            this.resumeSprint = true;
        }
    }

    private boolean isServerStationary() {
        return !this.serverSprinting && !this.serverMoving;
    }
}

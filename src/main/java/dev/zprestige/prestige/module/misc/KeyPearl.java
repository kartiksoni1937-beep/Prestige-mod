package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class KeyPearl extends Module {
    public KeyPearl() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting delay;
    private StopWatch actionTimer = new StopWatch();
    private int previousSlot = 0;
    private boolean pearlThrown = false;
    private static final long LAST_HOTBAR_SLOT = 4112858187273601032L;

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        this.pearlThrown = false;
        this.resetDelay();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.currentScreen == null && mc.isWindowFocused()) {
            if (this.actionTimer.hasElapsed(this.delay.getValue())) {
                if (this.pearlThrown) {
                    InventoryUtil.selectSlot(this.previousSlot);
                    this.toggle();
                } else if (mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
                    int pearlSlot = this.findPearlSlot();
                    if (pearlSlot == -1) {
                        this.toggle();
                    } else {
                        this.previousSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(pearlSlot);
                        this.resetDelay();
                    }
                } else {
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    this.pearlThrown = true;
                    this.resetDelay();
                }
            }
        }
    }

    private int findPearlSlot() {
        for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.ENDER_PEARL) {
                return slot;
            }
        }

        return -1;
    }

    private void resetDelay() {
        this.delay.reroll();
        this.actionTimer.reset();
    }
}

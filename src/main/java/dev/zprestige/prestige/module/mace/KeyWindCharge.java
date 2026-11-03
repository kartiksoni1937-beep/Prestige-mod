package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class KeyWindCharge extends Module {
    public KeyWindCharge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private RangeSetting delay;
    private StopWatch delayTimer = new StopWatch();
    private int previousSlot = 0;
    private boolean thrown = false;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        this.thrown = false;
        this.restartDelay();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            switch (this.mode.getValue()) {
                case "Normal":
                    if (!this.delayTimer.hasElapsed(this.delay.getValue())) {
                        return;
                    }

                    if (this.thrown) {
                        InventoryUtil.selectSlot(this.previousSlot);
                        this.toggle();
                        return;
                    }

                    if (!isWindCharge(mc.player.getMainHandStack())) {
                        int windChargeSlot = this.findWindChargeSlot();
                        if (windChargeSlot == -1) {
                            this.toggle();
                            return;
                        }

                        this.previousSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(windChargeSlot);
                        this.restartDelay();
                        return;
                    }

                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    this.thrown = true;
                    this.restartDelay();
                    break;
                case "Silent":
                    InventoryUtil.runWithSlot(this.findWindChargeSlot(), () -> PlayerInteractions.interactItem(Hand.MAIN_HAND), true);
                    this.toggle();
            }
        }
    }

    private int findWindChargeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (isWindCharge(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private void restartDelay() {
        this.delay.reroll();
        this.delayTimer.reset();
    }

    public static boolean isWindCharge(ItemStack stack) {
        return stack.getItem().equals(Items.WIND_CHARGE);
    }
}

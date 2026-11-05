package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import java.util.List;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class AutoLoot extends Module {
    public AutoLoot() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting delay;
    private IntSetting minTotems;
    private IntSetting minPearls;
    private KeybindSetting lootKey;
    private StopWatch dropTimer = new StopWatch();

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen instanceof InventoryScreen) {
            if (!this.dropTimer.hasElapsed(this.delay.getValue())) {
                return;
            }

            if (this.lootKey.getValue() == -1) {
                return;
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), this.lootKey.getValue()) == 0) {
                return;
            }

            List totemSlots = InventoryUtil.collectInventorySlots(Items.TOTEM_OF_UNDYING, true);
            List pearlSlots = InventoryUtil.collectInventorySlots(Items.ENDER_PEARL, true);
            if (!totemSlots.isEmpty() && totemSlots.size() > this.minTotems.getValue()) {
                InventoryUtil.clickSlot((Integer)totemSlots.get(0) < 9 ? (Integer)totemSlots.get(0) + 36 : (Integer)totemSlots.get(0), 1, SlotActionType.THROW);
                this.delay.reroll();
                this.dropTimer.reset();
                return;
            }

            if (!pearlSlots.isEmpty() && pearlSlots.size() > this.minPearls.getValue()) {
                InventoryUtil.clickSlot((Integer)pearlSlots.get(0) < 9 ? (Integer)pearlSlots.get(0) + 36 : (Integer)pearlSlots.get(0), 1, SlotActionType.THROW);
                this.delay.reroll();
                this.dropTimer.reset();
                return;
            }
        }
    }
}

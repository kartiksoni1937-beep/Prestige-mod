package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import java.util.Set;
import net.minecraft.item.Items;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class CartStealer extends Module {
    public CartStealer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting delay;
    private BoolSetting autoClose;
    private KeybindSetting activateKey;
    private StopWatch stealTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (this.activateKey.getValue() == -1 || GLFW.glfwGetKey(mc.getWindow().getHandle(), this.activateKey.getValue()) == 1) {
            if (this.stealTimer.hasElapsedRandom(this.delay)) {
                if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler shulkerHandler) {
                    if (this.countEmptyPlayerSlots(shulkerHandler) <= 1) {
                        if (this.autoClose.getValue()) {
                            mc.player.closeHandledScreen();
                        }
                    } else {
                        for (int slotId = 0; slotId < 27; slotId++) {
                            Slot slot = shulkerHandler.getSlot(slotId);
                            if (slot.hasStack() && slot.getStack().isOf(Items.TNT_MINECART)) {
                                mc.interactionManager.clickSlot(shulkerHandler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                                this.stealTimer.reset();
                                this.delay.reroll();
                                return;
                            }
                        }

                        if (this.autoClose.getValue()) {
                            mc.player.closeHandledScreen();
                        }
                    }
                }
            }
        }
    }

    private int countEmptyPlayerSlots(ShulkerBoxScreenHandler handler) {
        int emptySlots = 0;

        for (int slotId = 27; slotId < 63; slotId++) {
            if (!handler.getSlot(slotId).hasStack()) {
                emptySlots++;
            }
        }

        return emptySlots;
    }
}

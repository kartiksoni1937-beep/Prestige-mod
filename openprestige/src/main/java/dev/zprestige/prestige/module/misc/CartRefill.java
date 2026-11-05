package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import java.util.Set;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

public class CartRefill extends Module {
    public CartRefill() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private KeybindSetting refillKey;
    private IntSetting targetSlot;
    private IntSetting delay;
    private StopWatch refillTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            StopWatch timer = this.refillTimer;
            float delayMs = this.delay.getValue().intValue();
            if (timer.hasElapsed(delayMs)) {
                if (this.isRefillAllowed()) {
                    int hotbarSlot = Math.max(0, Math.min(8, this.targetSlot.getValue() - 1));
                    if (mc.player.getInventory().getStack(hotbarSlot).isEmpty()) {
                        int cartSlot = this.findTntMinecartSlot();
                        if (cartSlot != -1) {
                            InventoryUtil.swapSlot(cartSlot, hotbarSlot);
                            this.refillTimer.reset();
                        }
                    }
                }
            }
        }
    }

    private boolean isRefillAllowed() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            return false;
        }

        if (this.mode.getValue().equals("Always")) {
            return mc.currentScreen instanceof InventoryScreen;
        } else {
            int keyCode = this.refillKey.getValue();
            return keyCode != -1 && GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
        }
    }

    private int findTntMinecartSlot() {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == Items.TNT_MINECART) {
                return slot;
            }
        }

        return -1;
    }
}

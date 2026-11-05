package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.AutoAttackEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;

public class BreachSwap extends Module {
    public BreachSwap() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting chance;
    private int previousSlot = -1;
    private int swapBackTicks = 0;
    private static final long LAST_HOTBAR_SLOT = 5554404204296011784L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.swapBackTicks == 0) {
            if (this.previousSlot != -1) {
                InventoryUtil.selectSlot(this.previousSlot);
                this.previousSlot = -1;
            }
        } else {
            this.swapBackTicks--;
        }
    }

    @Subscribe
    public void onAutoAttack(AutoAttackEvent event) {
        if (RandomUtil.RANDOM.nextFloat() * 100.0F > this.chance.getValue()) {
            this.swapBackTicks = 1;
        }

        if (this.swapBackTicks <= 0) {
            if (!(mc.player.fallDistance > 1.5)) {
                if (this.previousSlot == -1 && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    int maceSlot = this.findBestBreachMaceSlot();
                    if (maceSlot != -1) {
                        InventoryUtil.selectSlot(maceSlot);
                        this.swapBackTicks = 1;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (RandomUtil.RANDOM.nextFloat() * 100.0F > this.chance.getValue()) {
            this.swapBackTicks = 1;
        }

        if (this.swapBackTicks <= 0) {
            if (!(mc.player.fallDistance > 1.5)) {
                if (this.previousSlot == -1 && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    int maceSlot = this.findBestBreachMaceSlot();
                    if (maceSlot != -1) {
                        InventoryUtil.selectSlot(maceSlot);
                        this.swapBackTicks = 1;
                    }
                }
            }
        }
    }

    public int findBestBreachMaceSlot() {
        ArrayList maceSlots = new ArrayList();

        for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
            if (InventoryUtil.isMace(mc.player.getInventory().getStack(slot))) {
                maceSlots.add(slot);
            }
        }

        int bestLevel = -1;
        int bestSlot = -1;

        for (int maceSlot : (Iterable<Integer>)(maceSlots)) {
            int level = this.getBreachLevel(maceSlot);
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = maceSlot;
            }
        }

        return bestSlot;
    }

    public int getBreachLevel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack != null) {
            float level = EnchantmentHelper.getLevel(
                mc.world.getRegistryManager().getOptional(Enchantments.BREACH.getRegistryRef()).get().getEntry(Enchantments.BREACH.getValue()).get(), stack
            );
            return (int)level;
        } else {
            return 0;
        }
    }
}

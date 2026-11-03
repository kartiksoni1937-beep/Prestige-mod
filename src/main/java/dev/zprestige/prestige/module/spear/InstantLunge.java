package dev.zprestige.prestige.module.spear;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.network.PacketSender;
import dev.zprestige.prestige.render.StopWatch;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class InstantLunge extends Module {
    public InstantLunge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting swapBack;
    private RangeSetting swapBackDelay;
    private int previousSlot = -1;
    private StopWatch swapBackTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Spear);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        int lungeSpearSlot = this.findLungeSpearSlot();
        if (this.previousSlot != -1) {
            if (this.swapBackTimer.hasElapsedRandom(this.swapBackDelay)) {
                InventoryUtil.selectSlot(this.previousSlot);
                this.previousSlot = -1;
                this.toggle();
            }
        } else if (lungeSpearSlot != -1) {
            if (!mc.player.isUsingItem()) {
                if (mc.player.getHungerManager().getFoodLevel() >= 6) {
                    if (InventoryUtil.getSelectedSlot() == lungeSpearSlot) {
                        PacketSender.sendPacket(new PlayerActionC2SPacket(Action.STAB, BlockPos.ORIGIN, Direction.DOWN));
                        this.toggle();
                    } else {
                        this.previousSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(lungeSpearSlot);
                        PiercingWeaponComponent piercingWeapon = mc.player.getMainHandStack().get(DataComponentTypes.PIERCING_WEAPON);
                        if (piercingWeapon != null && !mc.interactionManager.isFlyingLocked()) {
                            mc.interactionManager.attackWithPiercingWeapon(piercingWeapon);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }

                        if (!this.swapBack.getValue()) {
                            this.previousSlot = -1;
                            this.toggle();
                        } else {
                            this.swapBackTimer.reset();
                            this.swapBackDelay.reroll();
                        }
                    }
                }
            }
        }
    }

    private int findLungeSpearSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isIn(ItemTags.SPEARS)) {
                if (getEnchantmentLevel(stack, Enchantments.LUNGE) > 0) {
                    return slot;
                }
            }
        }

        return -1;
    }

    public static int getEnchantmentLevel(ItemStack stack, RegistryKey enchantmentKey) {
        if (stack.isEmpty()) {
            return 0;
        } else {
            Object2IntArrayMap enchantmentLevels = new Object2IntArrayMap();
            collectEnchantments(stack, enchantmentLevels);
            return findEnchantmentLevel(enchantmentLevels, enchantmentKey);
        }
    }

    public static int findEnchantmentLevel(Object2IntMap enchantmentLevels, RegistryKey enchantmentKey) {
        for (Entry entry : (Iterable<Entry>)(Object2IntMaps.fastIterable(enchantmentLevels))) {
            if (((RegistryEntry)entry.getKey()).matchesKey(enchantmentKey)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    public static void collectEnchantments(ItemStack stack, Object2IntMap enchantmentLevels) {
        enchantmentLevels.clear();
        if (!stack.isEmpty()) {
            for (Entry entry : stack.getItem() == Items.ENCHANTED_BOOK
                ? stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentEntries()
                : stack.getEnchantments().getEnchantmentEntries()) {
                enchantmentLevels.put((RegistryEntry)entry.getKey(), entry.getIntValue());
            }
        }
    }
}

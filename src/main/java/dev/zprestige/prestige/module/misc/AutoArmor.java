package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.EquipmentSlotSwitchMap;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class AutoArmor extends Module {
    public AutoArmor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private KeybindSetting swapKey;
    private BoolSetting avoidCursed;
    private IntSetting delay;
    private StopWatch swapTimer = new StopWatch();
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.world != null) {
            StopWatch timer = this.swapTimer;
            float delayMs = this.delay.getValue().intValue();
            if (timer.hasElapsed(delayMs)) {
                if (this.isSwapAllowed()) {
                    for (EquipmentSlot slot : ARMOR_SLOTS) {
                        if (this.tryEquipBetterArmor(slot)) {
                            this.swapTimer.reset();
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean tryEquipBetterArmor(EquipmentSlot slot) {
        ItemStack equipped = mc.player.getEquippedStack(slot);
        if (slot == EquipmentSlot.CHEST && !equipped.isEmpty() && equipped.getItem() == Items.ELYTRA) {
            return false;
        } else if (!equipped.isEmpty() && hasEnchantment(equipped, Enchantments.BINDING_CURSE)) {
            return false;
        } else {
            int equippedScore = equipped.isEmpty() ? -1 : getArmorScore(equipped);
            int bestSlotId = -1;
            int bestScore = equippedScore;

            for (int invIndex = 0; invIndex < 36; invIndex++) {
                ItemStack candidate = mc.player.getInventory().getStack(invIndex);
                if (!candidate.isEmpty()
                    && getArmorEquipmentSlot(candidate.getItem()) == slot
                    && (
                        !this.avoidCursed.getValue()
                            || !hasEnchantment(candidate, Enchantments.BINDING_CURSE) && !hasEnchantment(candidate, Enchantments.VANISHING_CURSE)
                    )) {
                    int candidateScore = getArmorScore(candidate);
                    if (candidateScore > bestScore) {
                        bestScore = candidateScore;
                        bestSlotId = invIndex < 9 ? 36 + invIndex : invIndex;
                    }
                }
            }

            if (bestSlotId == -1) {
                return false;
            } else {
                int armorSlotId = getArmorSlotId(slot);
                InventoryUtil.clickSlot(bestSlotId, 0, SlotActionType.PICKUP);
                InventoryUtil.clickSlot(armorSlotId, 0, SlotActionType.PICKUP);
                InventoryUtil.clickSlot(bestSlotId, 0, SlotActionType.PICKUP);
                return true;
            }
        }
    }

    private boolean isSwapAllowed() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            return false;
        }

        return switch (this.mode.getValue()) {
            case "Inventory" -> mc.currentScreen instanceof InventoryScreen;
            case "Keybind" -> {
                int keyCode = this.swapKey.getValue();
                yield keyCode != -1 && GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
            }
            default -> true;
        };
    }

    private static int getArmorSlotId(EquipmentSlot slot) {
        return switch (EquipmentSlotSwitchMap.SWITCH_MAP[slot.ordinal()]) {
            case 1 -> 5;
            case 2 -> 6;
            case 3 -> 7;
            case 4 -> 8;
            default -> -1;
        };
    }

    private static EquipmentSlot getArmorEquipmentSlot(Item item) {
        if (item == Items.NETHERITE_HELMET
            || item == Items.DIAMOND_HELMET
            || item == Items.IRON_HELMET
            || item == Items.GOLDEN_HELMET
            || item == Items.CHAINMAIL_HELMET
            || item == Items.LEATHER_HELMET
            || item == Items.TURTLE_HELMET) {
            return EquipmentSlot.HEAD;
        } else if (item == Items.NETHERITE_CHESTPLATE
            || item == Items.DIAMOND_CHESTPLATE
            || item == Items.IRON_CHESTPLATE
            || item == Items.GOLDEN_CHESTPLATE
            || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.LEATHER_CHESTPLATE) {
            return EquipmentSlot.CHEST;
        } else if (item == Items.NETHERITE_LEGGINGS
            || item == Items.DIAMOND_LEGGINGS
            || item == Items.IRON_LEGGINGS
            || item == Items.GOLDEN_LEGGINGS
            || item == Items.CHAINMAIL_LEGGINGS
            || item == Items.LEATHER_LEGGINGS) {
            return EquipmentSlot.LEGS;
        } else {
            return item != Items.NETHERITE_BOOTS
                    && item != Items.DIAMOND_BOOTS
                    && item != Items.IRON_BOOTS
                    && item != Items.GOLDEN_BOOTS
                    && item != Items.CHAINMAIL_BOOTS
                    && item != Items.LEATHER_BOOTS
                ? null
                : EquipmentSlot.FEET;
        }
    }

    private static int getArmorScore(ItemStack stack) {
        int score = getMaterialScore(stack.getItem()) * 100;
        score += getEnchantmentLevel(stack, Enchantments.PROTECTION) * 5;
        score += getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * 3;
        score += getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION) * 3;
        score += getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION) * 2;
        score += getEnchantmentLevel(stack, Enchantments.THORNS) * 2;
        score += getEnchantmentLevel(stack, Enchantments.MENDING) * 3;
        score += getEnchantmentLevel(stack, Enchantments.UNBREAKING) * 1;
        score += getEnchantmentLevel(stack, Enchantments.FEATHER_FALLING) * 3;
        score += getEnchantmentLevel(stack, Enchantments.DEPTH_STRIDER) * 1;
        score += getEnchantmentLevel(stack, Enchantments.SOUL_SPEED) * 1;
        score += getEnchantmentLevel(stack, Enchantments.AQUA_AFFINITY) * 1;
        score += getEnchantmentLevel(stack, Enchantments.RESPIRATION) * 1;
        return score + getEnchantmentLevel(stack, Enchantments.SWIFT_SNEAK) * 2;
    }

    private static int getMaterialScore(Item item) {
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) {
            return 60;
        } else if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
            return 50;
        } else if (item == Items.IRON_HELMET
            || item == Items.IRON_CHESTPLATE
            || item == Items.IRON_LEGGINGS
            || item == Items.IRON_BOOTS
            || item == Items.TURTLE_HELMET) {
            return 30;
        } else if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS) {
            return 20;
        } else if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) {
            return 15;
        } else {
            return item != Items.LEATHER_HELMET && item != Items.LEATHER_CHESTPLATE && item != Items.LEATHER_LEGGINGS && item != Items.LEATHER_BOOTS ? 0 : 10;
        }
    }

    private static boolean hasEnchantment(ItemStack stack, RegistryKey enchantment) {
        return getEnchantmentLevel(stack, enchantment) > 0;
    }

    private static int getEnchantmentLevel(ItemStack stack, RegistryKey enchantment) {
        if (stack.isEmpty()) {
            return 0;
        } else {
            for (Entry entry : stack.getEnchantments().getEnchantmentEntries()) {
                if (((RegistryEntry)entry.getKey()).matchesKey(enchantment)) {
                    return entry.getIntValue();
                }
            }

            return 0;
        }
    }
}

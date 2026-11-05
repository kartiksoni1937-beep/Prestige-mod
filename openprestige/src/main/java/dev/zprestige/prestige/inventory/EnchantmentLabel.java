package dev.zprestige.prestige.inventory;

import net.minecraft.item.ItemStack;

public class EnchantmentLabel {
    public ItemStack stack;
    public String text;

    public EnchantmentLabel(ItemStack itemStack, String labelText) {
        this.stack = itemStack;
        this.text = labelText;
    }
}

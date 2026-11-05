package dev.zprestige.prestige.inventory;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;

public class HeldItemRendererAccessor {
    private final HeldItemRenderer renderer;

    public HeldItemRendererAccessor(HeldItemRenderer heldItemRenderer) {
        this.renderer = heldItemRenderer;
    }

    public void setMainHandEquipProgress(float progress) {
        FieldAccessor.bindMapped(this.renderer, "equipProgressMainHand", HeldItemRenderer.class, float.class).set(progress);
    }

    public void setOffHandEquipProgress(float progress) {
        FieldAccessor.bindMapped(this.renderer, "equipProgressOffHand", HeldItemRenderer.class, float.class).set(progress);
    }

    public float getMainHandEquipProgress() {
        return (Float)FieldAccessor.bindMapped(this.renderer, "equipProgressMainHand", HeldItemRenderer.class, float.class).get();
    }

    public float getOffHandEquipProgress() {
        return (Float)FieldAccessor.bindMapped(this.renderer, "equipProgressOffHand", HeldItemRenderer.class, float.class).get();
    }

    public void setMainHandStack(ItemStack stack) {
        FieldAccessor.bindMapped(this.renderer, "mainHand", HeldItemRenderer.class, ItemStack.class).set(stack);
    }

    public void setOffHandStack(ItemStack stack) {
        FieldAccessor.bindMapped(this.renderer, "offHand", HeldItemRenderer.class, ItemStack.class).set(stack);
    }
}

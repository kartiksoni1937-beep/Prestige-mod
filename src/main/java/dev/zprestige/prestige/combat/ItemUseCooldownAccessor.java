package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.MinecraftClient;

public class ItemUseCooldownAccessor {
    private final MinecraftClient client;

    public ItemUseCooldownAccessor(MinecraftClient minecraftClient) {
        this.client = minecraftClient;
    }

    public int getItemUseCooldown() {
        return (Integer)FieldAccessor.bindMapped(this.client, "itemUseCooldown", MinecraftClient.class, int.class).get();
    }

    public void setItemUseCooldown(int ticks) {
        FieldAccessor.bindMapped(this.client, "itemUseCooldown", MinecraftClient.class, int.class).set(ticks);
    }
}

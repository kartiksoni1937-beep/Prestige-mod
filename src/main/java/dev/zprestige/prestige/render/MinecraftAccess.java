package dev.zprestige.prestige.render;

import net.minecraft.client.MinecraftClient;

public interface MinecraftAccess {
    MinecraftClient mc = MinecraftClient.getInstance();
}

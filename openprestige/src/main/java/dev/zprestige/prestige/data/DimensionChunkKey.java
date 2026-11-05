package dev.zprestige.prestige.data;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public record DimensionChunkKey(RegistryKey<World> dimension, long chunkPos) {
}

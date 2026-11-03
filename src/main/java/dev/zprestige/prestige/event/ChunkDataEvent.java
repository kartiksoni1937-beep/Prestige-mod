package dev.zprestige.prestige.event;

import net.minecraft.world.chunk.WorldChunk;

public class ChunkDataEvent extends Event {
    private final WorldChunk chunk;

    public ChunkDataEvent(WorldChunk loadedChunk) {
        this.chunk = loadedChunk;
    }

    public WorldChunk getChunk() {
        return this.chunk;
    }
}

package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.world.ClientChunkManager.ClientChunkMap;

public class ClientChunkMapAccessor {
    private final ClientChunkMap chunkMap;
    private static final String CHUNKS_FIELD = "chunks";

    public ClientChunkMapAccessor(ClientChunkMap map) {
        this.chunkMap = map;
    }

    public AtomicReferenceArray getChunks() {
        return (AtomicReferenceArray)FieldAccessor.bindMapped(this.chunkMap, CHUNKS_FIELD, ClientChunkMap.class, AtomicReferenceArray.class).get();
    }
}

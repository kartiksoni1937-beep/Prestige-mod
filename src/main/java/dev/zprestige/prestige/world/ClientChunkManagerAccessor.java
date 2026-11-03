package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientChunkManager.ClientChunkMap;

public class ClientChunkManagerAccessor {
    private final ClientChunkManager chunkManager;
    private static final String CHUNKS_FIELD = "chunks";

    public ClientChunkManagerAccessor(ClientChunkManager manager) {
        this.chunkManager = manager;
    }

    public ClientChunkMap getChunkMap() {
        return (ClientChunkMap)FieldAccessor.bindMapped(this.chunkManager, CHUNKS_FIELD, ClientChunkManager.class, ClientChunkMap.class).get();
    }
}

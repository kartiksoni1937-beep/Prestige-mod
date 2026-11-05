package dev.zprestige.prestige.world;

import dev.zprestige.prestige.render.MinecraftAccess;
import java.util.Iterator;
import net.minecraft.world.chunk.Chunk;

public class LoadedChunkIterator implements Iterator, MinecraftAccess {
    private final ClientChunkMapAccessor chunkMapAccessor;
    private final boolean requireLoadedNeighbours;
    private int nextIndex;
    private Chunk pendingChunk;

    public LoadedChunkIterator(boolean onlyWithLoadedNeighbours) {
        super();
        this.chunkMapAccessor = new ClientChunkMapAccessor(new ClientChunkManagerAccessor(mc.world.getChunkManager()).getChunkMap());
        this.nextIndex = 0;
        this.requireLoadedNeighbours = onlyWithLoadedNeighbours;
        this.advance();
    }

    private Chunk advance() {
        Chunk current = this.pendingChunk;
        this.pendingChunk = null;

        while (this.nextIndex < this.chunkMapAccessor.getChunks().length()) {
            this.pendingChunk = (Chunk)this.chunkMapAccessor.getChunks().get(this.nextIndex++);
            if (this.pendingChunk != null && (!this.requireLoadedNeighbours || this.hasLoadedNeighbours(this.pendingChunk))) {
                break;
            }
        }

        return current;
    }

    private boolean hasLoadedNeighbours(Chunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        return mc.world.getChunkManager().isChunkLoaded(chunkX + 1, chunkZ)
            && mc.world.getChunkManager().isChunkLoaded(chunkX - 1, chunkZ)
            && mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ + 1)
            && mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ - 1);
    }

    @Override
    public boolean hasNext() {
        return this.pendingChunk != null;
    }

    public Chunk nextChunk() {
        return this.advance();
    }

    @Override
    public Object next() {
        return this.nextChunk();
    }
}

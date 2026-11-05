package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.TextSetting;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.DimensionChunkKey;
import dev.zprestige.prestige.event.BlockStateUpdateEvent;
import dev.zprestige.prestige.event.ChunkDataEvent;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.OreDefinition;
import dev.zprestige.prestige.world.LoadedChunkIterator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.ChunkRandom.RandomProvider;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.OreFeatureConfig.Target;

public class OreSimulation extends Module {
    public OreSimulation() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private MultiSetting ores;
    private TextSetting seed;
    private IntSetting chunks;
    public Map chunkOres = new ConcurrentHashMap();
    private long parsedSeed;
    private boolean seedLoaded;
    private Map oresByBiome;
    private RegistryKey cachedDimension;
    private ClientWorld cachedWorld;
    private Frustum frustum;
    private long lastPrune;

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player != null && this.seedLoaded && this.ensureWorldReady()) {
            int centerChunkX = mc.player.getChunkPos().x;
            int centerChunkZ = mc.player.getChunkPos().z;
            int radius = this.chunks.getValue();
            this.pruneUnloadedChunks();

            for (int ring = 0; ring <= radius; ring++) {
                for (int chunkX = -ring + centerChunkX; chunkX <= ring + centerChunkX; chunkX++) {
                    this.renderChunk(event, chunkX, centerChunkZ + ring - radius);
                }

                for (int mirroredChunkX = -ring + 1 + centerChunkX; mirroredChunkX < ring + centerChunkX; mirroredChunkX++) {
                    this.renderChunk(event, mirroredChunkX, centerChunkZ - ring + radius + 1);
                }
            }
        }
    }

    // Drops simulated ores of chunks that are no longer loaded so the cache cannot grow without bound.
    private void pruneUnloadedChunks() {
        long now = System.currentTimeMillis();
        ClientWorld world = mc.world;
        if (world == null || now - this.lastPrune < 5000L) {
            return;
        }

        this.lastPrune = now;
        RegistryKey dimension = world.getRegistryKey();
        this.chunkOres.keySet().removeIf(key -> {
            DimensionChunkKey chunkKey = (DimensionChunkKey)key;
            return !chunkKey.dimension().equals(dimension)
                || world.getChunk(ChunkPos.getPackedX(chunkKey.chunkPos()), ChunkPos.getPackedZ(chunkKey.chunkPos()), ChunkStatus.FULL, false) == null;
        });
    }

    private void renderChunk(Render3DEvent event, int chunkX, int chunkZ) {
        if (this.frustum == null || this.frustum.isVisible(new Box(chunkX << 4, -64.0, chunkZ << 4, (chunkX << 4) + 16, 320.0, (chunkZ << 4) + 16))) {
            ClientWorld world = mc.world;
            if (world != null) {
                DimensionChunkKey chunkKey = new DimensionChunkKey(world.getRegistryKey(), ChunkPos.toLong(chunkX, chunkZ));
                if (this.chunkOres.containsKey(chunkKey)) {
                    Map oreMap = (Map)this.chunkOres.get(chunkKey);

                    for (Entry entry : (Iterable<Entry>)(oreMap.entrySet())) {
                        if (this.ores.isSelected(((OreDefinition)entry.getKey()).name)) {
                            for (Vec3d pos : (Iterable<Vec3d>)((Set)entry.getValue())) {
                                ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)pos.x, (float)pos.y, (float)pos.z, (float)(pos.x + 1.0), (float)(pos.y + 1.0), (float)(pos.z + 1.0), ((OreDefinition)entry.getKey()).color);
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onFrustumSetup(FrustumSetupEvent event) {
        this.frustum = event.getFrustum();
    }

    @Subscribe
    public void onBlockStateUpdate(BlockStateUpdateEvent event) {
        if (!event.getState().isOpaque()) {
            if (mc.world != null) {
                DimensionChunkKey chunkKey = new DimensionChunkKey(mc.world.getRegistryKey(), ChunkPos.toLong(event.getPos()));
                if (this.chunkOres.containsKey(chunkKey)) {
                    Vec3d blockVec = Vec3d.of(event.getPos());

                    for (Set positions : (Iterable<Set>)(((Map)this.chunkOres.get(chunkKey)).values())) {
                        positions.remove(blockVec);
                    }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        if (this.seed.getValue().isEmpty()) {
            mc.player.sendMessage(Text.of("Please set a seed"), false);
            this.toggle();
        } else {
            this.loadSeed();
        }
    }

    @Override
    public void onDisable() {
        this.chunkOres.clear();
        this.oresByBiome = null;
        this.cachedDimension = null;
        this.cachedWorld = null;
        this.seedLoaded = false;
    }

    private void simulateLoadedChunks() {
        if (mc.player != null) {
            for (Chunk chunk : (Iterable<Chunk>)(getLoadedChunks(false))) {
                this.simulateChunk(chunk);
            }
        }
    }

    public static Iterable getLoadedChunks(boolean requireLoadedNeighbours) {
        return () -> new LoadedChunkIterator(requireLoadedNeighbours);
    }

    private void loadSeed() {
        long parsed;
        try {
            parsed = Long.parseLong(this.seed.getValue().toString());
        } catch (NumberFormatException exception) {
            this.seedLoaded = false;
            this.chunkOres.clear();
            this.oresByBiome = null;
            this.cachedDimension = null;
            this.cachedWorld = null;
            Prestige.notificationManager.addNotification("Ore Simulation", "Invalid seed!", 6000L, NotificationType.ERROR);
            return;
        }

        this.parsedSeed = parsed;
        this.seedLoaded = true;
        this.cachedDimension = null;
        this.cachedWorld = null;
        this.ensureWorldReady();
    }

    private boolean ensureWorldReady() {
        ClientWorld world = mc.world;
        if (this.seedLoaded && world != null) {
            RegistryKey dimension = world.getRegistryKey();
            if (world == this.cachedWorld && dimension.equals(this.cachedDimension) && this.oresByBiome != null) {
                return true;
            } else {
                Map biomeOres = OreDefinition.getOresByBiome(dimension.getValue().getPath());
                if (biomeOres != null && !biomeOres.isEmpty()) {
                    this.oresByBiome = biomeOres;
                    this.cachedDimension = dimension;
                    this.cachedWorld = world;
                    this.chunkOres.clear();
                    this.simulateLoadedChunks();
                    return true;
                } else {
                    Prestige.notificationManager.addNotification("Ore Simulation", "No ores registered for this dimension", 6000L, NotificationType.ERROR);
                    this.toggle();
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Subscribe
    public void onChunkData(ChunkDataEvent event) {
        if (this.ensureWorldReady()) {
            this.simulateChunk(event.getChunk());
        }
    }

    private void simulateChunk(Chunk chunk) {
        ClientWorld world = mc.world;
        if (world != null && world == this.cachedWorld && this.seedLoaded && this.oresByBiome != null && world.getRegistryKey().equals(this.cachedDimension)) {
            ChunkPos chunkPos = chunk.getPos();
            DimensionChunkKey chunkKey = new DimensionChunkKey(world.getRegistryKey(), chunkPos.toLong());
            if (!this.chunkOres.containsKey(chunkKey)) {
                HashSet biomeKeys = new HashSet();
                ChunkPos.stream(chunkPos, 1).forEach(neighborPos -> {
                    Chunk neighborChunk = world.getChunk(neighborPos.x, neighborPos.z, ChunkStatus.BIOMES, false);
                    if (neighborChunk != null) {
                        for (ChunkSection section : neighborChunk.getSectionArray()) {
                            section.getBiomeContainer().forEachValue(biomeEntry -> biomeKeys.add(((net.minecraft.registry.entry.RegistryEntry)biomeEntry).getKey().get()));
                        }
                    }
                });
                Set candidateOres = (Set)biomeKeys.stream()
                    .flatMap(biomeKey -> this.getOresForBiome(((net.minecraft.registry.RegistryKey)biomeKey)).stream())
                    .collect(Collectors.toSet());
                int chunkBlockX = chunkPos.x << 4;
                int chunkBlockZ = chunkPos.z << 4;
                ChunkRandom random = new ChunkRandom(RandomProvider.XOROSHIRO.create(0L));
                long populationSeed = random.setPopulationSeed(this.parsedSeed, chunkBlockX, chunkBlockZ);
                HashMap oresInChunk = new HashMap();

                for (OreDefinition ore : (Iterable<OreDefinition>)(candidateOres)) {
                    HashSet positions = new HashSet();
                    random.setDecoratorSeed(populationSeed, ore.index, ore.step);
                    int count = ore.count.get(random);

                    for (int attempt = 0; attempt < count; attempt++) {
                        if (ore.rarity == 1.0F || !(random.nextFloat() >= 1.0F / ore.rarity)) {
                            int oreX = random.nextInt(16) + chunkBlockX;
                            int oreZ = random.nextInt(16) + chunkBlockZ;
                            int oreY = ore.heightProvider.get(random, ore.heightContext);
                            BlockPos origin = new BlockPos(oreX, oreY, oreZ);
                            RegistryKey originBiome = chunk.getBiomeForNoiseGen(
                                    BiomeCoords.fromBlock(oreX), BiomeCoords.fromBlock(oreY), BiomeCoords.fromBlock(oreZ)
                                )
                                .getKey()
                                .get();
                            if (this.getOresForBiome(originBiome).contains(ore)) {
                                if (ore.scattered) {
                                    positions.addAll(this.generateScatteredOre(world, random, origin, ore));
                                } else {
                                    positions.addAll(this.generateVein(world, random, origin, ore));
                                }
                            }
                        }
                    }

                    if (!positions.isEmpty()) {
                        oresInChunk.put(ore, positions);
                    }
                }

                this.chunkOres.put(chunkKey, oresInChunk);
            }
        }
    }

    private List getOresForBiome(RegistryKey biome) {
        return this.oresByBiome == null ? Collections.emptyList() : (List)this.oresByBiome.getOrDefault(biome, Collections.emptyList());
    }

    private ArrayList generateVein(ClientWorld world, ChunkRandom random, BlockPos origin, OreDefinition ore) {
        int veinSize = ore.size;
        float angle = random.nextFloat() * (float) Math.PI;
        float halfLength = veinSize / 8.0F;
        int maxRadius = MathHelper.ceil((veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double startX = origin.getX() + Math.sin(angle) * halfLength;
        double endX = origin.getX() - Math.sin(angle) * halfLength;
        double startZ = origin.getZ() + Math.cos(angle) * halfLength;
        double endZ = origin.getZ() - Math.cos(angle) * halfLength;
        double startY = origin.getY() + random.nextInt(3) - 2;
        double endY = origin.getY() + random.nextInt(3) - 2;
        int minX = origin.getX() - MathHelper.ceil(halfLength) - maxRadius;
        int minY = origin.getY() - 2 - maxRadius;
        int minZ = origin.getZ() - MathHelper.ceil(halfLength) - maxRadius;
        int horizontalSize = 2 * (MathHelper.ceil(halfLength) + maxRadius);
        int verticalSize = 2 * (2 + maxRadius);

        for (int columnX = minX; columnX <= minX + horizontalSize; columnX++) {
            for (int columnZ = minZ; columnZ <= minZ + horizontalSize; columnZ++) {
                if (minY <= world.getTopY(Type.MOTION_BLOCKING, columnX, columnZ)) {
                    return this.generateVeinPart(world, random, veinSize, startX, endX, startZ, endZ, startY, endY, minX, minY, minZ, horizontalSize, verticalSize, ore);
                }
            }
        }

        return new ArrayList();
    }

    private ArrayList generateVeinPart(ClientWorld world, ChunkRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int minX, int minY, int minZ, int horizontalSize, int verticalSize, OreDefinition ore) {
        BitSet visited = new BitSet(horizontalSize * verticalSize * horizontalSize);
        Mutable mutablePos = new Mutable();
        double[] spheres = new double[veinSize * 4];
        ArrayList positions = new ArrayList();

        for (int sphereIndex = 0; sphereIndex < veinSize; sphereIndex++) {
            float progress = (float)sphereIndex / veinSize;
            double sphereX = MathHelper.lerp((double)progress, startX, endX);
            double sphereY = MathHelper.lerp((double)progress, startY, endY);
            double sphereZ = MathHelper.lerp((double)progress, startZ, endZ);
            double randomScale = random.nextDouble() * veinSize / 16.0;
            double sphereRadius = ((MathHelper.sin((float) Math.PI * progress) + 1.0F) * randomScale + 1.0) / 2.0;
            spheres[sphereIndex * 4] = sphereX;
            spheres[sphereIndex * 4 + 1] = sphereY;
            spheres[sphereIndex * 4 + 2] = sphereZ;
            spheres[sphereIndex * 4 + 3] = sphereRadius;
        }

        for (int firstIndex = 0; firstIndex < veinSize - 1; firstIndex++) {
            if (!(spheres[firstIndex * 4 + 3] <= 0.0)) {
                for (int secondIndex = firstIndex + 1; secondIndex < veinSize; secondIndex++) {
                    if (!(spheres[secondIndex * 4 + 3] <= 0.0)) {
                        double deltaX = spheres[firstIndex * 4] - spheres[secondIndex * 4];
                        double deltaY = spheres[firstIndex * 4 + 1] - spheres[secondIndex * 4 + 1];
                        double deltaZ = spheres[firstIndex * 4 + 2] - spheres[secondIndex * 4 + 2];
                        double deltaRadius = spheres[firstIndex * 4 + 3] - spheres[secondIndex * 4 + 3];
                        if (deltaRadius * deltaRadius > deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) {
                            if (deltaRadius > 0.0) {
                                spheres[secondIndex * 4 + 3] = -1.0;
                            } else {
                                spheres[firstIndex * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        for (int index = 0; index < veinSize; index++) {
            double radius = spheres[index * 4 + 3];
            if (!(radius < 0.0)) {
                double centerX = spheres[index * 4];
                double centerY = spheres[index * 4 + 1];
                double centerZ = spheres[index * 4 + 2];
                int fromX = Math.max(MathHelper.floor(centerX - radius), minX);
                int fromY = Math.max(MathHelper.floor(centerY - radius), minY);
                int fromZ = Math.max(MathHelper.floor(centerZ - radius), minZ);
                int toX = Math.max(MathHelper.floor(centerX + radius), fromX);
                int toY = Math.max(MathHelper.floor(centerY + radius), fromY);
                int toZ = Math.max(MathHelper.floor(centerZ + radius), fromZ);

                for (int blockX = fromX; blockX <= toX; blockX++) {
                    double normX = (blockX + 0.5 - centerX) / radius;
                    if (normX * normX < 1.0) {
                        for (int blockY = fromY; blockY <= toY; blockY++) {
                            double normY = (blockY + 0.5 - centerY) / radius;
                            if (normX * normX + normY * normY < 1.0) {
                                for (int blockZ = fromZ; blockZ <= toZ; blockZ++) {
                                    double normZ = (blockZ + 0.5 - centerZ) / radius;
                                    if (normX * normX + normY * normY + normZ * normZ < 1.0) {
                                        int bitIndex = blockX - minX + (blockY - minY) * horizontalSize + (blockZ - minZ) * horizontalSize * verticalSize;
                                        if (!visited.get(bitIndex)) {
                                            visited.set(bitIndex);
                                            mutablePos.set(blockX, blockY, blockZ);
                                            if (blockY >= -64
                                                && blockY < 320
                                                && this.shouldPlaceOre(world, mutablePos, ore, random)) {
                                                positions.add(new Vec3d(blockX, blockY, blockZ));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean shouldPlaceOre(ClientWorld world, BlockPos pos, OreDefinition ore, ChunkRandom random) {
        BlockState state = world.getBlockState(pos);

        for (Target target : (Iterable<Target>)(ore.targets)) {
            if (state.isOf(target.state.getBlock()) || target.target.test(state, random)) {
                if (this.shouldNotDiscard(random, ore.discardOnAirChance) || !this.isExposedToAir(world, pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean shouldNotDiscard(ChunkRandom random, float discardChance) {
        if (discardChance <= 0.0F) {
            return true;
        } else {
            return discardChance >= 1.0F ? false : random.nextFloat() >= discardChance;
        }
    }

    private boolean isExposedToAir(ClientWorld world, BlockPos pos) {
        Direction[] directions = Direction.values();
        int directionCount = directions.length;

        for (int directionIndex = 0; directionIndex < directionCount; directionIndex++) {
            if (world.getBlockState(pos.offset(directions[directionIndex])).isAir()) {
                return true;
            }
        }

        return false;
    }

    private ArrayList generateScatteredOre(ClientWorld world, ChunkRandom random, BlockPos origin, OreDefinition ore) {
        ArrayList positions = new ArrayList();
        int attempts = random.nextInt(ore.size + 1);

        for (int attempt = 0; attempt < attempts; attempt++) {
            int spread = Math.min(attempt, 7);
            int oreX = this.getSpread(random, spread) + origin.getX();
            int oreY = this.getSpread(random, spread) + origin.getY();
            int oreZ = this.getSpread(random, spread) + origin.getZ();
            if (this.shouldPlaceOre(world, new BlockPos(oreX, oreY, oreZ), ore, random)) {
                positions.add(new Vec3d(oreX, oreY, oreZ));
            }
        }

        return positions;
    }

    private int getSpread(ChunkRandom random, int spread) {
        return Math.round((random.nextFloat() - random.nextFloat()) * spread);
    }
}

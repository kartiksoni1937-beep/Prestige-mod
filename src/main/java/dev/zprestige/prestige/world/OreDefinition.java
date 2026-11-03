package dev.zprestige.prestige.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper.Impl;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.ScatteredOreFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

public class OreDefinition {
    public int step;
    public int index;
    public String name;
    public IntProvider count;
    public HeightProvider heightProvider;
    public HeightContext heightContext;
    public float rarity;
    public float discardOnAirChance;
    public int size;
    public List targets;
    public Color color;
    public boolean scattered;

    public static Map getOresByBiome(String dimension) {
        WrapperLookup registries = BuiltinRegistries.createWrapperLookup();
        Impl placedFeatures = registries.getOrThrow(RegistryKeys.PLACED_FEATURE);
        Map dimensions = registries.getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder().dimensions();

        Set biomes = (switch (dimension) {
            case "the_nether" -> (DimensionOptions)dimensions.get(DimensionOptions.NETHER);
            case "the_end" -> (DimensionOptions)dimensions.get(DimensionOptions.END);
            default -> (DimensionOptions)dimensions.get(DimensionOptions.OVERWORLD);
        }).chunkGenerator().getBiomeSource().getBiomes();
        List biomeList = biomes.stream().toList();
        List indexedFeatures = PlacedFeatureIndexer.collectIndexedFeatures(biomeList, biomeEntry -> ((Biome)((net.minecraft.registry.entry.RegistryEntry)biomeEntry).value()).getGenerationSettings().getFeatures(), true);
        HashMap oresByFeature = new HashMap();
        RegistryKey featureKey = OrePlacedFeatures.ORE_COAL_LOWER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Coal", new Color(15, 15, 15));
        featureKey = OrePlacedFeatures.ORE_COAL_UPPER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Coal", new Color(15, 15, 15));
        featureKey = OrePlacedFeatures.ORE_IRON_MIDDLE;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Iron", new Color(255, 255, 255));
        featureKey = OrePlacedFeatures.ORE_IRON_SMALL;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Iron", new Color(255, 255, 255));
        featureKey = OrePlacedFeatures.ORE_IRON_UPPER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Iron", new Color(255, 255, 255));
        featureKey = OrePlacedFeatures.ORE_GOLD;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Gold", new Color(255, 255, 0));
        featureKey = OrePlacedFeatures.ORE_GOLD_LOWER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Gold", new Color(255, 255, 0));
        featureKey = OrePlacedFeatures.ORE_GOLD_EXTRA;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Gold", new Color(255, 255, 0));
        featureKey = OrePlacedFeatures.ORE_GOLD_NETHER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Gold", new Color(255, 255, 0));
        featureKey = OrePlacedFeatures.ORE_GOLD_DELTAS;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Gold", new Color(255, 255, 0));
        featureKey = OrePlacedFeatures.ORE_REDSTONE;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Redstone", new Color(255, 0, 0));
        featureKey = OrePlacedFeatures.ORE_REDSTONE_LOWER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Redstone", new Color(255, 0, 0));
        featureKey = OrePlacedFeatures.ORE_DIAMOND;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Diamond", new Color(0, 255, 255));
        featureKey = OrePlacedFeatures.ORE_DIAMOND_BURIED;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Diamond", new Color(0, 255, 255));
        featureKey = OrePlacedFeatures.ORE_DIAMOND_LARGE;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Diamond", new Color(0, 255, 255));
        featureKey = OrePlacedFeatures.ORE_DIAMOND_MEDIUM;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Diamond", new Color(0, 255, 255));
        featureKey = OrePlacedFeatures.ORE_LAPIS;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Lapis", new Color(0, 0, 200));
        featureKey = OrePlacedFeatures.ORE_LAPIS_BURIED;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Lapis", new Color(0, 0, 200));
        featureKey = OrePlacedFeatures.ORE_COPPER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Copper", new Color(255, 120, 0));
        featureKey = OrePlacedFeatures.ORE_COPPER_LARGE;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Copper", new Color(255, 120, 0));
        featureKey = OrePlacedFeatures.ORE_EMERALD;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 6, "Emerald", new Color(0, 255, 0));
        featureKey = OrePlacedFeatures.ORE_QUARTZ_NETHER;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Quartz", new Color(200, 200, 200));
        featureKey = OrePlacedFeatures.ORE_QUARTZ_DELTAS;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Quartz", new Color(200, 200, 200));
        featureKey = OrePlacedFeatures.ORE_DEBRIS_SMALL;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Ancient Debris", new Color(200, 30, 255));
        featureKey = OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE;
        registerOre(oresByFeature, indexedFeatures, placedFeatures, featureKey, 7, "Ancient Debris", new Color(200, 30, 255));
        HashMap oresByBiome = new HashMap();
        biomeList.forEach(
            biome -> {
                oresByBiome.put((RegistryKey)((net.minecraft.registry.entry.RegistryEntry)biome).getKey().get(), new ArrayList());
                ((Biome)((net.minecraft.registry.entry.RegistryEntry)biome).value())
                    .getGenerationSettings()
                    .getFeatures()
                    .stream()
                    .flatMap(RegistryEntryList::stream)
                    .map(RegistryEntry::value)
                    .filter(oresByFeature::containsKey)
                    .forEach(placedFeature -> ((List)oresByBiome.get(((net.minecraft.registry.entry.RegistryEntry)biome).getKey().get())).add((OreDefinition)oresByFeature.get(placedFeature)));
            }
        );
        return oresByBiome;
    }

    private static void registerOre(Map oresByFeature, List indexedFeatures, Impl placedFeatures, RegistryKey featureKey, int generationStep, String oreName, Color oreColor) {
        PlacedFeature placedFeature = (PlacedFeature)placedFeatures.getOrThrow(featureKey).value();
        oresByFeature.put(placedFeature, new OreDefinition(placedFeature, generationStep, ((IndexedFeatures)indexedFeatures.get(generationStep)).indexMapping().applyAsInt(placedFeature), oreName, oreColor));
    }

    private OreDefinition(PlacedFeature placedFeature, int generationStep, int featureIndex, String oreName, Color oreColor) {
        super();
        this.count = ConstantIntProvider.create(1);
        this.rarity = 1.0F;
        this.targets = List.of();
        this.step = generationStep;
        this.index = featureIndex;
        this.name = oreName;
        this.color = oreColor;
        this.heightContext = new HeightContext(null, HeightLimitView.create(MinecraftClient.getInstance().world.getBottomY(), MinecraftClient.getInstance().world.getDimension().logicalHeight()));

        for (PlacementModifier modifier : placedFeature.placementModifiers()) {
            if (modifier instanceof CountPlacementModifier) {
                this.count = new CountPlacementModifierAccessor((CountPlacementModifier)modifier).getCount();
            } else if (modifier instanceof HeightRangePlacementModifier) {
                this.heightProvider = new HeightRangePlacementModifierAccessor((HeightRangePlacementModifier)modifier).getHeight();
            } else if (modifier instanceof RarityFilterPlacementModifier) {
                this.rarity = new RarityFilterPlacementModifierAccessor((RarityFilterPlacementModifier)modifier).getChance();
            }
        }

        if (placedFeature.feature().value().config() instanceof OreFeatureConfig oreConfig) {
            this.discardOnAirChance = oreConfig.discardOnAirChance;
            this.size = oreConfig.size;
            this.targets = List.copyOf(oreConfig.targets);
            if (placedFeature.feature().value().feature() instanceof ScatteredOreFeature) {
                this.scattered = true;
            }
        } else {
            throw new IllegalStateException("config for " + placedFeature + "is not OreFeatureConfig.class");
        }
    }
}

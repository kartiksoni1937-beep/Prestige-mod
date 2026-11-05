package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;

public class HeightRangePlacementModifierAccessor {
    private final HeightRangePlacementModifier modifier;
    private static final String HEIGHT_FIELD = "height";

    public HeightRangePlacementModifierAccessor(HeightRangePlacementModifier heightModifier) {
        this.modifier = heightModifier;
    }

    public HeightProvider getHeight() {
        return (HeightProvider)FieldAccessor.bindMapped(this.modifier, HEIGHT_FIELD, HeightRangePlacementModifier.class, HeightProvider.class).get();
    }
}

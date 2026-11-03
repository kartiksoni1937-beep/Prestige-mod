package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;

public class CountPlacementModifierAccessor {
    private final CountPlacementModifier modifier;
    private static final String COUNT_FIELD = "count";

    public CountPlacementModifierAccessor(CountPlacementModifier countModifier) {
        this.modifier = countModifier;
    }

    public IntProvider getCount() {
        return (IntProvider)FieldAccessor.bindMapped(this.modifier, COUNT_FIELD, CountPlacementModifier.class, IntProvider.class).get();
    }
}

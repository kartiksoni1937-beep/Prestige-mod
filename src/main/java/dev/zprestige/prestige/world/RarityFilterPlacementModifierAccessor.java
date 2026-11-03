package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

public class RarityFilterPlacementModifierAccessor {
    private final RarityFilterPlacementModifier modifier;
    private static final String CHANCE_FIELD = "chance";

    public RarityFilterPlacementModifierAccessor(RarityFilterPlacementModifier rarityModifier) {
        this.modifier = rarityModifier;
    }

    public int getChance() {
        return (Integer)FieldAccessor.bindMapped(this.modifier, CHANCE_FIELD, RarityFilterPlacementModifier.class, int.class).get();
    }
}

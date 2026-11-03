package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.world.gen.HeightContext;

public class HeightContextAccessor {
    private final Object heightContext;

    public HeightContextAccessor(Object context) {
        this.heightContext = context;
    }

    public void setMinY(int minY) {
        FieldAccessor.bindMapped(this.heightContext, "minY", HeightContext.class, int.class).set(minY);
    }

    public void setHeight(int height) {
        FieldAccessor.bindMapped(this.heightContext, "height", HeightContext.class, int.class).set(height);
    }
}

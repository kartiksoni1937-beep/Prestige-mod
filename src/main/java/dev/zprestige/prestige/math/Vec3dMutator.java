package dev.zprestige.prestige.math;

import dev.zprestige.prestige.reflect.FieldAccessor;
import dev.zprestige.prestige.reflect.MappedField;
import net.minecraft.util.math.Vec3d;

public class Vec3dMutator {
    private static final MappedField X_FIELD = FieldAccessor.mappedField(Vec3d.class, "x", double.class);
    private static final MappedField Y_FIELD = FieldAccessor.mappedField(Vec3d.class, "y", double.class);
    private static final MappedField Z_FIELD = FieldAccessor.mappedField(Vec3d.class, "z", double.class);
    private final Vec3d target;

    public Vec3dMutator(Vec3d vec) {
        this.target = vec;
    }

    public void set(double x, double y, double z) {
        X_FIELD.setDouble(this.target, x);
        Y_FIELD.setDouble(this.target, y);
        Z_FIELD.setDouble(this.target, z);
    }
}

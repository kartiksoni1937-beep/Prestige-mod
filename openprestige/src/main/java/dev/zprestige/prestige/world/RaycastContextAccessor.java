package dev.zprestige.prestige.world;

import dev.zprestige.prestige.reflect.FieldAccessor;
import dev.zprestige.prestige.reflect.MappedField;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class RaycastContextAccessor {
    private static final MappedField START_FIELD = FieldAccessor.mappedField(RaycastContext.class, "start", Vec3d.class);
    private static final MappedField END_FIELD = FieldAccessor.mappedField(RaycastContext.class, "end", Vec3d.class);
    private static final MappedField SHAPE_TYPE_FIELD = FieldAccessor.mappedField(RaycastContext.class, "shapeType", ShapeType.class);
    private static final MappedField FLUID_FIELD = FieldAccessor.mappedField(RaycastContext.class, "fluid", FluidHandling.class);
    private static final MappedField SHAPE_CONTEXT_FIELD = FieldAccessor.mappedField(RaycastContext.class, "shapeContext", ShapeContext.class);
    private final RaycastContext context;

    public RaycastContextAccessor(RaycastContext raycastContext) {
        this.context = raycastContext;
    }

    public void setStart(Vec3d start) {
        START_FIELD.set(this.context, start);
    }

    public void setEnd(Vec3d end) {
        END_FIELD.set(this.context, end);
    }

    public void setShapeType(ShapeType shapeType) {
        SHAPE_TYPE_FIELD.set(this.context, shapeType);
    }

    public void setFluidHandling(FluidHandling fluidHandling) {
        FLUID_FIELD.set(this.context, fluidHandling);
    }

    public void setShapeContext(ShapeContext shapeContext) {
        SHAPE_CONTEXT_FIELD.set(this.context, shapeContext);
    }

    public void setAll(Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling, Entity entity) {
        this.setStart(start);
        this.setEnd(end);
        this.setShapeType(shapeType);
        this.setFluidHandling(fluidHandling);
        this.setShapeContext(ShapeContext.of(entity));
    }
}

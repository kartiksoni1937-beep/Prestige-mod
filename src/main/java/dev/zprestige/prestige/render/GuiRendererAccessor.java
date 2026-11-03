package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.FieldAccessor;
import dev.zprestige.prestige.reflect.MappedField;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.ProjectionMatrix2;

public class GuiRendererAccessor {
    private static final MappedField PROJECTION_MATRIX_FIELD;
    private final GuiRenderer guiRenderer;

    public GuiRendererAccessor(GuiRenderer renderer) {
        this.guiRenderer = renderer;
    }

    public ProjectionMatrix2 getProjectionMatrix() {
        return (ProjectionMatrix2)PROJECTION_MATRIX_FIELD.get(this.guiRenderer);
    }

    static {
        PROJECTION_MATRIX_FIELD = FieldAccessor.mappedField(GuiRenderer.class, "guiProjectionMatrix", ProjectionMatrix2.class);
    }
}

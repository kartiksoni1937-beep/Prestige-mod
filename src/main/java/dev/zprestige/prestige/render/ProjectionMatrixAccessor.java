package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.MethodAccessor;
import net.minecraft.client.render.ProjectionMatrix2;
import org.joml.Matrix4f;

public class ProjectionMatrixAccessor {
    private static final MethodAccessor GET_MATRIX_METHOD;
    private final ProjectionMatrix2 projectionMatrix;

    public ProjectionMatrixAccessor(ProjectionMatrix2 projection) {
        this.projectionMatrix = projection;
    }

    public Matrix4f getMatrix(float width, float height) {
        try {
            return (Matrix4f)GET_MATRIX_METHOD.invoke(this.projectionMatrix, new Object[]{width, height});
        } catch (Throwable error) {
            throw new RuntimeException(error);
        }
    }

    static {
        GET_MATRIX_METHOD = MethodAccessor.ofMapped(ProjectionMatrix2.class, "getMatrix", Matrix4f.class, new Class[]{float.class, float.class});
    }
}

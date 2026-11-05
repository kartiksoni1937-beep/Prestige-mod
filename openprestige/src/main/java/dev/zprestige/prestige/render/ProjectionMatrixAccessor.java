package dev.zprestige.prestige.render;

import org.joml.Matrix4f;

public class ProjectionMatrixAccessor {
    private final Object projectionMatrix;

    public ProjectionMatrixAccessor(Object projection) {
        this.projectionMatrix = projection;
    }

    public Matrix4f getMatrix(float width, float height) {
        try {
            java.lang.reflect.Method method = this.projectionMatrix.getClass().getMethod("getMatrix", float.class, float.class);
            return (Matrix4f)method.invoke(this.projectionMatrix, width, height);
        } catch (Throwable error) {
            throw new RuntimeException(error);
        }
    }
}

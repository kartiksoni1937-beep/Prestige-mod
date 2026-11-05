package dev.zprestige.prestige.reflect;

import net.minecraft.client.render.Camera;

public class CameraAccessor {
    private static final MethodAccessor SET_POS = MethodAccessor.ofMapped(
        Camera.class, "setPos", void.class, new Class[]{double.class, double.class, double.class}
    );
    private static final MethodAccessor SET_ROTATION;
    private final Camera camera;

    public CameraAccessor(Camera targetCamera) {
        this.camera = targetCamera;
    }

    public void setPos(double x, double y, double z) {
        try {
            SET_POS.invoke(this.camera, new Object[]{x, y, z});
        } catch (Throwable invokeError) {
            throw new RuntimeException(invokeError);
        }
    }

    public void setRotation(float yaw, float pitch) {
        try {
            SET_ROTATION.invoke(this.camera, new Object[]{yaw, pitch});
        } catch (Throwable invokeError) {
            throw new RuntimeException(invokeError);
        }
    }

    static {
        SET_ROTATION = MethodAccessor.ofMapped(Camera.class, "setRotation", void.class, new Class[]{float.class, float.class});
    }
}

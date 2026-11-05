package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.CameraPositionEvent;
import dev.zprestige.prestige.event.CameraRotationEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.render.Camera;

@CTransformer({Camera.class})
public class CameraMixin {
    @CInject(
        method = {"setPos"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void overrideCameraPosAtSetPosHead(double x, double y, double z, InjectionCallback ci) {
        if (!CameraPositionEvent.applyingOverride && Prestige.eventBus != null && HookBridge.hasCameraPosListeners(Prestige.eventBus, CameraPositionEvent.class)) {
            CameraPositionEvent event = HookBridge.createCameraPosEvent(x, y, z);
            if (HookBridge.postCameraPosEvent(event)) {
                CameraPositionEvent.applyingOverride = true;
                try {
                    HookBridge.setCameraPos(HookBridge.createCameraPosAccessor((Camera)(Object)this), HookBridge.getCameraPosX(event), HookBridge.getCameraPosY(event), HookBridge.getCameraPosZ(event));
                } finally {
                    CameraPositionEvent.applyingOverride = false;
                }
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"setRotation"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void overrideCameraRotationAtSetRotationHead(float yaw, float pitch, InjectionCallback ci) {
        if (!CameraRotationEvent.applyingOverride) {
            CameraRotationEvent event = HookBridge.createCameraRotationEvent(yaw, pitch);
            if (HookBridge.postCameraRotationEvent(event)) {
                CameraRotationEvent.applyingOverride = true;
                try {
                    HookBridge.setCameraRotation(HookBridge.createCameraRotationAccessor((Camera)(Object)this), HookBridge.getCameraYaw(event), HookBridge.getCameraPitch(event));
                } finally {
                    CameraRotationEvent.applyingOverride = false;
                }
                ci.cancel();
            }
        }
    }
}

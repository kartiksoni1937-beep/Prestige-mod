package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.VelocityUpdateEvent;
import dev.zprestige.prestige.event.BoundingBoxEvent;
import dev.zprestige.prestige.event.MovementYawEvent;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@CTransformer({Entity.class})
public abstract class EntityMixin {
    @CInject(
        method = {"updateVelocity"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void applyMovementYawAtUpdateVelocityHead(float speed, Vec3d movementInput, InjectionCallback ci) {
        Entity entity = (Entity)(Object)this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && Prestige.silentRotationManager != null && HookBridge.getUpdateVelocityRotation(Prestige.silentRotationManager) != null) {
            MovementYawEvent event = HookBridge.createUpdateVelocityEvent(entity.getYaw(), movementInput);
            if (HookBridge.postUpdateVelocityEvent(event)) {
                float yaw = HookBridge.getUpdateVelocityYaw(event);
                Vec3d input = HookBridge.getUpdateVelocityInputOrNull(event) != null ? HookBridge.getUpdateVelocityInput(event) : movementInput;
                double inputLengthSq = input.lengthSquared();
                Vec3d vec3d;
                if (inputLengthSq < 1.0E-7) {
                    vec3d = Vec3d.ZERO;
                } else {
                    Vec3d scaled = (inputLengthSq > 1.0 ? input.normalize() : input).multiply(speed);
                    float sinYaw = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
                    float cosYaw = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
                    vec3d = HookBridge.createVelocityVector(scaled.x * cosYaw - scaled.z * sinYaw, scaled.y, scaled.z * cosYaw + scaled.x * sinYaw);
                }

                entity.setVelocity(entity.getVelocity().add(vec3d));
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"getBoundingBox"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overrideBoundingBoxAtReturn(InjectionCallback cir) {
        Entity entity = (Entity)(Object)this;
        if (entity != null && MinecraftClient.getInstance().player != null) {
            if (Prestige.eventBus != null && HookBridge.hasBoundingBoxListeners(Prestige.eventBus, BoundingBoxEvent.class)) {
                Box box = (Box)cir.getReturnValue();
                BoundingBoxEvent event = HookBridge.createBoundingBoxEvent(entity, box);
                if (HookBridge.postBoundingBoxEvent(event)) {
                    cir.setReturnValue(HookBridge.getModifiedBoundingBox(event));
                }
            }
        }
    }

    @CInject(
        method = {"setVelocityClient"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void overrideVelocityAtSetVelocityClientHead(Vec3d velocity, InjectionCallback ci) {
        Entity entity = (Entity)(Object)this;
        if (entity == MinecraftClient.getInstance().player) {
            VelocityUpdateEvent event = HookBridge.createSetVelocityEvent(velocity.x, velocity.y, velocity.z);
            if (HookBridge.postSetVelocityEvent(event)) {
                entity.setVelocity(HookBridge.getSetVelocityX(event), HookBridge.getSetVelocityY(event), HookBridge.getSetVelocityZ(event));
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"getRotationVec"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    public void applySilentRotationAtGetRotationVecHead(float tickDelta, InjectionCallback cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && (Object)this == mc.player && Prestige.silentRotationManager != null) {
            Rotation rot = HookBridge.getRotationVecOverride(Prestige.silentRotationManager);
            if (rot != null) {
                cir.setReturnValue(mc.player.getRotationVector(HookBridge.getRotationVecPitch(rot), HookBridge.getRotationVecYaw(rot)));
            }
        }
    }
}

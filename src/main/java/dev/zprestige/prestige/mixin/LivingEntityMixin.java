package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.TravelEvent;
import dev.zprestige.prestige.event.MovementYawEvent;
import dev.zprestige.prestige.event.HandSwingDurationEvent;
import dev.zprestige.prestige.event.ShieldBreakEvent;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@CTransformer({LivingEntity.class})
public abstract class LivingEntityMixin {
    @CInject(
        method = {"travel"},
        target = {@CTarget("HEAD")}
    )
    private void spoofRotationAtTravelHead(Vec3d movementInput, InjectionCallback ci) {
        if (MinecraftClient.getInstance().player != null) {
            if (((LivingEntity)(Object)this).equals(MinecraftClient.getInstance().player)) {
                if (Prestige.silentRotationManager != null && HookBridge.getTravelPreRotation(Prestige.silentRotationManager) != null) {
                    TravelEvent event = HookBridge.createTravelPreEvent(EventPhase.PRE, ((LivingEntity)(Object)this).getYaw(), ((LivingEntity)(Object)this).getPitch());
                    if (HookBridge.postTravelPreEvent(event)) {
                        TravelEvent.savedYaw = ((LivingEntity)(Object)this).getYaw();
                        TravelEvent.savedPitch = ((LivingEntity)(Object)this).getPitch();
                        ((LivingEntity)(Object)this).setYaw(event.yaw);
                        ((LivingEntity)(Object)this).setPitch(event.pitch);
                    }
                }
            }
        }
    }

    @CInject(
        method = {"travel"},
        target = {@CTarget("RETURN")}
    )
    private void restoreRotationAtTravelReturn(Vec3d movementInput) {
        if (MinecraftClient.getInstance().player != null) {
            if (((LivingEntity)(Object)this).equals(MinecraftClient.getInstance().player)) {
                if (Prestige.silentRotationManager != null && HookBridge.getTravelPostRotation(Prestige.silentRotationManager) != null) {
                    TravelEvent event = HookBridge.createTravelPostEvent(EventPhase.POST, ((LivingEntity)(Object)this).getYaw(), ((LivingEntity)(Object)this).getPitch());
                    if (HookBridge.postTravelPostEvent(event)) {
                        ((LivingEntity)(Object)this).setYaw(TravelEvent.savedYaw);
                        ((LivingEntity)(Object)this).setPitch(TravelEvent.savedPitch);
                    }
                }
            }
        }
    }

    @CInject(
        method = {"getHandSwingDuration"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void overrideHandSwingDurationAtHead(InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasHandSwingDurationListeners(Prestige.eventBus, HandSwingDurationEvent.class)) {
            HandSwingDurationEvent event = HookBridge.createHandSwingDurationEvent(0);
            if (HookBridge.postHandSwingDurationEvent(event)) {
                ci.setReturnValue(event.duration);
            }
        }
    }

    @CInject(
        method = {"handleStatus"},
        target = {@CTarget("HEAD")}
    )
    private void postShieldBreakEventAtHandleStatusHead(byte status) {
        if (Prestige.eventBus != null && HookBridge.hasShieldBreakListeners(Prestige.eventBus, ShieldBreakEvent.class)) {
            if (status == 30) {
                HookBridge.postShieldBreakEvent(HookBridge.createShieldBreakEvent((LivingEntity)(Object)this));
            }
        }
    }

    @CInject(
        method = {"jump"},
        target = {@CTarget(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V"
        )},
        cancellable = true
    )
    private void applyJumpYawBeforeAddVelocityInternal(InjectionCallback ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity == MinecraftClient.getInstance().player && Prestige.silentRotationManager != null && HookBridge.getJumpRotation(Prestige.silentRotationManager) != null) {
            float yaw = entity.getYaw();
            MovementYawEvent event = HookBridge.createJumpYawEvent(yaw);
            if (HookBridge.postJumpYawEvent(event)) {
                yaw = HookBridge.getJumpYaw(event);
                float yawRadians = yaw * (float) (Math.PI / 180.0);
                entity.setVelocity(entity.getVelocity().add(-MathHelper.sin(yawRadians) * 0.2F, 0.0, MathHelper.cos(yawRadians) * 0.2F));
                entity.velocityDirty = true;
                ci.cancel();
            }
        }
    }
}

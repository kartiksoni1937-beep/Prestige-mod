package dev.zprestige.prestige.mixin;

import com.mojang.authlib.GameProfile;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;

@CTransformer({ClientPlayerEntity.class})
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @CInject(
        method = {"swingHand"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void postSwingHandEventAtHead(Hand hand, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasSwingHandListeners(Prestige.eventBus, SwingHandEvent.class)) {
            SwingHandEvent event = HookBridge.createSwingHandPreEvent(EventPhase.PRE);
            if (HookBridge.postSwingHandPreEvent(event)) {
                ci.cancel();
            } else if (!HookBridge.shouldSkipSwingHandPost(event) && HookBridge.postSwingHandPostEvent(HookBridge.createSwingHandPostEvent(EventPhase.POST))) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"sendMovementPackets"},
        target = {@CTarget("HEAD")}
    )
    private void spoofRotationAtSendMovementPacketsHead() {
        SendMovementPacketsEvent.savedYaw = this.getYaw();
        SendMovementPacketsEvent.savedPitch = this.getPitch();
        SendMovementPacketsEvent event = HookBridge.createMovementPacketsPreEvent(EventPhase.PRE, SendMovementPacketsEvent.savedYaw, SendMovementPacketsEvent.savedPitch);
        boolean changed = HookBridge.postMovementPacketsPreEvent(event);
        Rotation movementOverride = Prestige.silentRotationManager == null ? null : HookBridge.getLockedMovementRotation(Prestige.silentRotationManager);
        if (movementOverride != null) {
            this.setYaw(HookBridge.getLockedRotationYaw(movementOverride));
            this.setPitch(HookBridge.getLockedRotationPitch(movementOverride));
        } else if (changed) {
            this.setYaw(HookBridge.getMovementPacketsYaw(event));
            this.setPitch(HookBridge.getMovementPacketsPitch(event));
        }
    }

    @CInject(
        method = {"sendMovementPackets"},
        target = {@CTarget("RETURN")}
    )
    private void restoreRotationAtSendMovementPacketsReturn() {
        SendMovementPacketsEvent event = HookBridge.createMovementPacketsPostEvent(EventPhase.POST, SendMovementPacketsEvent.savedYaw, SendMovementPacketsEvent.savedPitch);
        HookBridge.postMovementPacketsPostEvent(event);
        this.setYaw(SendMovementPacketsEvent.savedYaw);
        this.setPitch(SendMovementPacketsEvent.savedPitch);
    }
}

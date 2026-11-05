package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.event.EntityAddRemoveEvent;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;

@CTransformer({ClientWorld.class})
public class ClientWorldMixin {
    @CInject(
        method = {"addEntity"},
        target = {@CTarget("TAIL")}
    )
    private void postEntityAddedEventAtAddEntityTail(Entity entity, InjectionCallback ci) {
        if (entity != null) {
            EntityAddRemoveEvent event = HookBridge.createEntityAddedEvent(entity, EventPhase.PRE);
            HookBridge.postEntityAddedEvent(event);
        }
    }

    @CInject(
        method = {"removeEntity"},
        target = {@CTarget("HEAD")}
    )
    private void postEntityRemovedEventAtRemoveEntityHead(int entityId, RemovalReason removalReason, InjectionCallback ci) {
        ClientWorld world = (ClientWorld)(Object)this;
        if (world != null) {
            Entity entity = world.getEntityById(entityId);
            if (entity != null) {
                EntityAddRemoveEvent event = HookBridge.createEntityRemovedEvent(entity, EventPhase.POST);
                HookBridge.postEntityRemovedEvent(event);
            }
        }
    }
}

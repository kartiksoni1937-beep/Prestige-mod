package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AddParticleEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;

@CTransformer({ParticleManager.class})
public class ParticleManagerMixin {
    @CInject(
        method = {"addParticle"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void postAddParticleEventAtHead(Particle particle, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasAddParticleListeners(Prestige.eventBus, AddParticleEvent.class)) {
            AddParticleEvent event = HookBridge.createAddParticleEvent();
            if (HookBridge.postAddParticleEvent(event)) {
                ci.cancel();
            }
        }
    }
}

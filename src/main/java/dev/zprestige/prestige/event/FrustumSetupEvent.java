package dev.zprestige.prestige.event;

import net.minecraft.client.render.Frustum;

public class FrustumSetupEvent extends Event {
    private final Frustum frustum;

    public FrustumSetupEvent(Frustum capturedFrustum) {
        this.frustum = capturedFrustum;
    }

    public Frustum getFrustum() {
        return this.frustum;
    }
}

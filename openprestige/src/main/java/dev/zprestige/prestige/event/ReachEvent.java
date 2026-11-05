package dev.zprestige.prestige.event;

import java.util.function.Predicate;

public class ReachEvent extends Event {
    private float extraReach;

    public ReachEvent(float initialExtra) {
        this.extraReach = initialExtra;
    }

    public float getExtraReach() {
        return this.extraReach;
    }

    public void setExtraReach(float value) {
        this.extraReach = value;
    }

    public static Predicate getHittableFilter() {
        return entity -> !((net.minecraft.entity.Entity)entity).isSpectator() && ((net.minecraft.entity.Entity)entity).canHit();
    }
}

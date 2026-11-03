package dev.zprestige.prestige.event;

import net.minecraft.entity.LivingEntity;

public class ShieldBreakEvent extends Event {
    public final LivingEntity entity;

    public ShieldBreakEvent(LivingEntity shieldHolder) {
        this.entity = shieldHolder;
    }
}

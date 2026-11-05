package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.entity.LivingEntity;

public class JumpCooldownAccessor {
    private final LivingEntity entity;
    private static final String JUMPING_COOLDOWN_FIELD = "jumpingCooldown";

    public JumpCooldownAccessor(LivingEntity livingEntity) {
        this.entity = livingEntity;
    }

    public void setJumpingCooldown(int ticks) {
        FieldAccessor.bindMapped(this.entity, JUMPING_COOLDOWN_FIELD, LivingEntity.class, int.class).set(ticks);
    }
}

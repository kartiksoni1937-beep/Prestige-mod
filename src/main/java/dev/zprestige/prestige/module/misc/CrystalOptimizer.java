package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.AttackEntityEvent;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;

public class CrystalOptimizer extends Module {
    public CrystalOptimizer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (target instanceof EndCrystalEntity && mc.player != null) {
            if (mc.player.getStatusEffect(StatusEffects.WEAKNESS) != null) {
                return;
            }

            target.remove(RemovalReason.KILLED);
            target.setRemoved(RemovalReason.KILLED);
            target.onRemoved();
        }
    }
}

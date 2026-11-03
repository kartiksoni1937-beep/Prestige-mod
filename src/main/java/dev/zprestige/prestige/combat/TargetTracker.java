package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;

public class TargetTracker implements MinecraftAccess {
    private PlayerEntity target;
    private long lastAttackTime;

    public TargetTracker() {
        super();
        this.target = null;
        this.lastAttackTime = 0L;
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.target != null && (mc.world == null || mc.player == null || !this.target.isAlive() || this.target.distanceTo(mc.player) > 15.0F || System.currentTimeMillis() - this.lastAttackTime > 5000L)) {
            this.target = null;
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        Entity attacked = event.getTarget();
        if (attacked instanceof PlayerEntity attackedPlayer) {
            this.setTarget(attackedPlayer);
        } else if (attacked instanceof EndCrystalEntity) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != null
                    && player != mc.player
                    && !Prestige.friendManager.isFriend(player.getName().getString())
                    && player.distanceTo(attacked) <= 5.0F) {
                    this.setTarget(player);
                }
            }
        }
    }

    public PlayerEntity getTarget() {
        return this.target;
    }

    public void setTarget(PlayerEntity newTarget) {
        if (!Prestige.friendManager.isFriend(newTarget.getName().getString())) {
            this.target = newTarget;
            this.lastAttackTime = System.currentTimeMillis();
        }
    }
}

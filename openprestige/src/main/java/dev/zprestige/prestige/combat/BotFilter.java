package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.BotCheckEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.AntiBotQueryEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class BotFilter implements MinecraftAccess {
    private final Set validatedPlayers;
    private final Map validityCache;

    public BotFilter() {
        super();
        this.validatedPlayers = new HashSet();
        this.validityCache = new IdentityHashMap();
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.validityCache.clear();
        if (!this.validatedPlayers.isEmpty()) {
            this.validatedPlayers.removeIf(player -> !((net.minecraft.entity.Entity)player).isAlive() || ((net.minecraft.entity.Entity)player).isRemoved());
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.validatedPlayers.clear();
    }

    public void markValidated(PlayerEntity player) {
        this.validatedPlayers.add(player);
    }

    public Set getValidatedPlayers() {
        return this.validatedPlayers;
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player) {
            return false;
        } else {
            Boolean cached = (Boolean)this.validityCache.get(entity);
            if (cached != null) {
                return cached;
            } else {
                boolean valid = this.checkValidTarget(entity);
                this.validityCache.put(entity, valid);
                return valid;
            }
        }
    }

    private boolean checkValidTarget(Entity entity) {
        if (!(entity instanceof PlayerEntity)) {
            return true;
        } else if (!Prestige.eventBus.hasListeners(AntiBotQueryEvent.class)) {
            return true;
        } else if (!new AntiBotQueryEvent().post()) {
            return true;
        } else {
            return Prestige.eventBus.hasListeners(BotCheckEvent.class) && new BotCheckEvent((PlayerEntity)entity).post()
                ? false
                : this.validatedPlayers.contains(entity) && entity.isAlive();
        }
    }
}

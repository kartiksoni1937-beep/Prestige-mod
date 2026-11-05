package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.ResetAttackCooldownEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.type.EventPhase;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;

public class NoMissDelay extends Module {
    private IntSetting humanizePercent;
    private BoolSetting inCombatOnly;
    private FloatSetting range;
    private boolean allowVanillaMiss = false;
    private static final long PERCENT_ROLL_MAX = 1273304670500028516L;

    public NoMissDelay() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.range.visibleWhen((Predicate<Float>)ignored -> this.inCombatOnly.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onResetAttackCooldown(ResetAttackCooldownEvent event) {
        HitResult crosshairHit = mc.crosshairTarget;
        if (crosshairHit != null) {
            if ((InventoryUtil.isSword(mc.player.getMainHandStack()) || mc.player.getMainHandStack().getItem() instanceof AxeItem)
                && crosshairHit.getType() == Type.MISS) {
                if (this.inCombatOnly.getValue() && !this.isEnemyNearby()) {
                    this.allowVanillaMiss = true;
                    return;
                }

                this.allowVanillaMiss = RandomUtil.randomInt(1, (int)PERCENT_ROLL_MAX) <= this.humanizePercent.getValue();
                if (!this.allowVanillaMiss) {
                    event.cancel();
                }
            }
        }
    }

    private boolean isEnemyNearby() {
        float radius = this.range.getValue();
        float radiusSq = radius * radius;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player
                && player.isAlive()
                && Prestige.botFilter.isValidTarget(player)
                && !Prestige.friendManager.isFriend(player.getName().getString())
                && mc.player.squaredDistanceTo(player) <= radiusSq) {
                return true;
            }
        }

        return false;
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (event.getPhase() == EventPhase.POST) {
            HitResult crosshairHit = mc.crosshairTarget;
            if (crosshairHit != null) {
                if ((InventoryUtil.isSword(mc.player.getMainHandStack()) || mc.player.getMainHandStack().getItem() instanceof AxeItem)
                    && crosshairHit.getType() == Type.MISS
                    && !this.allowVanillaMiss) {
                    mc.player.handSwinging = false;
                    event.cancel();
                }
            }
        }
    }
}

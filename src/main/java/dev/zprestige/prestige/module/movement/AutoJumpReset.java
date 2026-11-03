package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.JumpKeyPresser;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;

public class AutoJumpReset extends Module {
    public AutoJumpReset() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting chance;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null) {
            return;
        }

        DamageSource damageSource = mc.player.getRecentDamageSource();
        if (mc.currentScreen == null
            && !mc.player.isBlocking()
            && !mc.player.isUsingItem()
            && !mc.player.isTouchingWater()
            && damageSource != null
            && damageSource.getAttacker() instanceof PlayerEntity) {
            if (mc.player.isOnGround()
                && mc.player.hurtTime == mc.player.maxHurtTime
                && !mc.player.isOnFire()
                && RandomUtil.RANDOM.nextFloat() <= this.chance.getValue() / 100.0F) {
                JumpKeyPresser.pressJump();
            }
        }
    }

    @Override
    public void onDisable() {
        JumpKeyPresser.cancelPress();
    }
}

package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;

public class AutoPot extends Module {
    public AutoPot() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private FloatSetting health;
    private FloatSetting pitch;
    private StopWatch throwTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.UHC);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.currentScreen == null && mc.isWindowFocused()) {
            boolean autoMode = this.mode.getValue().equals("Auto");
            if (autoMode && mc.player.getHealth() > this.health.getValue()) {
                this.throwTimer.reset();
            } else {
                if (this.throwTimer.hasElapsed(300.0F)) {
                    if (autoMode) {
                        Integer potionSlot = InventoryUtil.findSplashPotion(0, 9, StatusEffects.INSTANT_HEALTH);
                        if (potionSlot == null) {
                            return;
                        }

                        InventoryUtil.runWithSlot(potionSlot, () -> {
                            float previousPitch = mc.player.getPitch();
                            mc.player.setPitch(this.pitch.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat());
                            PlayerInteractions.interactItem(Hand.MAIN_HAND);
                            mc.player.setPitch(previousPitch);
                        }, true);
                        this.throwTimer.reset();
                    } else {
                        float originalPitch = mc.player.getPitch();
                        mc.player.setPitch(this.pitch.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat());
                        PlayerInteractions.interactItem(Hand.MAIN_HAND);
                        mc.player.setPitch(originalPitch);
                        this.throwTimer.reset();
                    }
                }
            }
        }
    }
}

package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import java.util.Set;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class PotionRefill extends Module {
    public PotionRefill() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting healthPotOnly;
    private RangeSetting delay;
    private StopWatch refillTimer = new StopWatch();

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.UHC);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen instanceof InventoryScreen) {
            if (this.findEmptyHotbarSlot() != null && this.refillTimer.hasElapsedRandom(this.delay)) {
                Integer potionSlot = InventoryUtil.findSplashPotion(9, 35, (net.minecraft.registry.entry.RegistryEntry)(this.healthPotOnly.getValue() ? StatusEffects.INSTANT_HEALTH : null));
                if (potionSlot == null) {
                    return;
                }

                InventoryUtil.clickSlot(potionSlot, 0, SlotActionType.QUICK_MOVE);
                this.delay.reroll();
                this.refillTimer.reset();
            }
        } else {
            this.delay.reroll();
            this.refillTimer.reset();
        }
    }

    private Integer findEmptyHotbarSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.AIR) {
                return slot;
            }
        }

        return null;
    }
}

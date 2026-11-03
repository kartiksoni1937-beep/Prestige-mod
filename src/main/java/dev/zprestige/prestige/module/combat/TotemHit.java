package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

public class TotemHit extends Module {
    public TotemHit() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private static final long LAST_HOTBAR_SLOT = -2304674206878531576L;

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (target != null) {
            if (target instanceof PlayerEntity) {
                if (mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    Integer swordSlot = null;

                    for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
                        if (InventoryUtil.isSword(mc.player.getInventory().getStack(slot))) {
                            swordSlot = slot;
                            break;
                        }
                    }

                    if (swordSlot == null) {
                        return;
                    }

                    event.cancel();
                    InventoryUtil.runWithSlot(swordSlot, () -> PlayerInteractions.attackEntity(event.getTarget()), true);
                }
            }
        }
    }
}

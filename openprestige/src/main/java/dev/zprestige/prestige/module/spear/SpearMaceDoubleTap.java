package dev.zprestige.prestige.module.spear;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.hit.EntityHitResult;

public class SpearMaceDoubleTap extends Module {
    public SpearMaceDoubleTap() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting triggerFallDistance;
    private StopWatch maceSwapTimer = new StopWatch();
    private boolean pendingMaceHit = false;
    private static final long LAST_HOTBAR_SLOT = 5793104364755222536L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Spear, Gamemode.Mace);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.pendingMaceHit) {
            if (!this.maceSwapTimer.hasElapsed(1000.0F)) {
                if (!(mc.crosshairTarget instanceof EntityHitResult)) {
                    return;
                }

                PlayerInteractions.attackEntity(((EntityHitResult)mc.crosshairTarget).getEntity());
            }
        }

        this.pendingMaceHit = false;
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof EntityStatusS2CPacket) {
            if (((EntityStatusS2CPacket)event.getPacket()).getStatus() == 2) {
                mc.execute(() -> {
                    if (mc.player != null && mc.world != null) {
                        int maceSlot = this.findBestMaceSlot();
                        if (maceSlot != -1 && mc.player.fallDistance > this.triggerFallDistance.getValue().floatValue()) {
                            mc.player.stopUsingItem();
                            InventoryUtil.selectSlot(maceSlot);
                            this.maceSwapTimer.reset();
                            this.pendingMaceHit = true;
                        }
                    }
                });
            }
        }
    }

    public int findBestMaceSlot() {
        ArrayList maceSlots = new ArrayList();

        for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
            if (InventoryUtil.isMace(mc.player.getInventory().getStack(slot))) {
                maceSlots.add(slot);
            }
        }

        int bestLevel = -1;
        int bestSlot = -1;

        for (int maceSlot : (Iterable<Integer>)(maceSlots)) {
            int enchantLevel;
            if (mc.player.fallDistance <= 7.0) {
                enchantLevel = this.getBreachLevel(maceSlot);
            } else {
                enchantLevel = this.getDensityLevel(maceSlot);
            }

            int level = enchantLevel;
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = maceSlot;
            }
        }

        return bestSlot;
    }

    public int getDensityLevel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack != null) {
            float level = EnchantmentHelper.getLevel(
                mc.world.getRegistryManager().getOptional(Enchantments.DENSITY.getRegistryRef()).get().getEntry(Enchantments.DENSITY.getValue()).get(), stack
            );
            return (int)level;
        } else {
            return 0;
        }
    }

    public int getBreachLevel(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack != null) {
            float level = EnchantmentHelper.getLevel(
                mc.world.getRegistryManager().getOptional(Enchantments.BREACH.getRegistryRef()).get().getEntry(Enchantments.BREACH.getValue()).get(), stack
            );
            return (int)level;
        } else {
            return 0;
        }
    }
}

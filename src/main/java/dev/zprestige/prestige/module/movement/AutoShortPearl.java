package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.Hand;

public class AutoShortPearl extends Module implements RotationProvider {
    public AutoShortPearl() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting delay;
    private StopWatch hitTimer = new StopWatch();
    private boolean throwPending = false;
    private static final String PLAYER_ATTACK_MSG_ID = "player";

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.world != null && mc.player != null && event.getPacket() instanceof EntityDamageS2CPacket damagePacket) {
            Entity damagedEntity = mc.world.getEntityById(damagePacket.entityId());
            if (damagedEntity == null || damagedEntity != mc.player) {
                return;
            }

            String damageMsgId = damagePacket.createDamageSource(mc.world).getType().msgId();
            if (PLAYER_ATTACK_MSG_ID.equals(damageMsgId)) {
                this.throwPending = true;
                this.hitTimer.reset();
                this.delay.reroll();
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player == null) {
            return null;
        }

        if (this.throwPending) {
            if (this.hitTimer.hasElapsed(this.delay.getValue()) && mc.isWindowFocused() && mc.currentScreen == null) {
                float throwPitch = 89.0F + RandomUtil.RANDOM.nextFloat() * 2.0F;
                InventoryUtil.runWithItem(Items.ENDER_PEARL, () -> {
                    float realPitch = mc.player.getPitch();
                    mc.player.setPitch(throwPitch);
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    mc.player.setPitch(realPitch);
                }, true);
                this.throwPending = false;
                return new Rotation(mc.player.getYaw(), throwPitch);
            }
        }

        return null;
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.throwPending = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }
}

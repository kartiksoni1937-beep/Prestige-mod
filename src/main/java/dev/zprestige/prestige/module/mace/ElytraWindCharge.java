package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.Set;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class ElytraWindCharge extends Module implements RotationProvider {
    public ElytraWindCharge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private boolean equippingElytra;
    private int slotToRestore = 0;
    private StopWatch glideTimer = new StopWatch();
    private StopWatch windChargeTimer = new StopWatch();
    private static final long LAST_HOTBAR_SLOT = -1612205855334203384L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (!this.equippingElytra) {
            if (InventoryUtil.isHolding(Items.NETHERITE_CHESTPLATE)
                || InventoryUtil.isHolding(Items.DIAMOND_CHESTPLATE)) {
                this.toggle();
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            this.equippingElytra = false;
            if (this.slotToRestore != -1) {
                InventoryUtil.selectSlot(this.slotToRestore);
                this.slotToRestore = -1;
                return null;
            } else if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                if (InventoryUtil.isHolding(Items.ELYTRA)) {
                    this.equippingElytra = true;
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                } else {
                    Integer elytraSlot = InventoryUtil.findHotbarSlot(Items.ELYTRA);
                    if (elytraSlot != null) {
                        InventoryUtil.selectSlot(elytraSlot);
                    }
                }

                return null;
            } else if (mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
                return null;
            } else {
                mc.options.jumpKey.setPressed(false);
                if (!PositionUtil.isPlayerGliding()) {
                    if (!mc.player.isOnGround()) {
                        if (this.glideTimer.hasElapsed(400.0F)) {
                            PlayerInteractions.startGliding();
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
                            mc.options.jumpKey.setPressed(true);
                            this.glideTimer.reset();
                        }
                    }

                    return null;
                } else {
                    if (!this.windChargeTimer.hasElapsed(400.0F)) {
                        return null;
                    } else {
                        Vec3d velocity = mc.player.getVelocity();
                        double yawRadians = Math.toRadians(mc.player.getYaw());
                        Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize();
                        float forwardSpeed = (float)(velocity.x * forward.x + velocity.z * forward.z);
                        float minTriggerHeight = 0.4F;
                        float maxTriggerHeight = 1.2F;
                        float triggerHeight = maxTriggerHeight - (maxTriggerHeight - minTriggerHeight) * Math.max(0.0F, Math.min(1.0F, forwardSpeed));
                        if (PositionUtil.getHeightAboveGround(mc.player) > triggerHeight) {
                            return null;
                        } else {
                            float throwPitch = 87.0F + RandomUtil.RANDOM.nextFloat() * 3.0F;
                            int windChargeSlot = this.findWindChargeSlot();
                            if (windChargeSlot == -1) {
                                return null;
                            } else {
                                this.slotToRestore = InventoryUtil.getSelectedSlot();
                                InventoryUtil.runWithSlot(windChargeSlot, () -> {
                                    float previousPitch = mc.player.getPitch();
                                    mc.player.setPitch(throwPitch);
                                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                    mc.player.setPitch(previousPitch);
                                }, false);
                                this.windChargeTimer.reset();
                                return new Rotation(mc.player.getYaw(), throwPitch);
                            }
                        }
                    }
                }
            }
        } else {
            return null;
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        if (!InputUtil.isKeyPressed(mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode())) {
            mc.options.jumpKey.setPressed(false);
        }
    }

    private int findWindChargeSlot() {
        for (int slot = 0; slot <= (int)LAST_HOTBAR_SLOT; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem().equals(Items.WIND_CHARGE)) {
                return slot;
            }
        }

        return -1;
    }
}

package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.JumpKeyPresser;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ShortWindCharge extends Module implements RotationProvider {
    public ShortWindCharge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private RangeSetting minDelay;
    private BoolSetting switchBack;
    private BoolSetting autoJump;
    private FloatSetting pitch1;
    private FloatSetting smoothing;
    private StopWatch stageTimer = new StopWatch();
    private float originalPitch = 0.0F;
    private int stage = 0;
    private int previousSlot = 0;
    private boolean throwPending;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public Rotation provideRotation() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (this.throwPending) {
                if (mc.player.isOnGround() && this.autoJump.getValue()) {
                    JumpKeyPresser.pressJump();
                }

                PlayerInteractions.interactItem(Hand.MAIN_HAND);
                this.throwPending = false;
            }

            if (!this.mode.getValue().equals("Silent")) {
                return null;
            } else if (this.previousSlot != -1) {
                InventoryUtil.selectSlot(this.previousSlot);
                this.previousSlot = -1;
                this.toggle();
                return null;
            } else {
                float throwPitch = this.pitch1.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat() * 2.0F;
                int windChargeSlot = this.findWindChargeSlot();
                if (windChargeSlot == -1) {
                    return null;
                } else {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    if (mc.player.isOnGround() && this.autoJump.getValue()) {
                        JumpKeyPresser.pressJump();
                    }

                    InventoryUtil.runWithSlot(windChargeSlot, () -> {
                        float previousPitch = mc.player.getPitch();
                        mc.player.setPitch(throwPitch);
                        PlayerInteractions.interactItem(Hand.MAIN_HAND);
                        mc.player.setPitch(previousPitch);
                    }, false);
                    return new Rotation(mc.player.getYaw(), throwPitch);
                }
            }
        } else {
            return null;
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (this.mode.getValue().equals("Legit")) {
                if (this.stageTimer.hasElapsed(this.minDelay.getValue())) {
                    switch (this.stage) {
                        case 0:
                            if (isWindCharge(mc.player.getMainHandStack())) {
                                this.advanceStage();
                                return;
                            }

                            int windChargeSlot = this.findWindChargeSlot();
                            if (windChargeSlot == -1) {
                                this.toggle();
                                return;
                            }

                            this.previousSlot = InventoryUtil.getSelectedSlot();
                            InventoryUtil.selectSlot(windChargeSlot);
                            this.advanceStage();
                            break;
                        case 1:
                            if (RotationUtil.smoothRotate(new Rotation(0.0F, this.pitch1.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat() * 2.0F), this.smoothing.getValue() / 2.0F, 0.0F, 0.5F)) {
                                this.throwPending = true;
                                this.advanceStage();
                            }
                            break;
                        case 2:
                            if (this.throwPending) {
                                return;
                            }

                            if (RotationUtil.smoothRotatePitch(new Rotation(0.0F, this.originalPitch), this.smoothing.getValue() / 2.0F, 0.0F, 0.5F)) {
                                if (!this.switchBack.getValue()) {
                                    this.toggle();
                                } else {
                                    this.advanceStage();
                                }
                            }
                            break;
                        case 3:
                            if (this.switchBack.getValue()) {
                                InventoryUtil.selectSlot(this.previousSlot);
                                this.toggle();
                            }
                    }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.stageTimer.reset();
        this.originalPitch = mc.player.getPitch();
        this.stage = 0;
        this.stageTimer.reset();
        this.minDelay.reroll();
    }

    private void advanceStage() {
        this.stageTimer.reset();
        this.minDelay.reroll();
        this.stage++;
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private int findWindChargeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem().equals(Items.WIND_CHARGE)) {
                return slot;
            }
        }

        return -1;
    }

    public static boolean isWindCharge(ItemStack stack) {
        return stack.getItem().equals(Items.WIND_CHARGE);
    }
}

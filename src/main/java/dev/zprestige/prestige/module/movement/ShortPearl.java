package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
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
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.RotationUtil;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ShortPearl extends Module implements RotationProvider {
    public ShortPearl() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private RangeSetting minDelay;
    private BoolSetting switchBack;
    private FloatSetting pitch;
    private FloatSetting smoothing;
    private StopWatch stageTimer = new StopWatch();
    private float startPitch = 0.0F;
    private int stage = 0;
    private int previousSlot = 0;
    private int rotateDoneAge = -1;
    private int throwAge = -1;
    private int silentThrowAge = -1;
    private float silentThrowPitch;

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player == null) {
            return null;
        }

        if (!this.mode.getValue().equals("Silent")) {
            return null;
        } else if (this.silentThrowAge != -1) {
            if (mc.player.age <= this.silentThrowAge + 1) {
                return new Rotation(mc.player.getYaw(), this.silentThrowPitch);
            } else {
                this.silentThrowAge = -1;
                this.toggle();
                return null;
            }
        } else {
            float throwPitch = this.pitch.getValue() - 1.0F + RandomUtil.RANDOM.nextFloat() * 2.0F;
            if (!InventoryUtil.runWithItem(Items.ENDER_PEARL, () -> {
                float realPitch = mc.player.getPitch();
                mc.player.setPitch(throwPitch);
                PlayerInteractions.interactItem(Hand.MAIN_HAND);
                mc.player.setPitch(realPitch);
            }, true)) {
                return null;
            } else {
                this.silentThrowAge = mc.player.age;
                this.silentThrowPitch = throwPitch;
                return new Rotation(mc.player.getYaw(), throwPitch);
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player != null && this.mode.getValue().equals("Legit")) {
            if (this.stageTimer.hasElapsed(this.minDelay.getValue())) {
                switch (this.stage) {
                    case 0:
                        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
                            this.advanceStage();
                            return;
                        }

                        Integer pearlSlot = InventoryUtil.findHotbarSlot(Items.ENDER_PEARL);
                        if (pearlSlot == null) {
                            this.toggle();
                            return;
                        }

                        this.previousSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(pearlSlot);
                        this.advanceStage();
                        break;
                    case 1:
                        if (RotationUtil.smoothRotatePitch(new Rotation(0.0F, this.pitch.getValue()), this.smoothing.getValue() / 2.0F, 0.0F, 0.5F)) {
                            mc.player.setPitch(this.pitch.getValue());
                            this.rotateDoneAge = mc.player.age;
                            this.advanceStage();
                        }
                        break;
                    case 2:
                        if (mc.player.age >= this.rotateDoneAge + 2) {
                            PlayerInteractions.interactItem(Hand.MAIN_HAND);
                            this.throwAge = mc.player.age;
                            this.advanceStage();
                        }
                        break;
                    case 3:
                        if (mc.player.age >= this.throwAge + 2) {
                            this.advanceStage();
                        }
                        break;
                    case 4:
                        if (RotationUtil.smoothRotatePitch(new Rotation(0.0F, this.startPitch), this.smoothing.getValue() / 2.0F, 0.0F, 0.5F)) {
                            if (!this.switchBack.getValue()) {
                                this.toggle();
                            } else {
                                this.advanceStage();
                            }
                        }
                        break;
                    case 5:
                        if (this.switchBack.getValue()) {
                            InventoryUtil.selectSlot(this.previousSlot);
                            this.toggle();
                        }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.stageTimer.reset();
        this.startPitch = mc.player.getPitch();
        this.stage = 0;
        this.rotateDoneAge = -1;
        this.throwAge = -1;
        this.silentThrowAge = -1;
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
}

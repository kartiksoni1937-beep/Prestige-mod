package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

public class AutoClicker extends Module {
    private EnumSetting mode;
    private EnumSetting clickButton;
    private IntSetting interval;
    private FloatSetting minCps;
    private FloatSetting maxCps;
    private BoolSetting stopOnBlock;
    private BoolSetting weaponOnly;
    private FloatSetting reactionTime;
    private FloatSetting reactionJitter;
    private StopWatch clickTimer = new StopWatch();
    private float nextClickDelay = 200.0F;
    private float meanReactionDelay;
    private float cachedReactionTime = -1.0F;
    private int lastTargetId = -1;
    private long targetSeenTime;
    private float targetReactionDelay;

    public AutoClicker() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.interval.visibleWhen((Predicate<Integer>)ignored -> this.mode.getValue().equals("Interval"));
        this.clickButton.visibleWhen((Predicate<String>)ignored -> this.mode.getValue().equals("Interval"));
        this.reactionTime.visibleWhen((Predicate<Float>)ignored -> this.isGhostMode());
        this.maxCps.visibleWhen((Predicate<Float>)ignored -> this.isGhostMode());
        this.reactionJitter
            .visibleWhen(
                (Predicate<Float>)ignored -> this.isGhostMode() && this.reactionTime.getValue() > 0.0F
            );
        this.weaponOnly.visibleWhen((Predicate<Boolean>)ignored -> this.isGhostMode());
        this.stopOnBlock.visibleWhen((Predicate<Boolean>)ignored -> this.isGhostMode());
        this.minCps.visibleWhen((Predicate<Float>)ignored -> this.isGhostMode());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    private boolean isGhostMode() {
        return this.mode.getValue().equals("Ghost");
    }

    @Override
    public void onEnable() {
        this.updateMeanReactionDelay();
        this.lastTargetId = -1;
        this.targetReactionDelay = 0.0F;
        this.nextClickDelay = 1000.0F / RandomUtil.randomFloat(Math.max(this.minCps.getValue(), 1.0F), this.maxCps.getValue());
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen == null && mc.player != null && !Prestige.clickScheduler.processPendingClick()) {
            if (this.mode.getValue().equals("Interval")) {
                if (this.clickTimer.hasElapsed(this.interval.getValue().floatValue())) {
                    Prestige.clickScheduler.scheduleClick(this.clickButton.getValue().equals("Left") ? 0 : 1, 0.0F);
                    this.clickTimer.reset();
                }
            } else if (!mc.player.isUsingItem()) {
                if (!this.stopOnBlock.getValue() || !(mc.crosshairTarget instanceof BlockHitResult)) {
                    if (!this.weaponOnly.getValue()
                        || InventoryUtil.isSword(mc.player.getMainHandStack())
                        || mc.player.getMainHandStack().getItem() instanceof AxeItem) {
                        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                            int targetId = entityHit.getEntity().getId();
                            if (targetId != this.lastTargetId) {
                                this.lastTargetId = targetId;
                                this.targetSeenTime = System.currentTimeMillis();
                                this.targetReactionDelay = this.sampleReactionDelay();
                            }

                            if (System.currentTimeMillis() - this.targetSeenTime < (long)this.targetReactionDelay) {
                                return;
                            }
                        } else {
                            this.lastTargetId = -1;
                        }

                        if (this.clickTimer.hasElapsed(this.nextClickDelay)) {
                            Prestige.clickScheduler.scheduleClick(0, 0.0F);
                            this.nextClickDelay = 1000.0F / RandomUtil.randomFloat(Math.max(this.minCps.getValue(), 1.0F), this.maxCps.getValue());
                            this.clickTimer.reset();
                        }
                    }
                }
            }
        }
    }

    private void updateMeanReactionDelay() {
        this.cachedReactionTime = this.reactionTime.getValue();
        if (this.reactionTime.getValue() <= 0.0F) {
            this.meanReactionDelay = 0.0F;
        } else {
            this.meanReactionDelay = clamp(sampleGaussian(this.reactionTime.getValue(), 15.0F), this.reactionTime.getValue() - 50.0F, this.reactionTime.getValue() + 50.0F);
        }
    }

    private float sampleReactionDelay() {
        if (this.reactionTime.getValue() != this.cachedReactionTime) {
            this.updateMeanReactionDelay();
        }

        if (this.meanReactionDelay <= 0.0F) {
            return 0.0F;
        } else {
            float jitter = this.reactionJitter.getValue() > 0.0F ? this.reactionJitter.getValue() : 1.0F;
            return clamp(sampleGaussian(this.meanReactionDelay, jitter), 80.0F, 500.0F);
        }
    }

    private static float sampleGaussian(float mean, float stdDev) {
        float u1 = RandomUtil.randomFloat(1.0E-6F, 1.0F);
        float u2 = RandomUtil.randomFloat(0.0F, 1.0F);
        return mean + stdDev * (float)(Math.sqrt(-2.0 * Math.log(u1)) * Math.cos((Math.PI * 2) * u2));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

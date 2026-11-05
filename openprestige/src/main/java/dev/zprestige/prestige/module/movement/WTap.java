package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class WTap extends Module {
    public WTap() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting chance;
    private RangeSetting holdLength;
    private RangeSetting reaction;
    private StopWatch reactionTimer = new StopWatch();
    private StopWatch releaseTimer = new StopWatch();
    private boolean tapPending = false;
    private boolean forwardReleased = false;
    private static final long W_KEY_CODE = 5341673954434089047L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Sword, Gamemode.UHC);
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (!this.tapPending && !this.forwardReleased) {
            Entity target = event.getTarget();
            if (target != null && mc.player != null) {
                if (target instanceof PlayerEntity) {
                    if (mc.options.forwardKey.isPressed() && mc.player.isSprinting() && RandomUtil.RANDOM.nextFloat() * 100.0F <= this.chance.getValue()) {
                        this.reaction.reroll();
                        this.reactionTimer.reset();
                        this.tapPending = true;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (GLFW.glfwGetKey(mc.getWindow().getHandle(), (int)W_KEY_CODE) != 1) {
            this.forwardReleased = false;
            this.tapPending = false;
        } else {
            if (this.tapPending) {
                if (this.reactionTimer.hasElapsed(this.reaction.getValue())) {
                    mc.options.forwardKey.setPressed(false);
                    this.releaseTimer.reset();
                    this.holdLength.reroll();
                    this.forwardReleased = true;
                    this.tapPending = false;
                }
            }

            if (this.forwardReleased) {
                if (this.releaseTimer.hasElapsed(this.holdLength.getValue())) {
                    if (mc.currentScreen == null) {
                        mc.options.forwardKey.setPressed(true);
                    }

                    this.forwardReleased = false;
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.tapPending = false;
        this.forwardReleased = false;
    }

    public boolean isTapPending() {
        return this.tapPending;
    }

    public boolean isForwardReleased() {
        return this.forwardReleased;
    }
}

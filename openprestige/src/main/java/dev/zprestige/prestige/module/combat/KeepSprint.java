package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.module.movement.WTap;
import dev.zprestige.prestige.render.RandomUtil;
import org.lwjgl.glfw.GLFW;

public class KeepSprint extends Module {
    public KeepSprint() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private RangeSetting restoreDelay;
    private boolean pendingSprintReset = false;
    private int restoreTicks = -1;

    private boolean isForwardKeyDown() {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), 87) == 1;
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (mc.player != null) {
            if (event.getTarget() != null) {
                if (mc.player.isSprinting() && this.isForwardKeyDown()) {
                    this.pendingSprintReset = true;
                }
            }
        }
    }

    private boolean isWTapActive() {
        for (Module candidate : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            if (candidate.getClass().equals(WTap.class)) {
                if (!candidate.isEnabled()) {
                    return false;
                }

                WTap wTap = (WTap)candidate;
                return wTap.isTapPending() || wTap.isForwardReleased();
            }
        }

        return false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.isWTapActive()) {
            this.pendingSprintReset = false;
            this.restoreTicks = -1;
        } else if (this.restoreTicks > 0) {
            this.restoreTicks--;
        } else {
            if (this.restoreTicks == 0) {
                this.restoreTicks = -1;
                if (mc.player != null && this.isForwardKeyDown()) {
                    mc.options.forwardKey.setPressed(true);
                    mc.player.setSprinting(true);
                }
            }

            if (this.pendingSprintReset) {
                this.pendingSprintReset = false;
                if (mc.player != null) {
                    if (!mc.options.sneakKey.isPressed() && !mc.player.isSneaking()) {
                        if (this.isForwardKeyDown()) {
                            if (!mc.player.horizontalCollision) {
                                if (!mc.player.isUsingItem()) {
                                    if (mc.player.getHungerManager().getFoodLevel() > 6) {
                                        mc.options.forwardKey.setPressed(false);
                                        int minTicks = Math.round(this.restoreDelay.getLow());
                                        int maxTicks = Math.round(this.restoreDelay.getHigh());
                                        int delayTicks;
                                        if (minTicks == maxTicks) {
                                            delayTicks = minTicks;
                                        } else {
                                            delayTicks = RandomUtil.randomInt(minTicks, maxTicks);
                                        }

                                        this.restoreTicks = delayTicks;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

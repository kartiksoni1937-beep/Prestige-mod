package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.ScheduledClick;
import dev.zprestige.prestige.render.MouseAccessor;
import dev.zprestige.prestige.render.RandomUtil;

public class ClickScheduler implements MinecraftAccess {
    private ScheduledClick pendingClick = null;

    public boolean processPendingClick() {
        if (this.pendingClick != null) {
            if ((float)(System.currentTimeMillis() - this.pendingClick.getScheduledAt()) > this.pendingClick.getPressDelay()
                && !this.pendingClick.isPressed()) {
                new MouseAccessor(mc.mouse).invokeMouseButton(mc.getWindow().getHandle(), this.pendingClick.getButton(), 1, 0);
                this.pendingClick.setPressed(true);
            }

            if ((float)(System.currentTimeMillis() - this.pendingClick.getScheduledAt()) > this.pendingClick.getPressDelay()
                && (float)(System.currentTimeMillis() - this.pendingClick.getScheduledAt())
                    > this.pendingClick.getPressDelay() + this.pendingClick.getHoldDuration()) {
                new MouseAccessor(mc.mouse).invokeMouseButton(mc.getWindow().getHandle(), this.pendingClick.getButton(), 0, 0);
                this.pendingClick = null;
            }

            return true;
        } else {
            return false;
        }
    }

    public void scheduleClick(int button, float delayMs) {
        this.pendingClick = new ScheduledClick(button, System.currentTimeMillis(), delayMs, RandomUtil.randomFloat(RandomUtil.randomFloat(5.0F, 15.0F), RandomUtil.randomFloat(35.0F, 55.0F)), false);
    }
}

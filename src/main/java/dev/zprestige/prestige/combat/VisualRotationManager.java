package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PlayerRenderStateEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.type.EventPhase;
import net.minecraft.util.math.MathHelper;

public class VisualRotationManager implements MinecraftAccess {
    private boolean initialized;
    private float renderYaw;
    private float renderPitch;
    private float prevRenderYaw;
    private float prevRenderPitch;
    private boolean applied;
    private float savedYaw;
    private float savedPitch;
    private float savedBodyYaw;
    private float savedHeadYaw;
    private float savedLastYaw;
    private float savedLastPitch;
    private float savedLastBodyYaw;
    private float savedLastHeadYaw;

    public VisualRotationManager() {
        super();
        this.initialized = false;
        this.applied = false;
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null) {
            this.initialized = false;
        } else if (!this.initialized) {
            this.renderYaw = mc.player.getYaw();
            this.renderPitch = mc.player.getPitch();
            this.prevRenderYaw = this.renderYaw;
            this.prevRenderPitch = this.renderPitch;
            this.initialized = true;
        } else {
            Rotation targetRotation = Prestige.silentRotationManager.getServerRotation();
            if (targetRotation == null) {
                this.renderYaw = mc.player.getYaw();
                this.renderPitch = mc.player.getPitch();
                this.prevRenderYaw = this.renderYaw;
                this.prevRenderPitch = this.renderPitch;
            } else {
                this.prevRenderYaw = this.renderYaw;
                this.prevRenderPitch = this.renderPitch;
                float yawDelta = MathHelper.wrapDegrees(targetRotation.getYaw() - this.renderYaw);
                float pitchDelta = targetRotation.getPitch() - this.renderPitch;
                this.renderYaw = MathHelper.wrapDegrees(this.renderYaw + yawDelta * 0.4F);
                this.renderPitch = MathHelper.clamp(this.renderPitch + pitchDelta * 0.4F, -90.0F, 90.0F);
            }
        }
    }

    @Subscribe
    public void onPlayerRenderState(PlayerRenderStateEvent event) {
        if (event.entity == mc.player && this.initialized) {
            if (event.getPhase() == EventPhase.PRE) {
                if (this.applied) {
                    return;
                }

                if (Prestige.silentRotationManager.getServerRotation() == null) {
                    return;
                }

                float yawDiff = Math.abs(MathHelper.wrapDegrees(this.renderYaw - mc.player.getYaw()));
                float pitchDiff = Math.abs(this.renderPitch - mc.player.getPitch());
                if (yawDiff < 0.5F && pitchDiff < 0.5F) {
                    return;
                }

                this.savedYaw = mc.player.getYaw();
                this.savedPitch = mc.player.getPitch();
                this.savedBodyYaw = mc.player.bodyYaw;
                this.savedHeadYaw = mc.player.headYaw;
                this.savedLastYaw = mc.player.lastYaw;
                this.savedLastPitch = mc.player.lastPitch;
                this.savedLastBodyYaw = mc.player.lastBodyYaw;
                this.savedLastHeadYaw = mc.player.lastHeadYaw;
                mc.player.setYaw(this.renderYaw);
                mc.player.setPitch(this.renderPitch);
                mc.player.bodyYaw = this.renderYaw;
                mc.player.headYaw = this.renderYaw;
                mc.player.lastYaw = this.prevRenderYaw;
                mc.player.lastPitch = this.prevRenderPitch;
                mc.player.lastBodyYaw = this.prevRenderYaw;
                mc.player.lastHeadYaw = this.prevRenderYaw;
                this.applied = true;
            } else if (event.getPhase() == EventPhase.POST && this.applied) {
                mc.player.setYaw(this.savedYaw);
                mc.player.setPitch(this.savedPitch);
                mc.player.bodyYaw = this.savedBodyYaw;
                mc.player.headYaw = this.savedHeadYaw;
                mc.player.lastYaw = this.savedLastYaw;
                mc.player.lastPitch = this.savedLastPitch;
                mc.player.lastBodyYaw = this.savedLastBodyYaw;
                mc.player.lastHeadYaw = this.savedLastHeadYaw;
                this.applied = false;
            }
        }
    }

    public boolean isRotationApplied() {
        return this.applied;
    }
}

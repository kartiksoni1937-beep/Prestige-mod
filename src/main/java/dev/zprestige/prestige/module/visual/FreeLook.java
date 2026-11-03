package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.MouseUpdateEvent;
import dev.zprestige.prestige.event.CameraRotationEvent;
import net.minecraft.client.option.Perspective;

public class FreeLook extends Module {
    public FreeLook() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting thirdPerson;
    private float cameraYaw = 0.0F;
    private float cameraPitch = 0.0F;
    private Perspective previousPerspective = null;

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Subscribe
    public void onMouseUpdate(MouseUpdateEvent event) {
        this.cameraYaw = (float)(this.cameraYaw + event.getDeltaX() * 0.1F);
        this.cameraPitch = (float)(this.cameraPitch + event.getDeltaY() * 0.1F);
        this.cameraPitch = Math.max(-90.0F, Math.min(90.0F, this.cameraPitch));
        event.cancel();
    }

    @Subscribe
    public void onCameraRotation(CameraRotationEvent event) {
        event.setYaw(this.cameraYaw);
        event.setPitch(this.cameraPitch);
        event.cancel();
    }

    @Override
    public void onEnable() {
        this.cameraYaw = mc.player.getYaw();
        this.cameraPitch = mc.player.getPitch();
        this.previousPerspective = mc.options.getPerspective();
        if (this.thirdPerson.getValue()) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        if (this.previousPerspective != null) {
            mc.options.setPerspective(this.previousPerspective);
            this.previousPerspective = null;
        }
    }
}

package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.module.combat.AutoElytra;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;

public class AutoReMace extends Module {
    public AutoReMace() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting rotationSmoothing;
    private FloatSetting aboveThem;
    private StopWatch reMaceTimer = new StopWatch();
    private boolean reMacing;
    private PlayerEntity target;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (this.reMacing) {
            if (this.target != null && this.target.isAlive() && !(mc.player.distanceTo(this.target) > 100.0F)) {
                label67: {
                    if (this.reMaceTimer.hasElapsed(1000.0F)) {
                        if (mc.player.isOnGround()) {
                            break label67;
                        }

                        if (this.reMaceTimer.hasElapsed(5000.0F)) {
                            break label67;
                        }
                    }

                    if (!this.reMaceTimer.hasElapsed(100.0F)) {
                        return;
                    }

                    if (!this.reMaceTimer.hasElapsed(600.0F)) {
                        if (!AutoElytra.instance.isEnabled()) {
                            AutoElytra.instance.toggle();
                        }

                        RotationUtil.smoothRotate(RotationUtil.getRotationTo(
                            PositionUtil.getEntityPos(this.target).add(0.0, this.aboveThem.getValue().floatValue(), 0.0)
                        ), this.rotationSmoothing.getValue() / 2.0F, 0.0F, 0.5F);
                        return;
                    }

                    if (!this.reMaceTimer.hasElapsed(1400.0F)) {
                        RotationUtil.smoothRotate(RotationUtil.getRotationTo(this.target.getEyePos()), this.rotationSmoothing.getValue() / 2.0F, 0.0F, 0.5F);
                        return;
                    }

                    if (PositionUtil.getHeightAboveGround(mc.player) < 1.0) {
                        this.reMacing = false;
                    }

                    return;
                }

                this.reMacing = false;
            } else {
                this.reMacing = false;
            }
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof PlayerEntity attackedPlayer && InventoryUtil.isMace(mc.player.getMainHandStack())) {
            this.target = attackedPlayer;
            this.reMacing = true;
            this.reMaceTimer.reset();
        }
    }
}

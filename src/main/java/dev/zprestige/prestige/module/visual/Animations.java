package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.SwingArmEvent;
import dev.zprestige.prestige.event.HandSwingDurationEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.HeldItemRendererAccessor;
import dev.zprestige.prestige.inventory.InventoryUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class Animations extends Module {
    public Animations() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private IntSetting mode;
    private BoolSetting slow;
    private static final long SLOW_SWING_DURATION = 5714003492503289866L;

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null) {
            return;
        }

        HeldItemRendererAccessor heldItemRenderer = new HeldItemRendererAccessor(mc.getEntityRenderDispatcher().getHeldItemRenderer());
        if (heldItemRenderer.getMainHandEquipProgress() <= 1.0F) {
            heldItemRenderer.setMainHandEquipProgress(1.0F);
            heldItemRenderer.setMainHandStack(mc.player.getMainHandStack());
        }

        if (heldItemRenderer.getOffHandEquipProgress() <= 1.0F) {
            heldItemRenderer.setOffHandEquipProgress(1.0F);
            heldItemRenderer.setOffHandStack(mc.player.getOffHandStack());
        }
    }

    @Subscribe
    public void onHandSwingDuration(HandSwingDurationEvent event) {
        if (this.slow.getValue()) {
            event.duration = (int)SLOW_SWING_DURATION;
            event.cancel();
        }
    }

    @Subscribe
    public void onSwingArm(SwingArmEvent event) {
        MatrixStack matrices = event.getMatrices();
        Arm arm = event.getArm();
        float swingProgress = event.getSwingProgress();
        int side = arm == Arm.RIGHT ? 1 : -1;
        switch (this.mode.getValue()) {
            case 1:
                this.applyEquipOffset(matrices, arm, 0.0F);
                float swingSin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + swingSin * -20.0F)));
                float swingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * swingRootSin * -10.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingRootSin * -80.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                break;
            case 2:
                this.applyEquipOffset(matrices, arm, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
                float mildSwingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * mildSwingRootSin * -10.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mildSwingRootSin * -40.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                break;
            case 3:
                this.applyEquipOffset(matrices, arm, 0.0F);
                if (arm == Arm.RIGHT && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    matrices.translate(0.0F, 0.1F, -0.2F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 65.0F));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(side * -100.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 20.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -220.0F));
                    float blockSwingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * blockSwingRootSin * -40.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                } else {
                    float fallbackSwingSin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + fallbackSwingSin * -20.0F)));
                    float fallbackSwingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * fallbackSwingRootSin * -20.0F));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(fallbackSwingRootSin * -80.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                }
        }

        event.cancel();
    }

    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        matrices.translate((arm == Arm.RIGHT ? 1 : -1) * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }
}

package dev.zprestige.prestige.event;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

public class SwingArmEvent extends Event {
    private final MatrixStack matrices;
    private final Arm arm;
    private final float swingProgress;
    private final float equipProgress;

    public SwingArmEvent(MatrixStack matrixStack, Arm swingingArm, float initialSwingProgress, float initialEquipProgress) {
        this.matrices = matrixStack;
        this.arm = swingingArm;
        this.swingProgress = initialSwingProgress;
        this.equipProgress = initialEquipProgress;
    }

    public MatrixStack getMatrices() {
        return this.matrices;
    }

    public Arm getArm() {
        return this.arm;
    }

    public float getSwingProgress() {
        return this.swingProgress;
    }

    public float getEquipProgress() {
        return this.equipProgress;
    }
}

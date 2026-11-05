package dev.zprestige.prestige.math;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.module.combat.AutoCrystal;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.ShapeRenderer;
import java.awt.Color;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AutoCrystalRenderer {
    private AutoCrystal module;
    private BlockPos targetPos;
    private Box renderBox;
    private float alpha = 0.0F;
    public long frameDelta = 0L;
    public long lastFrameTime;

    public AutoCrystalRenderer(AutoCrystal autoCrystal) {
        this.module = autoCrystal;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.module.render.getValue()) {
            this.frameDelta = System.currentTimeMillis() - this.lastFrameTime;
            this.lastFrameTime = System.currentTimeMillis();
            float step = (float)this.frameDelta * 0.005F;
            if (!(step > 1.0F)) {
                if (this.targetPos != null) {
                    if (this.module.isEnabled()
                        && MinecraftAccess.mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                        && MinecraftAccess.mc.crosshairTarget != null
                        && MinecraftAccess.mc.crosshairTarget.getType() == Type.BLOCK) {
                        this.alpha = UiMath.approach(this.alpha, this.module.color.getValue().getAlpha() / 255.0F, step);
                    } else {
                        this.alpha = UiMath.approach(this.alpha, 0.0F, step);
                    }

                    if (this.alpha <= 0.01F) {
                        this.targetPos = null;
                        return;
                    }

                    if (this.renderBox == null) {
                        this.renderBox = new Box(this.targetPos);
                    }

                    this.renderBox = this.renderBox.offset((this.targetPos.getX() - this.renderBox.minX) * 0.05F, (this.targetPos.getY() - this.renderBox.minY) * 0.05F, (this.targetPos.getZ() - this.renderBox.minZ) * 0.05F);
                    Color fillColor = new Color(
                        this.module.color.getValue().getRed(),
                        this.module.color.getValue().getGreen(),
                        this.module.color.getValue().getBlue(),
                        (int)(this.alpha / 4.0F * 255.0F)
                    );
                    Color outlineColor = new Color(
                        this.module.color.getValue().getRed(),
                        this.module.color.getValue().getGreen(),
                        this.module.color.getValue().getBlue(),
                        (int)(this.alpha * 255.0F)
                    );
                    ShapeRenderer.drawFilledBox(event.context, event.renderer, (float)this.renderBox.minX, (float)this.renderBox.minY, (float)this.renderBox.minZ, (float)this.renderBox.maxX, (float)this.renderBox.maxY, (float)this.renderBox.maxZ, fillColor);
                    ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)this.renderBox.minX, (float)this.renderBox.minY, (float)this.renderBox.minZ, (float)this.renderBox.maxX, (float)this.renderBox.maxY, (float)this.renderBox.maxZ, outlineColor);
                }
            }
        }
    }

    public void setTargetPos(BlockPos pos) {
        this.targetPos = pos;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public void reset() {
        this.targetPos = null;
        this.renderBox = null;
        this.alpha = 0.0F;
        this.frameDelta = 0L;
        this.lastFrameTime = System.currentTimeMillis();
    }
}

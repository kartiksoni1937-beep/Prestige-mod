package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MacePrediction extends Module {
    private EnumSetting mode;
    private BoolSetting predictAirPath;
    private IntSetting maxAirPredTicks;
    private ColorSetting airPathColor;
    private ColorSetting airLandingColor;
    private BoolSetting predictGroundPath;
    private IntSetting groundPredTicks;
    private ColorSetting groundPathColor;
    private BoolSetting rangeCircles;
    private ColorSetting circleInner;
    private ColorSetting circleOuter;
    private FloatSetting circleAlpha;

    public MacePrediction() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.groundPathColor.visibleWhen((Predicate<Color>)ignoredGroundPathColor -> this.predictGroundPath.getValue());
        this.circleInner.visibleWhen((Predicate<Color>)ignoredCircleInner -> this.rangeCircles.getValue());
        this.airLandingColor.visibleWhen((Predicate<Color>)ignoredAirLandingColor -> this.predictAirPath.getValue());
        this.maxAirPredTicks.visibleWhen((Predicate<Integer>)ignoredMaxAirPredTicks -> this.predictAirPath.getValue());
        this.circleAlpha.visibleWhen((Predicate<Float>)ignoredCircleAlpha -> this.rangeCircles.getValue());
        this.airPathColor.visibleWhen((Predicate<Color>)ignoredAirPathColor -> this.predictAirPath.getValue());
        this.circleOuter.visibleWhen((Predicate<Color>)ignoredCircleOuter -> this.rangeCircles.getValue());
        this.groundPredTicks.visibleWhen((Predicate<Integer>)ignoredGroundPredTicks -> this.predictGroundPath.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        PlayerEntity target = TargetSelector.getClosestEnemy();
        if (target != null) {
            if (PositionUtil.getHeightAboveGround(target) < 0.3F) {
                if (!this.predictGroundPath.getValue()) {
                    return;
                }

                Vec3d groundPos = PositionUtil.getEntityPos(target);
                if (this.rangeCircles.getValue()) {
                    ShapeRenderer.drawDisc(event.context, event.renderer, (float)groundPos.x, (float)groundPos.y, (float)groundPos.z, 3.0F, UiMath.withAlpha(this.circleInner.getValue(), 0.8F), UiMath.withAlpha(this.circleOuter.getValue(), 0.2F * (this.circleAlpha.getValue() / 255.0F)));
                }

                this.drawGroundPrediction(target, event);
            } else {
                if (!this.predictAirPath.getValue()) {
                    return;
                }

                Vec3d landingPos = this.traceAirPath(target, event);
                if (landingPos != null && this.rangeCircles.getValue()) {
                    ShapeRenderer.drawDisc(event.context, event.renderer, (float)landingPos.x, (float)landingPos.y, (float)landingPos.z, 3.0F, UiMath.withAlpha(this.circleInner.getValue(), 0.8F), UiMath.withAlpha(this.circleOuter.getValue(), 0.2F * (this.circleAlpha.getValue() / 255.0F)));
                }
            }
        }
    }

    private Vec3d traceAirPath(Entity entity, Render3DEvent event) {
        Vec3d pos = PositionUtil.getEntityPos(entity);
        Vec3d velocity = entity.getVelocity();
        Vec3d previousPos = pos;
        Vec3d lastMarkerPos = null;

        for (int tick = 0; tick < this.maxAirPredTicks.getValue(); tick++) {
            previousPos = pos;
            pos = pos.add(velocity);
            BlockPos belowPos = new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y - 0.1), (int)Math.floor(pos.z));
            if (!entity.getEntityWorld().getBlockState(belowPos).isAir() || pos.y <= entity.getEntityWorld().getBottomY()) {
                this.drawMarker(previousPos, this.airLandingColor.getValue(), event);
                return new Vec3d(previousPos.x, belowPos.getY() + 1, previousPos.z);
            }

            if (lastMarkerPos == null || lastMarkerPos.squaredDistanceTo(pos) >= 0.1) {
                this.drawMarker(pos, this.airPathColor.getValue(), event);
                lastMarkerPos = pos;
            }

            velocity = velocity.subtract(0.0, 0.08, 0.0);
            velocity = velocity.multiply(0.98, 0.98, 0.98);
        }

        return previousPos;
    }

    private void drawGroundPrediction(PlayerEntity player, Render3DEvent event) {
        Vec3d currentPos = PositionUtil.getEntityPos(player);
        Vec3d lastPos = PositionUtil.getLastPos(player);
        this.traceGroundPath(player, currentPos, new Vec3d(currentPos.x - lastPos.x, currentPos.y, currentPos.z - lastPos.z), event);
    }

    private void traceGroundPath(PlayerEntity player, Vec3d startPos, Vec3d startVelocity, Render3DEvent event) {
        Vec3d pos = startPos;
        Vec3d velocity = startVelocity;
        Vec3d lastMarkerPos = null;

        for (int tick = 0; tick < this.groundPredTicks.getValue(); tick++) {
            pos = pos.add(velocity.x, 0.0, velocity.z);
            BlockPos belowPos = new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y - 0.1), (int)Math.floor(pos.z));
            if (player.getEntityWorld() != null && player.getEntityWorld().getBlockState(belowPos).isAir()) {
                break;
            }

            velocity = velocity.multiply(0.999, 1.0, 0.999);
            if (Math.abs(velocity.x) < 0.005 && Math.abs(velocity.z) < 0.005) {
                break;
            }

            if (lastMarkerPos == null || lastMarkerPos.squaredDistanceTo(pos) >= 0.1) {
                this.drawMarker(pos, this.groundPathColor.getValue(), event);
                lastMarkerPos = pos;
            }
        }
    }

    public void drawMarker(Vec3d pos, Color color, Render3DEvent event) {
        switch (this.mode.getValue()) {
            case "Box":
                ShapeRenderer.drawFilledBox(event.context, event.renderer, (float)pos.x - 0.25F, (float)pos.y, (float)pos.z - 0.25F, (float)pos.x + 0.25F, (float)pos.y + 2.0F, (float)pos.z + 0.25F, color);
                break;
            case "Outline":
                ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)pos.x - 0.25F, (float)pos.y, (float)pos.z - 0.25F, (float)pos.x + 0.25F, (float)pos.y + 2.0F, (float)pos.z + 0.25F, color);
                break;
            case "Both":
                ShapeRenderer.drawFilledBox(event.context, event.renderer, (float)pos.x - 0.25F, (float)pos.y, (float)pos.z - 0.25F, (float)pos.x + 0.25F, (float)pos.y + 2.0F, (float)pos.z + 0.25F, UiMath.withAlpha(color, color.getAlpha() / 255.0F * 0.5F));
                ShapeRenderer.drawBoxOutline(event.context, event.renderer, (float)pos.x - 0.25F, (float)pos.y, (float)pos.z - 0.25F, (float)pos.x + 0.25F, (float)pos.y + 2.0F, (float)pos.z + 0.25F, color);
        }
    }
}

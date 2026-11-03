package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.CameraPositionEvent;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.PositionUtil;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class Freecam extends Module {
    public Freecam() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private FloatSetting speed;
    private BoolSetting renderPrevious;
    private Vec3d cameraPos = Vec3d.ZERO;
    private Vec3d prevCameraPos = Vec3d.ZERO;
    private Vec3d frozenPos = Vec3d.ZERO;
    private float frozenYaw = 0.0F;
    private float frozenPitch = 0.0F;
    private Vec3d savedVelocity = Vec3d.ZERO;

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            this.cameraPos = mc.player.getEyePos();
            this.prevCameraPos = this.cameraPos;
            this.frozenPos = PositionUtil.getPlayerPos();
            this.frozenYaw = mc.player.getYaw();
            this.frozenPitch = mc.player.getPitch();
            this.savedVelocity = mc.player.getVelocity();
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setVelocity(this.savedVelocity);
        }

        this.savedVelocity = Vec3d.ZERO;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player != null && mc.currentScreen == null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.player.setSneaking(false);
            float degToRad = (float) (Math.PI / 180.0);
            float pi = (float) Math.PI;
            float cosYaw = MathHelper.cos(-mc.player.getYaw() * degToRad - pi);
            Vec3d forward = new Vec3d(-MathHelper.sin(-mc.player.getYaw() * degToRad - pi), 0.0, -cosYaw);
            Vec3d up = new Vec3d(0.0, 1.0, 0.0);
            Vec3d left = up.crossProduct(forward);
            Vec3d right = forward.crossProduct(up);
            Vec3d motion = Vec3d.ZERO;
            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.forwardKey).getBoundKey().getCode()) == 1) {
                motion = motion.add(forward);
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.backKey).getBoundKey().getCode()) == 1) {
                motion = motion.subtract(forward);
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.leftKey).getBoundKey().getCode()) == 1) {
                motion = motion.add(left);
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.rightKey).getBoundKey().getCode()) == 1) {
                motion = motion.add(right);
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.jumpKey).getBoundKey().getCode()) == 1) {
                motion = motion.add(0.0, this.speed.getValue().floatValue(), 0.0);
            }

            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.sneakKey).getBoundKey().getCode()) == 1) {
                motion = motion.add(0.0, -this.speed.getValue(), 0.0);
            }

            motion = motion.normalize()
                .multiply(
                    this.speed.getValue()
                        * (
                            GLFW.glfwGetKey(mc.getWindow().getHandle(), new KeyBindingAccessor(mc.options.sprintKey).getBoundKey().getCode())
                                    == 1
                                ? 2.0F
                                : 1.0F
                        )
                );
            this.prevCameraPos = this.cameraPos;
            this.cameraPos = this.cameraPos.add(motion);
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (event.getPhase() == EventPhase.PRE) {
            if (mc.player != null) {
                mc.player.setPosition(this.frozenPos.x, this.frozenPos.y, this.frozenPos.z);
                mc.player.setVelocity(Vec3d.ZERO);
                event.setYaw(this.frozenYaw);
                event.setPitch(this.frozenPitch);
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof ClientCommandC2SPacket commandPacket) {
            Mode commandMode = commandPacket.getMode();
            if (commandMode == Mode.START_SPRINTING || commandMode == Mode.STOP_SPRINTING) {
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onCameraPosition(CameraPositionEvent event) {
        float tickDelta = Render2D.getTickDelta();
        event.setX((double)MathHelper.lerp(tickDelta, (float)this.prevCameraPos.x, (float)this.cameraPos.x));
        event.setY((double)MathHelper.lerp(tickDelta, (float)this.prevCameraPos.y, (float)this.cameraPos.y));
        event.setZ((double)MathHelper.lerp(tickDelta, (float)this.prevCameraPos.z, (float)this.cameraPos.z));
        event.cancel();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.renderPrevious.getValue() && mc.player != null) {
            float bodyX = (float)this.frozenPos.x;
            float bodyY = (float)this.frozenPos.y;
            float bodyZ = (float)this.frozenPos.z;
            ShapeRenderer.drawFilledBox(event.context, event.renderer, bodyX - 0.3F, bodyY, bodyZ - 0.3F, bodyX + 0.3F, bodyY + mc.player.getHeight(), bodyZ + 0.3F, UiMath.withAlpha(UiMath.getAccentColor(), 0.1F));
            ShapeRenderer.drawBoxOutline(event.context, event.renderer, bodyX - 0.3F, bodyY, bodyZ - 0.3F, bodyX + 0.3F, bodyY + mc.player.getHeight(), bodyZ + 0.3F, UiMath.getAccentColor());
        }
    }
}

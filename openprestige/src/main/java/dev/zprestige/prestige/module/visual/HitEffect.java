package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.HitParticle;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.module.misc.CrystalOptimizer;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import dev.zprestige.prestige.type.AnimationMode;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class HitEffect extends Module {
    public HitEffect() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private IntSetting amount;
    private IntSetting lifespan;
    private BoolSetting physics;
    private BoolSetting totemPop;
    private static GlTextureHandle glowTexture;
    private static GlTextureHandle popTexture;
    private ArrayList particles = new ArrayList();
    private StopWatch frameTimer = new StopWatch();

    @Override
    public void onEnable() {
        for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            if (module.getClass().equals(CrystalOptimizer.class) && module.isEnabled()) {
                module.toggle();
                module.toggle();
            }
        }
    }

    @Override
    public void onDisable() {
        this.particles.clear();
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }

        for (int particleIndex = 0; particleIndex < this.amount.getValue(); particleIndex++) {
            if (this.particles.size() <= this.lifespan.getValue() * 20) {
                this.particles
                    .add(
                        new HitParticle(
                            PositionUtil.getEntityPos(target),
                            new Vec3d(
                                target.getX() + (Math.random() - 0.5) * 0.5, target.getY() + Math.random() * 1.0 + 0.5, target.getZ() + (Math.random() - 0.5) * 0.5
                            ),
                            AnimationMode.LEGACY
                        )
                    );
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) {
            this.particles.clear();
            this.frameTimer.reset();
            return;
        }

        if (this.particles.isEmpty()) {
            this.frameTimer.reset();
        } else {
            boolean physicsEnabled = this.physics.getValue();
            long steps = (long)Math.min(this.frameTimer.getElapsed(), 20.0F);

            for (long step = 0L; step <= steps; step++) {
                for (HitParticle particle : (Iterable<HitParticle>)(this.particles)) {
                    particle.update(physicsEnabled);
                }
            }

            this.particles.removeIf(candidate -> {
                if (!(Math.sqrt(mc.player.squaredDistanceTo(((dev.zprestige.prestige.math.HitParticle)candidate).getPos())) > 20.0)) {
                    StopWatch ageTimer = ((dev.zprestige.prestige.math.HitParticle)candidate).getAgeTimer();
                    float lifespanMs = this.lifespan.getValue() * 1000;
                    if (!ageTimer.hasElapsed(lifespanMs)) {
                        return false;
                    }
                }

                return true;
            });
            this.frameTimer.reset();
            this.renderParticles(
                event,
                    this.particles,
                    UiMath.getAccentColor().brighter(),
                    UiMath.getAccentColor().darker()
            );
        }
    }

    public void renderParticles(Render3DEvent event, ArrayList particleList, Color brightColor, Color darkColor) {
        mc.getEntityRenderDispatcher();
        Render2D.getCameraEntityPos();
        int index = 0;

        for (HitParticle particle : (Iterable<HitParticle>)(new ArrayList(particleList))) {
            index++;
            Vec3d particlePos = particle.getPos();
            double distance = PositionUtil.getPlayerPos().distanceTo(particlePos);
            if (mc.world.getBlockState(BlockPos.ofFloored(particlePos)).isAir() && (index % 10 == 0 || !(distance > 25.0)) && (index % 3 != 0 || !(distance > 15.0))) {
                int alpha = (int)(255.0F * (1.0F - particle.getLifeProgress(this.lifespan.getValue().intValue() * 1000.0F)));
                alpha = MathHelper.clamp(alpha, 0, 255);
                Color waveColor = getWaveColor(index / 5.0F, 0.5F, brightColor, darkColor);
                renderParticle(event, particle, new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), alpha), particle.mode.equals(AnimationMode.POP) ? 7.0F : 4.5F);
            }
        }
    }

    public static void renderParticle(Render3DEvent event, HitParticle particle, Color tint, float quadSize) {
        if (glowTexture == null) {
            glowTexture = TextureLoader.loadTexture("assets/prestige/particles/glow.png");
        }

        if (popTexture == null) {
            popTexture = TextureLoader.loadTexture("assets/prestige/particles/pop.png");
        }

        Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, particle.pos.x, particle.pos.y, particle.pos.z);
        GlTextureHandle texture = particle.mode.equals(AnimationMode.POP) ? popTexture : glowTexture;
        Matrix4f matrix = new Matrix4f();
        matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
        matrix.scale(0.04F);
        matrix.rotateY((float)Math.toRadians(-event.context.camera().getYaw()));
        matrix.rotateX((float)Math.toRadians(event.context.camera().getPitch()));
        RoundedTextureShader.drawWorldTexture(event.renderer, event.context, matrix, texture, 0.0F, 0.0F, 0.0F, quadSize, quadSize, 0.0F, tint, 0.0F);
    }

    public static Color getWaveColor(float phaseOffset, float speed, Color firstColor, Color secondColor) {
        Color fallback = Color.BLACK;
        if (firstColor != null && secondColor != null) {
            return blendColors(firstColor, secondColor, (Math.sin(System.currentTimeMillis() / 1.0E8 * speed * 400000.0 + phaseOffset * 0.55F) + 1.0) * 0.5);
        } else {
            return fallback;
        }
    }

    public static Color blendColors(Color first, Color second, double ratio) {
        double inverseRatio = 1.0 - ratio;
        int red = (int)(first.getRed() * ratio + second.getRed() * inverseRatio);
        int green = (int)(first.getGreen() * ratio + second.getGreen() * inverseRatio);
        int blue = (int)(first.getBlue() * ratio + second.getBlue() * inverseRatio);
        if (red < 0 || red > 255) {
            red = Math.max(0, Math.min(255, red));
        }

        if (green < 0 || green > 255) {
            green = Math.max(0, Math.min(255, green));
        }

        if (blue < 0 || blue > 255) {
            blue = Math.max(0, Math.min(255, blue));
        }

        return new Color(red, green, blue);
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        // Packets are handled on the network thread; particles are used on the main thread.
        if (this.totemPop.getValue()) {
            if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket && statusPacket.getStatus() == 35) {
                mc.execute(() -> {
                    if (mc.world == null || !(statusPacket.getEntity(mc.world) instanceof PlayerEntity player)) {
                        return;
                    }

                    for (int particleIndex = 0; particleIndex < this.amount.getValue() * 2; particleIndex++) {
                        if (this.particles.size() <= this.lifespan.getValue() * 20) {
                            this.particles
                                .add(
                                    new HitParticle(
                                        PositionUtil.getEntityPos(player),
                                        new Vec3d(
                                            player.getX() + (Math.random() - 0.5) * 0.5,
                                            player.getY() + Math.random() * 1.0 + 0.5,
                                            player.getZ() + (Math.random() - 0.5) * 0.5
                                        ),
                                        AnimationMode.POP
                                    )
                                );
                        }
                    }
                });
            }
        }
    }
}

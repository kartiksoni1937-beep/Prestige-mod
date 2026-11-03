package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.math.UiMath;
import dev.zprestige.prestige.math.WeatherParticle;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.TextureLoader;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap.Type;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class Weather extends Module {
    private EnumSetting mode;
    private IntSetting density;
    private IntSetting radius;
    private FloatSetting speed;
    private FloatSetting size;
    private BoolSetting themeColor;
    private ColorSetting color;
    private static GlTextureHandle glowTexture;
    private ArrayList particles = new ArrayList();
    private StopWatch frameTimer = new StopWatch();
    private Vec3d lastCenter = null;

    public Weather() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.color.visibleWhen((Predicate<Color>)ignored -> !this.themeColor.getValue());
    }

    @Override
    public void onDisable() {
        this.particles.clear();
        this.frameTimer.reset();
        this.lastCenter = null;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player != null && mc.world != null) {
            float elapsedTicks = Math.min(this.frameTimer.getElapsed() / 50.0F, 4.0F);
            this.frameTimer.reset();
            String currentMode = this.mode.getValue();
            int maxParticles = this.density.getValue();
            int spawnRadius = this.radius.getValue();
            Vec3d playerPos = PositionUtil.getPlayerPos();
            boolean needsRefill = this.particles.isEmpty() || this.lastCenter != null && this.lastCenter.squaredDistanceTo(playerPos) > 256.0;
            this.lastCenter = playerPos;
            if (needsRefill) {
                int attempts = 0;

                for (int maxAttempts = maxParticles * 6; this.particles.size() < maxParticles && attempts < maxAttempts; attempts++) {
                    WeatherParticle spawned = this.createParticle(currentMode, playerPos, spawnRadius);
                    if (spawned != null) {
                        this.particles.add(spawned);
                    }
                }
            } else {
                int spawnBudget = (int)Math.ceil(elapsedTicks * Math.max(1, maxParticles / 20));

                for (int spawnAttempt = 0; this.particles.size() < maxParticles && spawnAttempt < spawnBudget; spawnAttempt++) {
                    WeatherParticle newParticle = this.createParticle(currentMode, playerPos, spawnRadius);
                    if (newParticle != null) {
                        this.particles.add(newParticle);
                    }
                }
            }

            Iterator iterator = this.particles.iterator();

            while (iterator.hasNext()) {
                WeatherParticle particle = (WeatherParticle)iterator.next();
                particle.advance(elapsedTicks * this.speed.getValue());
                if (particle.age > particle.maxAge) {
                    iterator.remove();
                } else if (particle.pos.squaredDistanceTo(playerPos) > spawnRadius * 1.6 * (spawnRadius * 1.6)) {
                    iterator.remove();
                } else {
                    BlockState blockState = mc.world.getBlockState(BlockPos.ofFloored(particle.pos));
                    if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getFluidState().isEmpty()) {
                        iterator.remove();
                    }
                }
            }

            this.renderParticles(event, currentMode);
        }
    }

    private WeatherParticle createParticle(String weatherMode, Vec3d center, int spawnRadius) {
        double angle = Math.random() * Math.PI * 2.0;
        double distance = Math.sqrt(Math.random()) * spawnRadius;
        double x = center.x + Math.cos(angle) * distance;
        double z = center.z + Math.sin(angle) * distance;
        double y;
        Vec3d velocity;
        short maxAge;
        switch (weatherMode) {
            case "Rain":
                y = center.y - 4.0 + Math.random() * 28.0;
                velocity = new Vec3d(0.0, -0.45, 0.0);
                maxAge = 200;
                break;
            case "Snow":
                y = center.y - 3.0 + Math.random() * 23.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.02, -0.06, (Math.random() - 0.5) * 0.02);
                maxAge = 600;
                break;
            case "Fireflies":
                y = center.y - 1.0 + Math.random() * 6.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.015, (Math.random() - 0.5) * 0.01, (Math.random() - 0.5) * 0.015);
                maxAge = 400;
                break;
            case "Embers":
                y = center.y - 4.0 + Math.random() * 12.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.01, 0.05 + Math.random() * 0.02, (Math.random() - 0.5) * 0.01);
                maxAge = 300;
                break;
            default:
                return null;
        }

        Vec3d spawnPos = new Vec3d(x, y, z);
        if (weatherMode.equals("Rain") || weatherMode.equals("Snow")) {
            if (y < mc.world.getTopY(Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z))) {
                return null;
            }
        }

        BlockState blockState = mc.world.getBlockState(BlockPos.ofFloored(spawnPos));
        if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getFluidState().isEmpty()) {
            return null;
        } else {
            WeatherParticle particle = new WeatherParticle(spawnPos, velocity, maxAge);
            particle.age = (float)(Math.random() * maxAge * 0.5);
            return particle;
        }
    }

    private void renderParticles(Render3DEvent event, String weatherMode) {
        if (!this.particles.isEmpty()) {
            if (glowTexture == null) {
                glowTexture = TextureLoader.loadTexture("assets/prestige/particles/glow.png");
            }

            boolean useTheme = this.themeColor.getValue();
            Color themeDark = useTheme ? UiMath.getAccentColor().darker() : null;
            Color themeBright = useTheme ? UiMath.getAccentColor().brighter() : null;
            Color customColor = useTheme ? null : this.color.getValue();
            float sizeScale = this.size.getValue();
            boolean facePitch = true;
            float width;
            float height;
            switch (weatherMode) {
                case "Rain":
                    width = 0.5F;
                    height = 4.5F;
                    facePitch = false;
                    break;
                case "Snow":
                    width = 1.6F;
                    height = 1.6F;
                    break;
                case "Fireflies":
                    width = 2.5F;
                    height = 2.5F;
                    break;
                case "Embers":
                    width = 1.4F;
                    height = 1.4F;
                    break;
                default:
                    width = 2.0F;
                    height = 2.0F;
            }

            width *= sizeScale;
            height *= sizeScale;
            Vec3d playerPos = PositionUtil.getPlayerPos();
            int renderRadius = this.radius.getValue();

            for (WeatherParticle particle : (Iterable<WeatherParticle>)(new ArrayList(this.particles))) {
                if (!(particle.pos.distanceTo(playerPos) > renderRadius * 1.5)) {
                    float lifeProgress = particle.age / particle.maxAge;
                    float fade;
                    if (lifeProgress < 0.1F) {
                        fade = lifeProgress / 0.1F;
                    } else if (lifeProgress > 0.7F) {
                        fade = (1.0F - lifeProgress) / 0.3F;
                    } else {
                        fade = 1.0F;
                    }

                    if (weatherMode.equals("Fireflies")) {
                        fade *= 0.35F + 0.65F * (float)(Math.sin(particle.age * 0.12 + particle.phase) * 0.5 + 0.5);
                    } else if (weatherMode.equals("Embers")) {
                        fade *= 0.6F + 0.4F * (float)(Math.sin(particle.age * 0.4 + particle.phase) * 0.5 + 0.5);
                    }

                    int alpha = MathHelper.clamp((int)(255.0F * fade), 0, 255);
                    Color resolvedColor;
                    if (useTheme) {
                        resolvedColor = blendColors(themeDark, themeBright, particle.colorBlend);
                    } else {
                        resolvedColor = customColor;
                    }

                    Color particleColor = resolvedColor;
                    Color fadedColor = new Color(particleColor.getRed(), particleColor.getGreen(), particleColor.getBlue(), alpha);
                    Vector3d cameraRelativePos = RenderCore.toCameraRelative(event.context, particle.pos.x, particle.pos.y, particle.pos.z);
                    Matrix4f matrix = new Matrix4f();
                    matrix.translate((float)cameraRelativePos.x, (float)cameraRelativePos.y, (float)cameraRelativePos.z);
                    matrix.scale(0.04F);
                    matrix.rotateY((float)Math.toRadians(-event.context.camera().getYaw()));
                    if (facePitch) {
                        matrix.rotateX((float)Math.toRadians(event.context.camera().getPitch()));
                    }

                    RoundedTextureShader.drawWorldTexture(event.renderer, event.context, matrix, glowTexture, 0.0F, 0.0F, 0.0F, width, height, 0.0F, fadedColor, 0.0F);
                }
            }
        }
    }

    private static Color blendColors(Color from, Color to, float progress) {
        progress = MathHelper.clamp(progress, 0.0F, 1.0F);
        float inverse = 1.0F - progress;
        int red = (int)(from.getRed() * inverse + to.getRed() * progress);
        int green = (int)(from.getGreen() * inverse + to.getGreen() * progress);
        int blue = (int)(from.getBlue() * inverse + to.getBlue() * progress);
        return new Color(MathHelper.clamp(red, 0, 255), MathHelper.clamp(green, 0, 255), MathHelper.clamp(blue, 0, 255));
    }
}

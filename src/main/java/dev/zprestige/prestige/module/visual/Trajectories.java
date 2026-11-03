package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TrajectoriesSimulatedPearl;
import dev.zprestige.prestige.data.TrajectoryPath;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Trajectories extends Module {
    public Trajectories() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private static final float[] MULTISHOT_ANGLES = new float[]{0.0F, -10.0F, 10.0F};
    public EnumSetting render;
    public ColorSetting color;
    private ArrayList paths = new ArrayList();
    private boolean cacheValid = false;
    private ItemStack cachedStack;
    private String cachedMode;
    private boolean cachedUsingItem;
    private boolean cachedCrossbowCharged;
    private int cachedUseTime;
    private float cachedPitch;
    private float cachedYaw;
    private double cachedPosX;
    private double cachedPosY;
    private double cachedPosZ;
    private float fadeAlpha = 0.0F;
    private float smoothPitch = 0.0F;
    private float smoothYaw = 0.0F;
    private boolean rotationInitialized = false;
    private long lastFrameTime = 0L;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal, Gamemode.Mace);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player != null && mc.world != null) {
            ItemStack heldStack = mc.player.getMainHandStack();
            String mode = this.render.getValue().toString();
            Vec3d playerPos = Render2D.getInterpolatedPos(mc.player);
            boolean usingItem = mc.player.isUsingItem();
            int useTime = mc.player.getItemUseTime();
            boolean crossbowCharged = heldStack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(heldStack);
            long now = System.currentTimeMillis();
            float deltaSeconds = this.lastFrameTime == 0L ? 0.016F : (float)(now - this.lastFrameTime) / 1000.0F;
            boolean afterPause = this.lastFrameTime != 0L && now - this.lastFrameTime > 1000L;
            this.lastFrameTime = now;
            deltaSeconds = Math.min(deltaSeconds, 0.1F);
            float pitch = mc.player.getPitch();
            float yaw = mc.player.getYaw();
            if (this.rotationInitialized && !afterPause) {
                this.smoothPitch = smoothTowards(this.smoothPitch, pitch, deltaSeconds, 20.0F);
                this.smoothYaw = this.smoothYaw + angleDifference(this.smoothYaw, yaw) * (1.0F - (float)Math.exp(-20.0F * deltaSeconds));
            } else {
                this.smoothPitch = pitch;
                this.smoothYaw = yaw;
                this.rotationInitialized = true;
            }

            boolean predicting = this.shouldPredict(heldStack, mode);
            this.fadeAlpha = smoothTowards(this.fadeAlpha, predicting ? 1.0F : 0.0F, deltaSeconds, predicting ? 12.0F : 7.0F);
            if (predicting) {
                if (!this.cacheValid
                    || heldStack != this.cachedStack
                    || !mode.equals(this.cachedMode)
                    || usingItem != this.cachedUsingItem
                    || crossbowCharged != this.cachedCrossbowCharged
                    || useTime != this.cachedUseTime
                    || this.smoothPitch != this.cachedPitch
                    || this.smoothYaw != this.cachedYaw
                    || playerPos.x != this.cachedPosX
                    || playerPos.y != this.cachedPosY
                    || playerPos.z != this.cachedPosZ) {
                    this.rebuildPaths(heldStack, mode);
                    this.cachedStack = heldStack;
                    this.cachedMode = mode;
                    this.cachedUsingItem = usingItem;
                    this.cachedCrossbowCharged = crossbowCharged;
                    this.cachedUseTime = useTime;
                    this.cachedPitch = this.smoothPitch;
                    this.cachedYaw = this.smoothYaw;
                    this.cachedPosX = playerPos.x;
                    this.cachedPosY = playerPos.y;
                    this.cachedPosZ = playerPos.z;
                    this.cacheValid = true;
                }
            } else if (this.fadeAlpha < 0.005F && !this.paths.isEmpty()) {
                this.paths.clear();
                this.cacheValid = false;
            }

            if (!(this.fadeAlpha < 0.005F)) {
                for (TrajectoryPath path : (Iterable<TrajectoryPath>)(this.paths)) {
                    this.renderPath(event, path);
                }
            }
        }
    }

    private boolean shouldPredict(ItemStack stack, String mode) {
        boolean crossbowEnabled = mode.equals("Bows") || mode.equals("Crossbow") || mode.equals("All");
        boolean bowEnabled = mode.equals("Bows") || mode.equals("Bow") || mode.equals("All");
        boolean pearlEnabled = mode.equals("Pearl") || mode.equals("All");
        if (stack.isOf(Items.CROSSBOW) && crossbowEnabled) {
            return CrossbowItem.isCharged(stack);
        } else {
            return stack.isOf(Items.BOW) && mc.player.isUsingItem() && bowEnabled ? true : stack.isOf(Items.ENDER_PEARL) && pearlEnabled;
        }
    }

    private static float smoothTowards(float current, float target, float deltaTime, float speed) {
        return current + (target - current) * (1.0F - (float)Math.exp(-speed * deltaTime));
    }

    private static float angleDifference(float fromAngle, float toAngle) {
        return ((toAngle - fromAngle) % 360.0F + 540.0F) % 360.0F - 180.0F;
    }

    private void rebuildPaths(ItemStack stack, String mode) {
        this.paths.clear();
        boolean crossbowEnabled = mode.equals("Bows") || mode.equals("Crossbow") || mode.equals("All");
        boolean bowEnabled = mode.equals("Bows") || mode.equals("Bow") || mode.equals("All");
        boolean pearlEnabled = mode.equals("Pearl") || mode.equals("All");
        if (stack.isOf(Items.CROSSBOW) && crossbowEnabled) {
            if (!CrossbowItem.isCharged(stack)) {
                return;
            }

            stack.getItem();
            if (EnchantmentHelper.getLevel(
                    mc.world.getRegistryManager().getOptional(Enchantments.MULTISHOT.getRegistryRef()).get().getEntry(Enchantments.MULTISHOT.getValue()).get(),
                    stack
                )
                > 0) {
                this.paths.add(this.simulateArrowPath(MULTISHOT_ANGLES[0], Items.ARROW.getDefaultStack(), stack));
                this.paths.add(this.simulateArrowPath(MULTISHOT_ANGLES[1], Items.ARROW.getDefaultStack(), stack));
                this.paths.add(this.simulateArrowPath(MULTISHOT_ANGLES[2], Items.ARROW.getDefaultStack(), stack));
            } else {
                this.paths.add(this.simulateArrowPath(0.0F, Items.ARROW.getDefaultStack(), stack));
            }
        } else if (stack.isOf(Items.BOW) && mc.player.isUsingItem() && bowEnabled) {
            this.paths.add(this.simulateArrowPath(0.0F, Items.ARROW.getDefaultStack(), stack));
        } else if (stack.isOf(Items.ENDER_PEARL) && pearlEnabled) {
            this.paths.add(this.simulatePearlPath());
        }
    }

    private void renderPath(Render3DEvent event, TrajectoryPath path) {
        ArrayList points = path.points();
        if (points.size() > 1) {
            Color beamColor = this.color.getValue();
            long now = System.currentTimeMillis();
            this.drawBeam(event, points, beamColor, now);
            this.drawImpactMarker(event, (Vec3d)points.get(points.size() - 1), beamColor, path.landed(), now);
        }
    }

    private void drawBeam(Render3DEvent event, ArrayList points, Color beamColor, long now) {
        int lastIndex = points.size() - 1;
        int segmentCount = Math.min(28, lastIndex);

        for (int segment = 0; segment < segmentCount; segment++) {
            int segmentAlpha = Math.round(255.0F - (segmentCount == 1 ? 0.0F : (float)segment / (segmentCount - 1)) * 175.0F);
            ShapeRenderer.drawPath(event.context, event.renderer, sliceSegment(points, segment, segmentCount, lastIndex), this.withFade(beamColor, segmentAlpha));
        }

        int pulseHead = (int)(((float)(now % 1200L) / 1200.0F) * segmentCount);

        for (int trail = 0; trail < 4; trail++) {
            int pulseSegment = pulseHead - trail;
            if (pulseSegment >= 0 && pulseSegment < segmentCount) {
                ShapeRenderer.drawPath(event.context, event.renderer, sliceSegment(points, pulseSegment, segmentCount, lastIndex), this.withFade(beamColor, 255 - trail * 55));
            }
        }
    }

    private static ArrayList sliceSegment(ArrayList points, int segmentIndex, int segmentCount, int lastIndex) {
        int startIndex = segmentIndex * lastIndex / segmentCount;
        int endIndex = (segmentIndex + 1) * lastIndex / segmentCount;
        ArrayList slice = new ArrayList(endIndex - startIndex + 1);

        for (int pointIndex = startIndex; pointIndex <= endIndex; pointIndex++) {
            slice.add((Vec3d)points.get(pointIndex));
        }

        return slice;
    }

    private void drawImpactMarker(Render3DEvent event, Vec3d landingPos, Color ringColor, boolean landed, long now) {
        float posX = (float)landingPos.x;
        float posY = (float)landingPos.y;
        float posZ = (float)landingPos.z;
        long pulsePeriod = landed ? 700L : 1500L;
        float discRadius = (landed ? 0.45F : 0.25F) + 0.1F * (0.5F * (1.0F + (float)Math.sin(((float)(now % pulsePeriod) / (float)pulsePeriod) * (float) (Math.PI * 2))));
        Color solidColor = this.withFade(ringColor, 255);
        ShapeRenderer.drawDisc(event.context, event.renderer, posX, posY, posZ, discRadius, solidColor, this.withFade(ringColor, 0));
        ShapeRenderer.drawGlowRing(event.context, event.renderer, posX, posY, posZ, discRadius + 0.08F, 0.02F, 0.06F, solidColor, this.withFade(ringColor, 60));
        if (landed) {
            float ripplePhase = (float)(now % 500L) / 500.0F;
            float rippleRadius = 0.3F + ripplePhase * 0.9F;
            float rippleFade = 1.0F - ripplePhase;
            ShapeRenderer.drawGlowRing(event.context, event.renderer, posX, posY, posZ, rippleRadius, 0.02F, 0.05F, this.withFade(ringColor, Math.round(255.0F * rippleFade)), this.withFade(ringColor, Math.round(60.0F * rippleFade)));
        }
    }

    private Color withFade(Color baseColor, int alpha) {
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), MathHelper.clamp(Math.round(alpha * this.fadeAlpha), 0, 255));
    }

    private TrajectoryPath simulatePearlPath() {
        ArrayList points = new ArrayList();
        ClientPlayerEntity player = mc.player;
        Vec3d renderPos = Render2D.getInterpolatedPos(mc.player);
        Vec3d originalPos = PositionUtil.getPlayerPos();
        player.setPosition(renderPos.x, renderPos.y, renderPos.z);
        try {
            TrajectoriesSimulatedPearl pearl = new TrajectoriesSimulatedPearl(mc.world, player, Items.ENDER_PEARL.getDefaultStack());
            pearl.setVelocity(player, this.smoothPitch, this.smoothYaw, 0.0F, 1.5F, 1.0F);
            int steps = 0;

            while (!pearl.landed && steps++ < 1000) {
                points.add(PositionUtil.getEntityPos(pearl));
                pearl.tick();
            }
        } finally {
            player.setPosition(originalPos);
        }

        for (int pointIndex = 0; pointIndex < points.size() - 1; pointIndex++) {
            Vec3d point = (Vec3d)points.get(pointIndex);
            points.set(pointIndex, new Vec3d(point.x, point.y - 0.1F, point.z));
        }

        return new TrajectoryPath(points, true);
    }

    private TrajectoryPath simulateArrowPath(float angleOffset, ItemStack arrowStack, ItemStack weaponStack) {
        ArrayList points = new ArrayList();
        Item weapon = weaponStack.getItem();
        ClientPlayerEntity player = mc.player;
        Vec3d renderPos = Render2D.getInterpolatedPos(mc.player);
        Vec3d originalPos = PositionUtil.getPlayerPos();
        float originalPitch = player.getPitch();
        float originalYaw = player.getYaw();
        boolean hitEntity;
        try {
            player.setPosition(renderPos.x, renderPos.y, renderPos.z);
            player.setPitch(this.smoothPitch);
            player.setYaw(this.smoothYaw);
            dev.zprestige.prestige.world.SimulatedBowArrow arrow = new dev.zprestige.prestige.world.SimulatedBowArrow(EntityType.ARROW, player, mc.world, arrowStack);
            if (weapon instanceof CrossbowItem) {
                Vec3d axis = player.getOppositeRotationVector(1.0F);
                Quaternionf rotation = new Quaternionf().setAngleAxis((double)(angleOffset * (float) (Math.PI / 180.0)), axis.x, axis.y, axis.z);
                Vector3f direction = player.getRotationVec(1.0F).toVector3f().rotate(rotation);
                arrow.setVelocity(direction.x(), direction.y(), direction.z(), 3.15F, 1.0F);
            } else {
                arrow.setVelocity(player, this.smoothPitch, this.smoothYaw, 0.0F, BowItem.getPullProgress(mc.player.getItemUseTime()) * 3.0F, 1.0F);
            }

            arrow.inFlight = true;
            int steps = 0;

            while (arrow.inFlight && steps++ < 1000) {
                points.add(PositionUtil.getEntityPos(arrow));
                arrow.tick();
            }

            hitEntity = arrow.hitEntity;
        } finally {
            player.setPosition(originalPos);
            player.setPitch(originalPitch);
            player.setYaw(originalYaw);
        }

        for (int pointIndex = 0; pointIndex < points.size() - 1; pointIndex++) {
            Vec3d point = (Vec3d)points.get(pointIndex);
            points.set(pointIndex, new Vec3d(point.x, point.y - 0.1F, point.z));
        }

        return new TrajectoryPath(points, hitEntity);
    }
}

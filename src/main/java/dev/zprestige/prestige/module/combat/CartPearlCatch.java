package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CartCatchPearlLanding;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.BlurredRoundedRectShader;
import dev.zprestige.prestige.render.RoundedRectShader;
import dev.zprestige.prestige.type.CartCatchState;
import dev.zprestige.prestige.world.RotationUtil;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;

public class CartPearlCatch extends Module implements RotationProvider {
    private FloatSetting scanRange;
    private FloatSetting minDamage;
    private FloatSetting maxSelfDamage;
    private BoolSetting preferXbow;
    private IntSetting switchDelay;
    private BoolSetting silentAim;
    private FloatSetting silentAimFov;
    private BoolSetting render;
    private static final Item[] RAIL_ITEMS = new Item[]{Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
    private CartCatchState state = CartCatchState.IDLE;
    private UUID pearlUuid;
    private Set handledPearls = new HashSet();
    private int cartX;
    private int cartY;
    private int cartZ;
    private BlockPos firePos;
    private boolean useCrossbow;
    private int previousSlot = -1;
    private int delayTicks = 0;
    private int activeTicks = 0;
    private int aimWaitTicks = 0;
    private int pendingCartSlot = -1;
    private boolean holdingUseKey = false;
    private Rotation shotRotation;
    private int shotFlightTicks = -1;
    private static final Identifier CART_TEXTURE_ID = Identifier.of("minecraft", "textures/item/tnt_minecart.png");
    private static GlTextureHandle cartTexture;

    public CartPearlCatch() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.silentAimFov.visibleWhen((Predicate<Float>)ignored -> this.silentAim.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.handledPearls.clear();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.releaseUseKey();
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.resetState();
    }

    private void resetState() {
        this.state = CartCatchState.IDLE;
        this.pearlUuid = null;
        this.firePos = null;
        this.previousSlot = -1;
        this.delayTicks = 0;
        this.activeTicks = 0;
        this.aimWaitTicks = 0;
        this.pendingCartSlot = -1;
        this.shotRotation = null;
        this.shotFlightTicks = -1;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.handledPearls.clear();
    }

    private void printMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§7[CartPearlCatch] §f" + message), false);
        }
    }

    private void abort(String reason) {
        this.printMessage("aborted: " + reason);
        if (this.pearlUuid != null) {
            this.handledPearls.add(this.pearlUuid);
        }

        this.releaseUseKey();
        this.resetState();
    }

    private void releaseUseKey() {
        if (this.holdingUseKey) {
            this.holdingUseKey = false;
            mc.options.useKey.setPressed(false);
            if (mc.player != null && mc.player.isUsingItem()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    private CartCatchPearlLanding predictLanding(EnderPearlEntity pearl) {
        double posX = pearl.getX();
        double posY = pearl.getY();
        double posZ = pearl.getZ();
        Vec3d velocity = pearl.getVelocity();
        double motionX = velocity.x;
        double motionY = velocity.y;
        double motionZ = velocity.z;

        for (int tick = 1; tick <= 200; tick++) {
            Vec3d prevPos = new Vec3d(posX, posY, posZ);
            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            motionX *= 0.99;
            motionZ *= 0.99;
            motionY = motionY * 0.99 - 0.03;
            BlockHitResult hit = mc.world.raycast(new RaycastContext(prevPos, new Vec3d(posX, posY, posZ), ShapeType.OUTLINE, FluidHandling.NONE, pearl));
            if (hit != null && hit.getType() == Type.BLOCK) {
                return new CartCatchPearlLanding(hit.getPos(), tick);
            }

            if (posY < -65.0) {
                break;
            }
        }

        return null;
    }

    private EnderPearlEntity findTrackedPearl() {
        if (this.pearlUuid != null && mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl && entity.getUuid().equals(this.pearlUuid)) {
                    return pearl;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private double estimateExplosionDamage(Vec3d targetPos, Vec3d explosionPos, double power) {
        double explosionDiameter = power * 2.0;
        double distanceRatio = Math.sqrt(targetPos.squaredDistanceTo(explosionPos)) / explosionDiameter;
        if (distanceRatio > 1.0) {
            return 0.0;
        } else {
            int visibleRays = 0;

            for (double heightOffset : new double[]{0.2, 1.0, 1.7}) {
                Vec3d samplePos = targetPos.add(0.0, heightOffset, 0.0);
                BlockHitResult hit = mc.world.raycast(new RaycastContext(explosionPos, samplePos, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
                if (hit == null || hit.getType() == Type.MISS) {
                    visibleRays++;
                }
            }

            double impact = (1.0 - distanceRatio) * (visibleRays / 3.0);
            return (impact * impact + impact) / 2.0 * 7.0 * explosionDiameter + 1.0;
        }
    }

    private BlockPos findBestCartPos(Vec3d landing) {
        double explosionPower = this.useCrossbow ? 6.4 : 6.25;
        double arrowSpeed = this.useCrossbow ? 3.15 : 3.0;
        BlockPos landingBlock = BlockPos.ofFloored(landing);
        BlockPos bestPos = null;
        double bestDamage = this.minDamage.getValue().floatValue();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos floorPos = landingBlock.add(dx, dy, dz);
                    BlockPos railPos = floorPos.up();
                    if (!mc.world.getBlockState(floorPos).isReplaceable() && mc.world.getBlockState(railPos).isReplaceable()) {
                        if (this.isInReach(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5)) {
                            Vec3d cartCenter = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.0625, railPos.getZ() + 0.5);
                            if (!(Prestige.damageCalculator.getTntCartDamage(mc.player, cartCenter, arrowSpeed) > this.maxSelfDamage.getValue().floatValue())) {
                                double damage = this.estimateExplosionDamage(landing, cartCenter, explosionPower);
                                if (damage >= bestDamage) {
                                    bestDamage = damage;
                                    bestPos = railPos;
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private int simulateArrowFlight(float yaw, float pitch, float pullProgress) {
        Vec3d eyePos = mc.player.getEyePos();
        double arrowX = eyePos.x;
        double arrowY = eyePos.y - 0.1;
        double arrowZ = eyePos.z;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double motionX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double motionY = -Math.sin(pitchRad);
        double motionZ = Math.cos(yawRad) * Math.cos(pitchRad);
        double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        double speed = Math.min(pullProgress, 1.05F) * 3.0;
        motionX = motionX / length * speed;
        motionY = motionY / length * speed;
        motionZ = motionZ / length * speed;
        motionX += mc.player.getVelocity().x;
        motionZ += mc.player.getVelocity().z;
        if (!mc.player.isOnGround()) {
            motionY += mc.player.getVelocity().y;
        }

        Box cartBox = new Box(this.cartX + 0.01, this.cartY + 0.0625, this.cartZ + 0.01, this.cartX + 0.99, this.cartY + 0.7625, this.cartZ + 0.99);
        BlockPos cartBlock = new BlockPos(this.cartX, this.cartY, this.cartZ);

        for (int tick = 1; tick <= 60; tick++) {
            Vec3d prevPos = new Vec3d(arrowX, arrowY, arrowZ);
            int substeps = Math.max(1, (int)Math.ceil(Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ) / 0.25));

            for (int step = 0; step < substeps; step++) {
                arrowX += motionX / substeps;
                arrowY += motionY / substeps;
                arrowZ += motionZ / substeps;
                if (cartBox.contains(arrowX, arrowY, arrowZ)) {
                    return tick;
                }
            }

            Vec3d nextPos = new Vec3d(arrowX, arrowY, arrowZ);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(prevPos, nextPos, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
            if (hit != null && hit.getType() == Type.BLOCK && !hit.getBlockPos().equals(cartBlock)) {
                return -1;
            }

            if (arrowY < this.cartY - 3) {
                return -1;
            }

            motionX *= 0.99;
            motionZ *= 0.99;
            motionY = motionY * 0.99 - 0.05;
        }

        return -1;
    }

    private Rotation getAimRotation(Vec3d target) {
        Vec3d eyePos = mc.player.getEyePos();
        double deltaX = target.x - eyePos.x;
        double deltaY = target.y - (eyePos.y - 0.1);
        double deltaZ = target.z - eyePos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (distance < 1.0E-6) {
            return null;
        } else {
            return new Rotation((float)(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0), (float)(-Math.toDegrees(Math.asin(deltaY / distance))));
        }
    }

    private boolean solveShot(float pullProgress) {
        Vec3d cartCenter = new Vec3d(this.cartX + 0.5, this.cartY + 0.45, this.cartZ + 0.5);
        double[] heightOffsets = new double[]{0.0, 0.15, 0.3, 0.5, 0.8, 1.2, 1.8, 2.6};

        for (double heightOffset : heightOffsets) {
            Rotation rotation = this.getAimRotation(cartCenter.add(0.0, heightOffset, 0.0));
            if (rotation != null) {
                int flightTicks = this.simulateArrowFlight(rotation.getYaw(), rotation.getPitch(), pullProgress);
                if (flightTicks != -1) {
                    this.shotRotation = rotation;
                    this.shotFlightTicks = flightTicks;
                    return true;
                }
            }
        }

        return false;
    }

    private BlockPos findFireSpot(Vec3d target) {
        Rotation rotation = this.getAimRotation(target);
        if (rotation == null) {
            return null;
        } else {
            Vec3d eyePos = mc.player.getEyePos();
            double arrowX = eyePos.x;
            double arrowY = eyePos.y - 0.1;
            double arrowZ = eyePos.z;
            double yawRad = Math.toRadians(rotation.getYaw());
            double pitchRad = Math.toRadians(rotation.getPitch());
            double motionX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double motionY = -Math.sin(pitchRad);
            double motionZ = Math.cos(yawRad) * Math.cos(pitchRad);
            double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            motionX = motionX / length * 3.15;
            motionY = motionY / length * 3.15;
            motionZ = motionZ / length * 3.15;
            motionX += mc.player.getVelocity().x;
            motionZ += mc.player.getVelocity().z;
            if (!mc.player.isOnGround()) {
                motionY += mc.player.getVelocity().y;
            }

            BlockPos cartBlock = new BlockPos(this.cartX, this.cartY, this.cartZ);
            Box playerBox = mc.player.getBoundingBox().expand(0.3);

            for (int tick = 0; tick < 60; tick++) {
                int substeps = Math.max(1, (int)Math.ceil(Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ) / 0.25));

                for (int step = 0; step < substeps; step++) {
                    arrowX += motionX / substeps;
                    arrowY += motionY / substeps;
                    arrowZ += motionZ / substeps;
                    BlockPos stepBlock = BlockPos.ofFloored(arrowX, arrowY, arrowZ);
                    if (stepBlock.equals(cartBlock) || stepBlock.equals(cartBlock.up())) {
                        return null;
                    }

                    if (!playerBox.intersects(new Box(stepBlock))) {
                        double centerX = stepBlock.getX() + 0.5;
                        double bottomY = stepBlock.getY();
                        if (this.isInReach(centerX, bottomY, stepBlock.getZ() + 0.5)
                            && mc.world.getBlockState(stepBlock).isReplaceable()
                            && mc.world.getBlockState(stepBlock.down()).isSideSolidFullSquare(mc.world, stepBlock.down(), Direction.UP)) {
                            return stepBlock;
                        }
                    }
                }

                motionX *= 0.99;
                motionZ *= 0.99;
                motionY = motionY * 0.99 - 0.05;
            }

            return null;
        }
    }

    private boolean shotPassesThroughFire(float yaw, float pitch) {
        Vec3d eyePos = mc.player.getEyePos();
        double arrowX = eyePos.x;
        double arrowY = eyePos.y - 0.1;
        double arrowZ = eyePos.z;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double motionX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double motionY = -Math.sin(pitchRad);
        double motionZ = Math.cos(yawRad) * Math.cos(pitchRad);
        double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX = motionX / length * 3.15;
        motionY = motionY / length * 3.15;
        motionZ = motionZ / length * 3.15;
        motionX += mc.player.getVelocity().x;
        motionZ += mc.player.getVelocity().z;
        if (!mc.player.isOnGround()) {
            motionY += mc.player.getVelocity().y;
        }

        Box cartBox = new Box(this.cartX + 0.01, this.cartY + 0.0625, this.cartZ + 0.01, this.cartX + 0.99, this.cartY + 0.7625, this.cartZ + 0.99);
        BlockPos cartBlock = new BlockPos(this.cartX, this.cartY, this.cartZ);
        Box fireBox = this.firePos != null ? new Box(this.firePos) : null;
        boolean ignited = false;

        for (int tick = 0; tick < 60; tick++) {
            Vec3d prevPos = new Vec3d(arrowX, arrowY, arrowZ);
            int substeps = Math.max(1, (int)Math.ceil(Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ) / 0.25));

            for (int step = 0; step < substeps; step++) {
                arrowX += motionX / substeps;
                arrowY += motionY / substeps;
                arrowZ += motionZ / substeps;
                if (!ignited && fireBox != null && fireBox.contains(arrowX, arrowY, arrowZ)) {
                    ignited = true;
                }

                if (cartBox.contains(arrowX, arrowY, arrowZ)) {
                    return ignited;
                }
            }

            Vec3d nextPos = new Vec3d(arrowX, arrowY, arrowZ);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(prevPos, nextPos, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
            if (hit != null && hit.getType() == Type.BLOCK && !hit.getBlockPos().equals(cartBlock)) {
                return false;
            }

            if (arrowY < this.cartY - 3) {
                return false;
            }

            motionX *= 0.99;
            motionZ *= 0.99;
            motionY = motionY * 0.99 - 0.05;
        }

        return false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.state == CartCatchState.IDLE) {
                this.scanForPearls();
            } else if (++this.activeTicks > 100) {
                this.abort("timed out");
            } else {
                switch (this.state) {
                    case ARMED:
                        EnderPearlEntity pearl = this.findTrackedPearl();
                        if (pearl == null) {
                            this.abort("too late — the pearl already landed");
                            return;
                        }

                        int ticksLeft = this.getTicksUntilLanding(pearl);
                        if (this.useCrossbow) {
                            this.solveShot(1.05F);
                            if (ticksLeft <= this.getLeadTicks() + Math.max(this.shotFlightTicks, 0)) {
                                this.state = CartCatchState.SWITCH_RAIL;
                            }
                        } else if (ticksLeft <= this.getLeadTicks() + 20) {
                            this.state = CartCatchState.SWITCH_RAIL;
                        }
                        break;
                    case SWITCH_RAIL:
                        int railSlot = this.findRailSlot();
                        if (railSlot == -1) {
                            this.abort("no rail in hotbar");
                            return;
                        }

                        if (!this.isInReach(this.cartX + 0.5, this.cartY + 0.5, this.cartZ + 0.5)) {
                            this.abort("placement out of reach");
                            return;
                        }

                        BlockPos railSupport = new BlockPos(this.cartX, this.cartY - 1, this.cartZ);
                        InventoryUtil.selectSlot(railSlot);
                        this.clickBlock(new BlockHitResult(new Vec3d(this.cartX + 0.5, this.cartY, this.cartZ + 0.5), Direction.UP, railSupport, false));
                        this.delayTicks = this.switchDelay.getValue();
                        this.state = CartCatchState.SWITCH_CART;
                        break;
                    case SWITCH_CART:
                        if (this.delayTicks > 0) {
                            this.delayTicks--;
                            return;
                        }

                        Integer cartSlot = this.findTntCartSlot();
                        if (cartSlot == null) {
                            this.abort("no TNT carts");
                            return;
                        }

                        if (mc.player.getInventory().getStack(cartSlot).getItem() != Items.TNT_MINECART) {
                            return;
                        }

                        if (!this.isInReach(this.cartX + 0.5, this.cartY + 0.5, this.cartZ + 0.5)) {
                            this.abort("cart out of reach");
                            return;
                        }

                        InventoryUtil.selectSlot(cartSlot);
                        PlayerInteractions.interactBlock(
                            new BlockHitResult(
                                    new Vec3d(this.cartX + 0.5, this.cartY + 0.125, this.cartZ + 0.5), Direction.UP, new BlockPos(this.cartX, this.cartY, this.cartZ), false
                                )
                        );
                        if (this.useCrossbow) {
                            this.delayTicks = this.switchDelay.getValue();
                            this.state = CartCatchState.SWITCH_FIRE;
                        } else {
                            this.state = CartCatchState.SWITCH_BOW;
                        }
                        break;
                    case SWITCH_FIRE:
                        if (this.delayTicks > 0) {
                            this.delayTicks--;
                            return;
                        }

                        if (this.firePos == null) {
                            this.firePos = this.findFireSpot(new Vec3d(this.cartX + 0.5, this.cartY + 0.45, this.cartZ + 0.5));
                            if (this.firePos == null) {
                                this.abort("no fire spot on the shot path");
                                return;
                            }
                        }

                        int flintSlot = this.findFlintAndSteelSlot();
                        if (flintSlot == -1) {
                            this.abort("lost the flint & steel");
                            return;
                        }

                        BlockPos fireSupport = this.firePos.down();
                        InventoryUtil.selectSlot(flintSlot);
                        this.clickBlock(
                            new BlockHitResult(new Vec3d(fireSupport.getX() + 0.5, fireSupport.getY() + 1.0, fireSupport.getZ() + 0.5), Direction.UP, fireSupport, false)
                        );
                        this.delayTicks = this.switchDelay.getValue();
                        this.state = CartCatchState.SWITCH_XBOW;
                        break;
                    case SWITCH_XBOW:
                        if (this.delayTicks > 0) {
                            this.delayTicks--;
                            return;
                        }

                        int crossbowSlot = this.findChargedCrossbowSlot();
                        if (crossbowSlot == -1) {
                            this.abort("no charged crossbow");
                            return;
                        }

                        InventoryUtil.selectSlot(crossbowSlot);
                        this.aimWaitTicks = 0;
                        this.state = CartCatchState.FIRE;
                        break;
                    case FIRE:
                        Rotation aimRotation = this.getAimRotation(new Vec3d(this.cartX + 0.5, this.cartY + 0.45, this.cartZ + 0.5));
                        if (aimRotation != null) {
                            if (this.shotPassesThroughFire(aimRotation.getYaw(), aimRotation.getPitch())) {
                                this.shotRotation = aimRotation;
                                int flightTicks = this.simulateArrowFlight(aimRotation.getYaw(), aimRotation.getPitch(), 1.05F);
                                if (flightTicks != -1) {
                                    this.shotFlightTicks = flightTicks;
                                }
                            }
                        }

                        if (this.shotRotation == null) {
                            this.abort("no firing line through the fire");
                            return;
                        }

                        if (!InventoryUtil.isHolding(Items.CROSSBOW)) {
                            int chargedCrossbowSlot = this.findChargedCrossbowSlot();
                            if (chargedCrossbowSlot == -1) {
                                this.abort("crossbow lost its charge");
                                return;
                            }

                            InventoryUtil.selectSlot(chargedCrossbowSlot);
                            return;
                        }

                        if (!this.shouldFireNow()) {
                            return;
                        }

                        Rotation xbowActiveRotation = Prestige.silentRotationManager.getActiveRotation();
                        if (xbowActiveRotation != null && xbowActiveRotation.isClamped() && this.aimWaitTicks++ < 5) {
                            return;
                        }

                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        Prestige.silentRotationManager.setActedThisTick(true);
                        this.finishCatch();
                        break;
                    case SWITCH_BOW:
                        Integer bowSlot = InventoryUtil.findHotbarSlot(Items.BOW);
                        if (bowSlot == null) {
                            this.abort("no bow in hotbar");
                            return;
                        }

                        InventoryUtil.selectSlot(bowSlot);
                        this.state = CartCatchState.USE_BOW;
                        break;
                    case USE_BOW:
                        mc.options.useKey.setPressed(true);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        this.holdingUseKey = true;
                        this.state = CartCatchState.CHARGE_WAIT;
                        break;
                    case CHARGE_WAIT:
                        if (!mc.options.useKey.isPressed()) {
                            mc.options.useKey.setPressed(true);
                        }

                        int useTime = mc.player.getItemUseTime();
                        if (useTime >= 3) {
                            this.solveShot(BowItem.getPullProgress(useTime));
                        }

                        if (this.shotRotation == null && useTime >= 30) {
                            this.abort("no firing line at the cart");
                            return;
                        }

                        if (this.shotRotation != null && (this.shouldFireNow() || useTime >= 30)) {
                            this.aimWaitTicks = 0;
                            this.state = CartCatchState.RELEASE;
                        }
                        break;
                    case RELEASE:
                        if (this.shotRotation == null) {
                            this.abort("no firing line at the cart");
                            return;
                        }

                        if (!this.shouldFireNow() && mc.player.getItemUseTime() < 30) {
                            if (!mc.options.useKey.isPressed()) {
                                mc.options.useKey.setPressed(true);
                            }

                            return;
                        }

                        Rotation bowActiveRotation = Prestige.silentRotationManager.getActiveRotation();
                        if (bowActiveRotation != null && bowActiveRotation.isClamped() && this.aimWaitTicks++ < 5) {
                            if (!mc.options.useKey.isPressed()) {
                                mc.options.useKey.setPressed(true);
                            }

                            return;
                        }

                        this.holdingUseKey = false;
                        mc.options.useKey.setPressed(false);
                        if (mc.player.isUsingItem()) {
                            mc.interactionManager.stopUsingItem(mc.player);
                        }

                        this.finishCatch();
                }
            }
        }
    }

    private int getTicksUntilLanding(EnderPearlEntity pearl) {
        CartCatchPearlLanding landing = this.predictLanding(pearl);
        return landing != null ? landing.ticksUntilLanding() : 0;
    }

    private int getLeadTicks() {
        int switchTicks = this.switchDelay.getValue();
        return this.useCrossbow ? 3 * switchTicks + 4 : switchTicks + 2;
    }

    private boolean shouldFireNow() {
        if (this.shotFlightTicks < 0) {
            return false;
        } else {
            EnderPearlEntity pearl = this.findTrackedPearl();
            if (pearl == null) {
                return true;
            } else {
                CartCatchPearlLanding landing = this.predictLanding(pearl);
                return landing == null || landing.ticksUntilLanding() <= this.shotFlightTicks + 1;
            }
        }
    }

    private void finishCatch() {
        if (this.pearlUuid != null) {
            this.handledPearls.add(this.pearlUuid);
        }

        if (this.previousSlot != -1) {
            InventoryUtil.selectSlot(this.previousSlot);
        }

        this.resetState();
    }

    private void scanForPearls() {
        if (this.findRailSlot() != -1) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl && !this.handledPearls.contains(pearl.getUuid())) {
                    double range = this.scanRange.getValue().floatValue();
                    Vec3d eyePos = mc.player.getEyePos();
                    double dx = pearl.getX() - eyePos.x;
                    double dy = pearl.getY() - eyePos.y;
                    double dz = pearl.getZ() - eyePos.z;
                    if (!(dx * dx + dy * dy + dz * dz > range * range)
                        && pearl.getOwner() instanceof PlayerEntity thrower
                        && !thrower.equals(mc.player)
                        && Prestige.botFilter.isValidTarget(thrower)
                        && !Prestige.friendManager.isFriend(thrower.getName().getString())) {
                        CartCatchPearlLanding landing = this.predictLanding(pearl);
                        if (landing != null && landing.ticksUntilLanding() <= 100) {
                            boolean canUseCrossbow = this.preferXbow.getValue() && this.findChargedCrossbowSlot() != -1 && this.findFlintAndSteelSlot() != -1;
                            if ((canUseCrossbow || InventoryUtil.findHotbarSlot(Items.BOW) != null) && this.findTntCartSlot() != null) {
                                this.useCrossbow = canUseCrossbow;
                                BlockPos cartPos = this.findBestCartPos(landing.landingPos());
                                if (cartPos != null) {
                                    this.pearlUuid = pearl.getUuid();
                                    this.cartX = cartPos.getX();
                                    this.cartY = cartPos.getY();
                                    this.cartZ = cartPos.getZ();
                                    if (this.solveShot(canUseCrossbow ? 1.05F : 1.0F)) {
                                        this.previousSlot = InventoryUtil.getSelectedSlot();
                                        this.activeTicks = 0;
                                        this.delayTicks = 0;
                                        this.state = CartCatchState.ARMED;
                                        return;
                                    }

                                    this.pearlUuid = null;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player != null && mc.world != null && this.silentAim.getValue()) {
            if (this.shotRotation == null
                || this.state != CartCatchState.FIRE
                    && this.state != CartCatchState.RELEASE
                    && this.state != CartCatchState.SWITCH_XBOW
                    && this.state != CartCatchState.SWITCH_BOW
                    && this.state != CartCatchState.USE_BOW
                    && this.state != CartCatchState.CHARGE_WAIT) {
                Vec3d aimPoint = null;
                if (this.state == CartCatchState.SWITCH_RAIL || this.state == CartCatchState.SWITCH_CART) {
                    aimPoint = new Vec3d(this.cartX + 0.5, this.cartY + 0.5, this.cartZ + 0.5);
                } else if (this.state == CartCatchState.SWITCH_FIRE && this.firePos != null) {
                    aimPoint = this.firePos.toCenterPos();
                }

                if (aimPoint != null) {
                    Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                    if (rotation != null) {
                        if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                            return rotation;
                        }
                    }
                }

                return null;
            } else {
                return Math.abs(RotationUtil.wrapDegrees(this.shotRotation.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue() ? this.shotRotation : null;
            }
        } else {
            return null;
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (this.render.getValue()) {
            if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) {
                if (mc.world != null && mc.player != null) {
                    if (this.state != CartCatchState.IDLE) {
                        if (cartTexture == null) {
                            cartTexture = GlTextureHandle.wrapMinecraftTexture(CART_TEXTURE_ID);
                        }

                        EnderPearlEntity pearl = this.findTrackedPearl();
                        String etaText = pearl == null ? "!" : String.format("%.1fs", this.getTicksUntilLanding(pearl) / 20.0F);
                        Vec3d screenPos = Render2D.worldToScreen(new Vec3d(this.cartX + 0.5, this.cartY + 0.8, this.cartZ + 0.5));
                        if (!(screenPos.z < 0.0) && !(screenPos.z >= 1.0)) {
                            MsdfFont font = Prestige.fontManager.getDefaultFont();
                            float lineHeight = font.getLineHeight();
                            float iconSize = 16.0F;
                            float lineGap = 3.0F;
                            float dividerHeight = 0.5F;
                            float paddingX = 4.0F;
                            float paddingY = 3.0F;
                            float cornerRadius = 5.0F;
                            float etaWidth = font.getTextWidth(etaText);
                            float contentWidth = Math.max(iconSize, etaWidth);
                            float contentHeight = iconSize + lineGap + dividerHeight + lineGap + lineHeight;
                            float boxWidth = contentWidth + 2.0F * paddingX;
                            float boxHeight = contentHeight + 2.0F * paddingY;
                            float centerX = (float)screenPos.x;
                            float centerY = (float)screenPos.y;
                            float boxX = centerX - boxWidth / 2.0F;
                            float boxY = centerY - boxHeight / 2.0F;
                            Matrix4f matrix = new Matrix4f();
                            Color backgroundColor = new Color(14, 14, 14, 200);
                            Color dividerColor = new Color(255, 255, 255, 50);
                            Color textColor = new Color(255, 255, 255, 255);
                            BlurredRoundedRectShader.drawBlurredRect(event.renderer, event.context, matrix, boxX, boxY, boxWidth, boxHeight, 5, cornerRadius);
                            RoundedRectShader.drawRoundedRect(event.renderer, event.context, matrix, boxX, boxY, boxWidth, boxHeight, backgroundColor, cornerRadius);
                            float iconX = centerX - iconSize / 2.0F;
                            float iconY = boxY + paddingY;
                            Render2D.drawTexture(iconX, iconY, iconSize, iconSize, matrix, cartTexture, textColor);
                            float dividerLeft = boxX + paddingX;
                            float dividerRight = boxX + boxWidth - paddingX;
                            float dividerY = iconY + iconSize + lineGap;
                            ShapeRenderer.drawRect(matrix, dividerLeft, dividerY, dividerRight, dividerY + dividerHeight, dividerColor);
                            font.drawText(matrix, etaText, centerX - etaWidth / 2.0F, dividerY + dividerHeight + lineGap, textColor);
                        }
                    }
                }
            }
        }
    }

    private void clickBlock(BlockHitResult hit) {
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            Prestige.silentRotationManager.setActedThisTick(true);
        }
    }

    private boolean isInReach(double targetX, double targetY, double targetZ) {
        Vec3d eyePos = mc.player.getEyePos();
        double reach = PlayerInteractions.getReachDistance();
        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private int findRailSlot() {
        for (int slot = 0; slot < 9; slot++) {
            Item item = mc.player.getInventory().getStack(slot).getItem();

            for (Item railItem : RAIL_ITEMS) {
                if (item == railItem) {
                    return slot;
                }
            }
        }

        return -1;
    }

    private int findFlintAndSteelSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == Items.FLINT_AND_STEEL) {
                return slot;
            }
        }

        return -1;
    }

    private int findChargedCrossbowSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.CROSSBOW) {
                ChargedProjectilesComponent chargedProjectiles = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
                if (chargedProjectiles != null) {
                    for (ItemStack projectile : chargedProjectiles.getProjectiles()) {
                        if (projectile.getItem() == Items.ARROW || projectile.getItem() == Items.SPECTRAL_ARROW) {
                            return slot;
                        }
                    }
                }
            }
        }

        return -1;
    }

    private Integer findTntCartSlot() {
        Integer hotbarSlot = InventoryUtil.findHotbarSlot(Items.TNT_MINECART);
        if (hotbarSlot != null) {
            this.pendingCartSlot = -1;
            return hotbarSlot;
        } else {
            int cartInvSlot = -1;

            for (int slot = 9; slot < 36; slot++) {
                if (mc.player.getInventory().getStack(slot).getItem() == Items.TNT_MINECART) {
                    cartInvSlot = slot;
                    break;
                }
            }

            if (cartInvSlot == -1) {
                return null;
            } else {
                int selectedSlot = InventoryUtil.getSelectedSlot();
                int emptySlot = -1;

                for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
                    if (hotbarIndex != selectedSlot && mc.player.getInventory().getStack(hotbarIndex).isEmpty()) {
                        emptySlot = hotbarIndex;
                        break;
                    }
                }

                if (emptySlot == -1) {
                    return null;
                } else {
                    if (this.pendingCartSlot != -1) {
                        if (Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
                            return this.pendingCartSlot;
                        }

                        this.pendingCartSlot = -1;
                    }

                    InventoryUtil.swapSlot(cartInvSlot, emptySlot);
                    this.pendingCartSlot = emptySlot;
                    return emptySlot;
                }
            }
        }
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.CartIgnitionMode;
import dev.zprestige.prestige.type.AutoCartState;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.lwjgl.glfw.GLFW;

public class AutoCart extends Module implements RotationProvider {
    private IntSetting switchDelay;
    private IntSetting bowBackDelay;
    private IntSetting minCharge;
    private IntSetting arc;
    private KeybindSetting triggerBind;
    private IntSetting overload;
    private KeybindSetting overloadBind;
    private RangeSetting overloadDelay;
    private BoolSetting overloadHotbarOnly;
    private EnumSetting placeMode;
    private BoolSetting onBowShot;
    private BoolSetting autoHitPlaced;
    private FloatSetting autoHitRange;
    private IntSetting autoHitMinCharge;
    private BoolSetting switchBackToBow;
    private BoolSetting safeBlocks;
    private BoolSetting silentAim;
    private FloatSetting silentAimFov;
    private BoolSetting renderPath;
    private ColorSetting renderColor;
    private static final Item[] RAIL_ITEMS = new Item[]{Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
    private static final Block[] RAIL_BLOCKS = new Block[]{Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL};
    private static final Set SAFE_BLOCK_ITEMS = Set.of(
        Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.ENDER_CHEST, Items.RESPAWN_ANCHOR
    );
    private AutoCartState state = AutoCartState.IDLE;
    private CartIgnitionMode ignitionMode = CartIgnitionMode.PASSIVE;
    private int railX;
    private int railY;
    private int railZ;
    private BlockPos landingBlock;
    private boolean useLandingBlock;
    private int previousSlot = -1;
    private int delayTicks = 0;
    private int railRetryTicks = 0;
    private boolean usingCrossbow = false;
    private boolean triggerWasPressed = false;
    private boolean overloadWasPressed = false;
    private boolean overloadActive = false;
    private int cartsPlaced = 0;
    private boolean waitingOverloadDelay = false;
    private StopWatch overloadTimer = new StopWatch();
    private int releaseWaitTicks = 0;
    private boolean shootFirst = false;
    private BlockPos shootTarget = null;
    private boolean skipNextPacket = false;
    private TntMinecartEntity autoHitTarget;
    private boolean autoHitReady = false;
    private Rotation weaponAim;
    private boolean ignoreOwnShot = false;
    private int pulledCartSlot = -1;

    public AutoCart() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.overloadBind.visibleWhen((Predicate<Integer>)ignored -> this.overload.getValue() > 1);
        this.overloadHotbarOnly.visibleWhen((Predicate<Boolean>)ignored -> this.overload.getValue() > 1);
        this.overloadDelay.visibleWhen((Predicate<Float>)ignored -> this.overload.getValue() > 1);
        this.silentAim.visibleWhen((Predicate<Boolean>)ignored -> this.placeMode.getValue().equals("Trajectory"));
        this.silentAimFov
            .visibleWhen(
                (Predicate<Float>)ignored -> this.placeMode.getValue().equals("Trajectory") && this.silentAim.getValue()
            );
        this.renderPath.visibleWhen((Predicate<Boolean>)ignored -> this.placeMode.getValue().equals("Trajectory"));
        this.autoHitRange.visibleWhen((Predicate<Float>)ignored -> this.autoHitPlaced.getValue());
        this.autoHitMinCharge.visibleWhen((Predicate<Integer>)ignored -> this.autoHitPlaced.getValue());
        this.renderColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.placeMode.getValue().equals("Trajectory") && this.renderPath.getValue()
            );
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    private boolean isRailItem(Item item) {
        Item[] rails = RAIL_ITEMS;

        for (Item rail : rails) {
            if (item == rail) {
                return true;
            }
        }

        return false;
    }

    private boolean isRailBlock(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        for (Block rail : RAIL_BLOCKS) {
            if (block == rail) {
                return true;
            }
        }

        return false;
    }

    private int findRailSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (this.isRailItem(mc.player.getInventory().getStack(slot).getItem())) {
                return slot;
            }
        }

        return -1;
    }

    private int findSafeBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (SAFE_BLOCK_ITEMS.contains(stack.getItem())) {
                return slot;
            }
        }

        return -1;
    }

    private Integer findOrPullCartSlot() {
        Integer hotbarCartSlot = InventoryUtil.findHotbarSlot(Items.TNT_MINECART);
        if (hotbarCartSlot != null) {
            this.pulledCartSlot = -1;
            return hotbarCartSlot;
        } else {
            int inventoryCartSlot = -1;

            for (int inventoryIndex = 9; inventoryIndex < 36; inventoryIndex++) {
                if (mc.player.getInventory().getStack(inventoryIndex).getItem() == Items.TNT_MINECART) {
                    inventoryCartSlot = inventoryIndex;
                    break;
                }
            }

            if (inventoryCartSlot == -1) {
                return null;
            } else {
                int selectedSlot = InventoryUtil.getSelectedSlot();
                int emptyHotbarSlot = -1;

                for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
                    if (hotbarIndex != selectedSlot && mc.player.getInventory().getStack(hotbarIndex).isEmpty()) {
                        emptyHotbarSlot = hotbarIndex;
                        break;
                    }
                }

                if (emptyHotbarSlot == -1) {
                    return null;
                } else {
                    if (this.pulledCartSlot != -1) {
                        if (Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
                            return this.pulledCartSlot;
                        }

                        this.pulledCartSlot = -1;
                    }

                    InventoryUtil.swapSlot(inventoryCartSlot, emptyHotbarSlot);
                    this.pulledCartSlot = emptyHotbarSlot;
                    return emptyHotbarSlot;
                }
            }
        }
    }

    private int findChargedCrossbowSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.CROSSBOW && stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isChargedCrossbow(ItemStack stack) {
        return stack.getItem() == Items.CROSSBOW && stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null;
    }

    private void resetSequence() {
        this.state = AutoCartState.IDLE;
        this.ignitionMode = CartIgnitionMode.PASSIVE;
        this.previousSlot = -1;
        this.delayTicks = 0;
        this.railRetryTicks = 0;
        this.usingCrossbow = false;
        this.landingBlock = null;
        this.useLandingBlock = false;
        this.weaponAim = null;
        this.overloadActive = false;
        this.cartsPlaced = 0;
        this.waitingOverloadDelay = false;
        this.releaseWaitTicks = 0;
        this.shootFirst = false;
        this.shootTarget = null;
    }

    @Override
    public void onEnable() {
        this.resetSequence();
        this.triggerWasPressed = this.triggerBind.getValue() != -1 && this.triggerBind.isPressed();
        this.overloadWasPressed = this.overloadBind.getValue() != -1 && this.overloadBind.isPressed();
        this.autoHitTarget = null;
        this.autoHitReady = false;
        this.skipNextPacket = false;
        this.ignoreOwnShot = false;
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.restoreUseKeyState();
        this.resetSequence();
        this.autoHitTarget = null;
        this.autoHitReady = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private void restoreUseKeyState() {
        int keyCode = new KeyBindingAccessor(mc.options.useKey).getBoundKey().getCode();
        boolean physicallyPressed;
        if (keyCode < 8) {
            physicallyPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), keyCode) == 1;
        } else {
            physicallyPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
        }

        mc.options.useKey.setPressed(physicallyPressed);
    }

    private void finishPlacement() {
        this.waitingOverloadDelay = false;
        if (this.ignitionMode == CartIgnitionMode.BOW && !this.shootFirst) {
            this.delayTicks = this.switchDelay.getValue();
            this.state = AutoCartState.SWITCH_WEAPON;
        } else {
            this.delayTicks = this.bowBackDelay.getValue();
            this.state = AutoCartState.WAIT_BACK;
        }
    }

    private void placeAtPredictedLanding(float power, float yaw, float pitch) {
        BlockPos landing = this.predictArrowLanding(yaw, pitch, power);
        if (landing != null
            && !this.hasTntCartAt(landing.up())
            && !this.hasTntCartAt(landing)
            && mc.world.getBlockState(landing.up()).isReplaceable()
            && this.isTopFaceInReach(landing)) {
            this.landingBlock = landing;
            this.useLandingBlock = true;
            this.state = AutoCartState.SWITCH_RAIL;
            this.delayTicks = 0;
        } else {
            this.resetSequence();
        }
    }

    private void placeAtServerAimLanding(float power) {
        Rotation serverRotation = Prestige.silentRotationManager.getServerRotation();
        this.placeAtPredictedLanding(power, serverRotation != null ? serverRotation.getYaw() : mc.player.getYaw(), serverRotation != null ? serverRotation.getPitch() : mc.player.getPitch());
    }

    private BlockPos predictArrowLanding(float yaw, float pitch, float power) {
        if (mc.player != null && mc.world != null) {
            Vec3d eyePos = mc.player.getEyePos();
            double posX = eyePos.x;
            double posY = eyePos.y - 0.1;
            double posZ = eyePos.z;
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double motionX = -MathHelper.sin((float)yawRad) * MathHelper.cos((float)pitchRad);
            double motionY = -MathHelper.sin((float)pitchRad);
            double motionZ = MathHelper.cos((float)yawRad) * MathHelper.cos((float)pitchRad);
            double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            if (length == 0.0) {
                return null;
            } else {
                motionX /= length;
                motionY /= length;
                motionZ /= length;
                float speed = MathHelper.clamp(power, 0.0F, 1.05F) * 3.0F;
                motionX *= speed;
                motionY *= speed;
                motionZ *= speed;
                motionX += mc.player.getVelocity().x;
                motionZ += mc.player.getVelocity().z;
                if (!mc.player.isOnGround()) {
                    motionY += mc.player.getVelocity().y;
                }

                for (int step = 0; step < 300; step++) {
                    Vec3d segmentStart = new Vec3d(posX, posY, posZ);
                    posX += motionX;
                    posY += motionY;
                    posZ += motionZ;
                    motionX *= 0.99;
                    motionY *= 0.99;
                    motionZ *= 0.99;
                    motionY -= 0.05;
                    Vec3d segmentEnd = new Vec3d(posX, posY, posZ);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(segmentStart, segmentEnd, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
                    if (hit != null && hit.getType() == Type.BLOCK) {
                        return hit.getBlockPos();
                    }

                    if (posY < -65.0) {
                        break;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    private Rotation computeBowAim(Vec3d target, float power) {
        Vec3d eyePos = mc.player.getEyePos();
        float yaw = (float)Math.toDegrees(Math.atan2(target.z - eyePos.z, target.x - eyePos.x)) - 90.0F;
        ArrayList candidates = new ArrayList();
        double bestDistSq = Double.MAX_VALUE;

        for (float pitch = -89.0F; pitch <= 60.0F; pitch += 0.5F) {
            BlockPos landing = this.predictArrowLanding(yaw, pitch, power);
            if (landing != null) {
                double distSq = landing.toCenterPos().squaredDistanceTo(target);
                candidates.add(new float[]{pitch, (float)distSq});
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                }
            }
        }

        if (bestDistSq > 4.0) {
            return null;
        } else {
            double maxDistSq = bestDistSq + 0.25;
            candidates.removeIf(candidate -> ((float[])candidate)[1] > maxDistSq);
            float chosenPitch = ((float[])candidates.get(Math.min((int)Math.round(this.arc.getValue().intValue() / 100.0 * (candidates.size() - 1)), candidates.size() - 1)))[0];

            for (float finePitch = chosenPitch - 0.5F; finePitch <= chosenPitch + 0.5F; finePitch += 0.05F) {
                BlockPos fineLanding = this.predictArrowLanding(yaw, finePitch, power);
                if (fineLanding != null) {
                    double fineDistSq = fineLanding.toCenterPos().squaredDistanceTo(target);
                    if (fineDistSq < bestDistSq) {
                        bestDistSq = fineDistSq;
                        chosenPitch = finePitch;
                    }
                }
            }

            Rotation limitedRotation = Prestige.silentRotationManager.getRotationTowards(target);
            if (limitedRotation == null) {
                return new Rotation(yaw, chosenPitch);
            } else {
                Rotation aim = new Rotation(limitedRotation.getYaw(), chosenPitch);
                aim.setClamped(limitedRotation.isClamped());
                return aim;
            }
        }
    }

    private void interactBlockAndSwing(BlockHitResult hitResult) {
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            Prestige.silentRotationManager.setActedThisTick(true);
        }
    }

    private boolean isTopFaceInReach(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        double reach = PlayerInteractions.getReachDistance();
        double dx = pos.getX() + 0.5 - eyePos.x;
        double dy = pos.getY() + 1.0 - eyePos.y;
        double dz = pos.getZ() + 0.5 - eyePos.z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private boolean isInReach(double targetX, double targetY, double targetZ) {
        Vec3d eyePos = mc.player.getEyePos();
        double reach = PlayerInteractions.getReachDistance();
        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private boolean hasTntCartAt(BlockPos pos) {
        if (mc.world == null) {
            return false;
        } else {
            for (Entity entity : mc.world.getOtherEntities(null, new Box(pos))) {
                if (entity instanceof TntMinecartEntity) {
                    return true;
                }
            }

            return false;
        }
    }

    private TntMinecartEntity findAutoHitCart() {
        if (mc.world != null && mc.player != null) {
            Vec3d eyePos = mc.player.getEyePos();
            float maxRange = this.autoHitRange.getValue();
            TntMinecartEntity bestCart = null;
            double bestScore = Double.MAX_VALUE;
            Vec3d lookVec = mc.player.getRotationVec(1.0F);

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof TntMinecartEntity cart) {
                    double distance = eyePos.distanceTo(PositionUtil.getEntityPos(cart));
                    if (!(distance > maxRange)) {
                        Vec3d toCart = PositionUtil.getEntityPos(cart).subtract(eyePos);
                        if (!(Math.sqrt(toCart.x * toCart.x + toCart.z * toCart.z) < 1.0E-6)) {
                            double score = Math.acos(MathHelper.clamp((float)lookVec.dotProduct(toCart.normalize()), -1.0F, 1.0F)) * 8.0 + distance * 0.05;
                            if (score < bestScore) {
                                bestScore = score;
                                bestCart = cart;
                            }
                        }
                    }
                }
            }

            return bestCart;
        } else {
            return null;
        }
    }

    private boolean isDrawingBow() {
        if (mc.player == null) {
            return false;
        } else {
            return !mc.player.isUsingItem() ? false : mc.player.getActiveItem().getItem() == Items.BOW;
        }
    }

    private int getChargeTicks() {
        return Math.max(this.minCharge.getValue(), 3);
    }

    private boolean isOverloadHeld() {
        return this.overload.getValue() > 1 && this.overloadBind.getValue() != -1 && this.overloadBind.isPressed();
    }

    private float getShotPower() {
        if (this.findChargedCrossbowSlot() != -1) {
            return 1.0500001F;
        } else {
            return InventoryUtil.findHotbarSlot(Items.BOW) != null ? BowItem.getPullProgress(this.getChargeTicks()) : -1.0F;
        }
    }

    private boolean isHoldingBowOrCrossbow() {
        if (mc.player == null) {
            return false;
        } else {
            ItemStack heldStack = mc.player.getMainHandStack();
            return heldStack.getItem() == Items.BOW ? true : this.isChargedCrossbow(heldStack);
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (this.skipNextPacket) {
            this.skipNextPacket = false;
        } else if (this.onBowShot.getValue()) {
            if (this.state == AutoCartState.IDLE) {
                if (!this.ignoreOwnShot) {
                    if (mc.player != null && mc.world != null) {
                        ItemStack heldStack = mc.player.getMainHandStack();
                        Item heldItem = heldStack.getItem();
                        boolean shotFired = false;
                        float shotPower = 1.0F;
                        if (event.getPacket() instanceof PlayerActionC2SPacket actionPacket && actionPacket.getAction() == Action.RELEASE_USE_ITEM && heldItem == Items.BOW) {
                            int useTicks = mc.player.getItemUseTime();
                            if (useTicks < 4) {
                                return;
                            }

                            shotFired = true;
                            shotPower = BowItem.getPullProgress(useTicks);
                        } else if (event.getPacket() instanceof PlayerInteractItemC2SPacket && this.isChargedCrossbow(heldStack)) {
                            shotFired = true;
                            shotPower = 1.0500001F;
                        }

                        if (shotFired) {
                            if (this.findRailSlot() != -1) {
                                if (this.findOrPullCartSlot() != null) {
                                    BlockPos landing = null;
                                    if (this.placeMode.getValue().equals("Trajectory")) {
                                        landing = this.predictArrowLanding(mc.player.getYaw(), mc.player.getPitch(), shotPower);
                                        if (landing == null) {
                                            return;
                                        }

                                        if (this.hasTntCartAt(landing.up()) || this.hasTntCartAt(landing)) {
                                            return;
                                        }

                                        if (!mc.world.getBlockState(landing.up()).isReplaceable()) {
                                            return;
                                        }

                                        if (!this.isTopFaceInReach(landing)) {
                                            return;
                                        }
                                    }

                                    this.previousSlot = InventoryUtil.getSelectedSlot();
                                    this.ignitionMode = CartIgnitionMode.PASSIVE;
                                    if (landing != null) {
                                        this.landingBlock = landing;
                                        this.useLandingBlock = true;
                                    }

                                    this.overloadActive = this.isOverloadHeld();
                                    this.cartsPlaced = 0;
                                    this.state = AutoCartState.SWITCH_RAIL;
                                    this.delayTicks = 0;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            boolean triggerPressed = this.triggerBind.getValue() != -1 && this.triggerBind.isPressed();
            boolean triggerJustPressed = triggerPressed && !this.triggerWasPressed;
            this.triggerWasPressed = triggerPressed;
            boolean overloadPressed = this.overloadBind.getValue() != -1 && this.overloadBind.isPressed();
            boolean overloadJustPressed = overloadPressed && !this.overloadWasPressed;
            this.overloadWasPressed = overloadPressed;
            if (!mc.options.useKey.isPressed()) {
                this.ignoreOwnShot = false;
            }

            if (this.state == AutoCartState.IDLE && (triggerJustPressed || overloadJustPressed)) {
                boolean overloadHeld = this.isOverloadHeld();
                boolean hasWeapon = this.findChargedCrossbowSlot() != -1 || InventoryUtil.findHotbarSlot(Items.BOW) != null;
                if (this.findRailSlot() != -1 && this.findOrPullCartSlot() != null && (!overloadHeld || hasWeapon)) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    this.ignitionMode = CartIgnitionMode.BOW;
                    this.shootFirst = overloadHeld;
                    this.shootTarget = overloadHeld && mc.crosshairTarget instanceof BlockHitResult aimedBlockHit ? aimedBlockHit.getBlockPos() : null;
                    this.landingBlock = null;
                    this.useLandingBlock = false;
                    this.ignoreOwnShot = true;
                    this.overloadActive = this.isOverloadHeld();
                    this.cartsPlaced = 0;
                    this.state = overloadHeld ? AutoCartState.SWITCH_WEAPON : AutoCartState.SWITCH_RAIL;
                    this.delayTicks = 0;
                }
            }

            if (this.autoHitReady && this.state == AutoCartState.IDLE && this.isDrawingBow()) {
                this.skipNextPacket = true;
                mc.options.useKey.setPressed(false);
                if (mc.player.isUsingItem()) {
                    mc.interactionManager.stopUsingItem(mc.player);
                }

                this.autoHitReady = false;
                this.autoHitTarget = null;
            } else {
                if (this.autoHitPlaced.getValue() && this.state == AutoCartState.IDLE && this.isDrawingBow()) {
                    TntMinecartEntity nearestCart = this.findAutoHitCart();
                    this.autoHitTarget = nearestCart;
                    if (nearestCart != null && mc.player.getItemUseTime() >= this.autoHitMinCharge.getValue()) {
                        Rotation autoHitRotation = Prestige.silentRotationManager.getActiveRotation();
                        if (autoHitRotation != null && !autoHitRotation.isClamped()) {
                            this.autoHitReady = true;
                        }
                    }
                } else {
                    this.autoHitTarget = null;
                    this.autoHitReady = false;
                }

                if (this.state != AutoCartState.IDLE) {
                    switch (this.state) {
                        case SWITCH_RAIL:
                            int railSlot = this.findRailSlot();
                            if (railSlot == -1) {
                                this.resetSequence();
                                return;
                            }

                            BlockHitResult railHit = this.getRailPlaceHit();
                            if (railHit == null) {
                                if (++this.railRetryTicks > 10) {
                                    this.resetSequence();
                                }

                                return;
                            }

                            if (!this.isInReach(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5)) {
                                this.resetSequence();
                                return;
                            }

                            InventoryUtil.selectSlot(railSlot);
                            this.interactBlockAndSwing(railHit);
                            this.delayTicks = this.switchDelay.getValue();
                            this.state = AutoCartState.SWITCH_CART;
                            break;
                        case SWITCH_CART:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            if (this.waitingOverloadDelay) {
                                if (!this.overloadTimer.hasElapsedRandom(this.overloadDelay)) {
                                    return;
                                }

                                this.waitingOverloadDelay = false;
                            }

                            Integer cartSlot = this.overloadHotbarOnly.getValue() && this.cartsPlaced > 0
                                ? InventoryUtil.findHotbarSlot(Items.TNT_MINECART)
                                : this.findOrPullCartSlot();
                            if (cartSlot == null) {
                                if (this.cartsPlaced > 0) {
                                    this.finishPlacement();
                                } else {
                                    this.resetSequence();
                                }

                                return;
                            }

                            if (mc.player.getInventory().getStack(cartSlot).getItem() != Items.TNT_MINECART) {
                                return;
                            }

                            if (!this.isInReach(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5)) {
                                this.resetSequence();
                                return;
                            }

                            BlockHitResult cartHit = new BlockHitResult(
                                new Vec3d(this.railX + 0.5, this.railY + 0.125, this.railZ + 0.5), Direction.UP, new BlockPos(this.railX, this.railY, this.railZ), false
                            );
                            if (this.cartsPlaced >= 1) {
                                InventoryUtil.runWithSlot(cartSlot, () -> this.interactBlockAndSwing(cartHit), true);
                            } else {
                                InventoryUtil.selectSlot(cartSlot);
                                PlayerInteractions.interactBlock(cartHit);
                            }

                            this.cartsPlaced++;
                            if ((this.overloadActive || this.isOverloadHeld()) && this.cartsPlaced < this.overload.getValue()) {
                                this.overloadTimer.reset();
                                this.overloadDelay.reroll();
                                this.waitingOverloadDelay = true;
                                return;
                            }

                            if (this.safeBlocks.getValue()) {
                                this.state = AutoCartState.SWITCH_SAFE;
                                this.delayTicks = this.bowBackDelay.getValue();
                            } else {
                                this.finishPlacement();
                            }
                            break;
                        case SWITCH_SAFE:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            int safeBlockSlot = this.findSafeBlockSlot();
                            if (safeBlockSlot == -1) {
                                this.finishPlacement();
                                return;
                            }

                            if (mc.crosshairTarget instanceof BlockHitResult safeBlockHit && safeBlockHit.getSide() == Direction.UP) {
                                BlockPos safeBlockPos = safeBlockHit.getBlockPos();
                                Vec3d playerPos = PositionUtil.getPlayerPos();
                                Vec3d cartCenter = new Vec3d(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5);
                                Vec3d safeBlockCenter = new Vec3d(safeBlockPos.getX() + 0.5, safeBlockPos.getY() + 0.5, safeBlockPos.getZ() + 0.5);
                                double cartDistance = playerPos.distanceTo(cartCenter);
                                double safeBlockDistance = playerPos.distanceTo(safeBlockCenter);
                                if (safeBlockPos.getY() <= (int)Math.floor(playerPos.y)
                                    && safeBlockDistance < cartDistance
                                    && !this.isRailBlock(safeBlockPos)
                                    && !mc.world.getBlockState(safeBlockPos).isReplaceable()) {
                                    if (this.isInReach(safeBlockPos.getX() + 0.5, safeBlockPos.getY() + 1.0, safeBlockPos.getZ() + 0.5)) {
                                        InventoryUtil.selectSlot(safeBlockSlot);
                                        this.interactBlockAndSwing(safeBlockHit);
                                    }
                                }
                            }

                            this.finishPlacement();
                            break;
                        case WAIT_BACK:
                            if (!this.switchBackToBow.getValue()) {
                                this.resetSequence();
                                return;
                            }

                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            if (this.previousSlot != -1) {
                                InventoryUtil.selectSlot(this.previousSlot);
                            }

                            this.resetSequence();
                            break;
                        case SWITCH_WEAPON:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            int crossbowSlot = this.findChargedCrossbowSlot();
                            Integer bowSlot = InventoryUtil.findHotbarSlot(Items.BOW);
                            if (crossbowSlot != -1) {
                                InventoryUtil.selectSlot(crossbowSlot);
                                this.usingCrossbow = true;
                            } else {
                                if (bowSlot == null) {
                                    this.resetSequence();
                                    return;
                                }

                                InventoryUtil.selectSlot(bowSlot);
                                this.usingCrossbow = false;
                            }

                            this.state = AutoCartState.USE_WEAPON;
                            break;
                        case USE_WEAPON:
                            if (this.usingCrossbow) {
                                this.ignoreOwnShot = true;
                                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                                if (this.shootFirst) {
                                    this.placeAtServerAimLanding(1.0500001F);
                                } else {
                                    this.resetSequence();
                                }
                            } else {
                                mc.options.useKey.setPressed(true);
                                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                                this.delayTicks = this.minCharge.getValue();
                                this.state = AutoCartState.CHARGE_BOW;
                            }
                            break;
                        case CHARGE_BOW:
                            if (!mc.options.useKey.isPressed()) {
                                mc.options.useKey.setPressed(true);
                            }

                            if (mc.player.getItemUseTime() < this.getChargeTicks()) {
                                return;
                            }

                            this.releaseWaitTicks = 0;
                            this.state = AutoCartState.RELEASE_BOW;
                            break;
                        case RELEASE_BOW:
                            Rotation currentRotation = Prestige.silentRotationManager.getActiveRotation();
                            if (this.weaponAim != null && currentRotation != null && currentRotation.isClamped() && this.releaseWaitTicks++ < 10) {
                                if (!mc.options.useKey.isPressed()) {
                                    mc.options.useKey.setPressed(true);
                                }

                                return;
                            }

                            float pullProgress = BowItem.getPullProgress(mc.player.getItemUseTime());
                            Rotation serverRotation = Prestige.silentRotationManager.getServerRotation();
                            float shotYaw = serverRotation != null ? serverRotation.getYaw() : mc.player.getYaw();
                            float shotPitch = serverRotation != null ? serverRotation.getPitch() : mc.player.getPitch();
                            this.restoreUseKeyState();
                            if (mc.player.isUsingItem()) {
                                this.ignoreOwnShot = true;
                                mc.interactionManager.stopUsingItem(mc.player);
                            }

                            if (this.shootFirst) {
                                this.placeAtPredictedLanding(pullProgress, shotYaw, shotPitch);
                            } else {
                                this.resetSequence();
                            }
                    }
                }
            }
        } else {
            this.resetSequence();
        }
    }

    private BlockHitResult getRailPlaceHit() {
        if (this.useLandingBlock && this.landingBlock != null) {
            this.railX = this.landingBlock.getX();
            this.railY = this.landingBlock.getY() + 1;
            this.railZ = this.landingBlock.getZ();
            return new BlockHitResult(new Vec3d(this.railX + 0.5, this.landingBlock.getY() + 1.0, this.railZ + 0.5), Direction.UP, this.landingBlock, false);
        } else if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
            if (crosshairHit.getSide() != Direction.UP) {
                return null;
            } else {
                BlockPos hitPos = crosshairHit.getBlockPos();
                this.railX = hitPos.getX();
                this.railY = hitPos.getY() + 1;
                this.railZ = hitPos.getZ();
                return crosshairHit;
            }
        } else {
            return null;
        }
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player != null && mc.world != null) {
            if (this.autoHitPlaced.getValue() && this.state == AutoCartState.IDLE && this.isDrawingBow()) {
                TntMinecartEntity targetCart = this.autoHitTarget != null ? this.autoHitTarget : this.findAutoHitCart();
                if (targetCart != null) {
                    Rotation autoHitAim = this.computeBowAim(PositionUtil.getEntityPos(targetCart).add(0.0, 0.25, 0.0), 1.0F);
                    if (autoHitAim != null) {
                        if (Math.abs(RotationUtil.wrapDegrees(autoHitAim.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                            return autoHitAim;
                        }
                    }
                }
            }

            if (this.ignitionMode == CartIgnitionMode.BOW
                && this.shootFirst
                && this.shootTarget != null
                && (this.state == AutoCartState.USE_WEAPON || this.state == AutoCartState.CHARGE_BOW || this.state == AutoCartState.RELEASE_BOW)) {
                float shotPower = this.getShotPower();
                Rotation computedShotAim;
                if (shotPower > 0.0F) {
                    computedShotAim = this.computeBowAim(this.shootTarget.toCenterPos(), shotPower);
                } else {
                    computedShotAim = null;
                }

                Rotation shotAim = computedShotAim;
                this.weaponAim = shotAim;
                if (shotAim != null) {
                    if (Math.abs(RotationUtil.wrapDegrees(shotAim.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                        return shotAim;
                    }
                }
            }

            if (this.ignitionMode == CartIgnitionMode.BOW
                && !this.shootFirst
                && (
                    this.state == AutoCartState.SWITCH_SAFE
                        || this.state == AutoCartState.SWITCH_WEAPON
                        || this.state == AutoCartState.USE_WEAPON
                        || this.state == AutoCartState.CHARGE_BOW
                        || this.state == AutoCartState.RELEASE_BOW
                )) {
                Vec3d cartCenter = new Vec3d(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5);
                float ignitePower = this.getShotPower();
                Rotation computedIgniteAim;
                if (ignitePower > 0.0F) {
                    computedIgniteAim = this.computeBowAim(cartCenter, ignitePower);
                } else {
                    computedIgniteAim = null;
                }

                Rotation igniteAim = computedIgniteAim;
                this.weaponAim = igniteAim;
                if (igniteAim != null) {
                    if (Math.abs(RotationUtil.wrapDegrees(igniteAim.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                        return igniteAim;
                    }
                }
            }

            if (this.silentAim.getValue()
                && this.placeMode.getValue().equals("Trajectory")
                && this.useLandingBlock
                && this.landingBlock != null
                && (this.state == AutoCartState.SWITCH_RAIL || this.state == AutoCartState.SWITCH_CART)) {
                BlockPos railPos = this.landingBlock.up();
                Vec3d railCenter = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5);
                Rotation railAim = Prestige.silentRotationManager.getRotationTowards(railCenter);
                if (railAim != null) {
                    if (Math.abs(RotationUtil.wrapDegrees(railAim.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                        return railAim;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (this.renderPath.getValue()) {
            if (this.placeMode.getValue().equals("Trajectory")) {
                if (mc.player != null && mc.world != null) {
                    if (this.isHoldingBowOrCrossbow()) {
                        Rotation serverRotation = Prestige.silentRotationManager.getServerRotation();
                        float yaw = serverRotation != null ? serverRotation.getYaw() : mc.player.getYaw();
                        float pitch = serverRotation != null ? serverRotation.getPitch() : mc.player.getPitch();
                        ItemStack heldStack = mc.player.getMainHandStack();
                        float power;
                        if (heldStack.getItem() == Items.BOW) {
                            if (!mc.player.isUsingItem()) {
                                return;
                            }

                            power = BowItem.getPullProgress(mc.player.getItemUseTime());
                            if (power < 0.05F) {
                                return;
                            }
                        } else {
                            if (!this.isChargedCrossbow(heldStack)) {
                                return;
                            }

                            power = 1.0500001F;
                        }

                        BlockPos landing = this.predictArrowLanding(yaw, pitch, power);
                        if (landing != null) {
                            BlockPos railPos = landing.up();
                            float minX = railPos.getX();
                            float minY = railPos.getY();
                            float minZ = railPos.getZ();
                            float maxX = minX + 1.0F;
                            float maxY = minY + 1.0F;
                            float maxZ = minZ + 1.0F;
                            Color fillColor = this.renderColor.getValue();
                            Color outlineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), Math.min(255, fillColor.getAlpha() + 100));
                            ShapeRenderer.drawFilledBox(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, fillColor);
                            ShapeRenderer.drawBoxOutline(event.context, event.renderer, minX, minY, minZ, maxX, maxY, maxZ, outlineColor);
                        }
                    }
                }
            }
        }
    }
}

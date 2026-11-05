package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.type.XbowCartState;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class XbowCart extends Module implements RotationProvider {
    private IntSetting switchDelay;
    private BoolSetting silentAim;
    private FloatSetting silentAimFov;
    private static final Item[] RAIL_ITEMS = new Item[]{Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
    private XbowCartState state = XbowCartState.IDLE;
    private int railX;
    private int railY;
    private int railZ;
    private BlockPos firePos;
    private int previousSlot = -1;
    private int delayTicks = 0;
    private int railRetryTicks = 0;
    private int elapsedTicks = 0;
    private int aimWaitTicks = 0;
    private int pulledCartSlot = -1;
    private Rotation shotRotation;

    public XbowCart() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.silentAimFov.visibleWhen((Predicate<Float>)ignored -> this.silentAim.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.previousSlot = InventoryUtil.getSelectedSlot();
        if (this.findLoadedCrossbowSlot() == -1) {
            this.abort();
        } else if (this.findRailSlot() == -1) {
            this.abort();
        } else if (this.findOrPullCartSlot() == null) {
            this.abort();
        } else if (this.findFlintAndSteelSlot() == -1) {
            this.abort();
        } else {
            this.state = XbowCartState.SWITCH_RAIL;
            Prestige.silentRotationManager.addRotationProvider(this);
        }
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.resetState();
    }

    private void resetState() {
        this.state = XbowCartState.IDLE;
        this.firePos = null;
        this.previousSlot = -1;
        this.delayTicks = 0;
        this.railRetryTicks = 0;
        this.elapsedTicks = 0;
        this.aimWaitTicks = 0;
        this.pulledCartSlot = -1;
        this.shotRotation = null;
    }

    private void abort() {
        this.disableSelf();
    }

    private void finishAndSwitchBack() {
        if (this.previousSlot != -1) {
            InventoryUtil.selectSlot(this.previousSlot);
        }

        this.disableSelf();
    }

    private void disableSelf() {
        if (this.isEnabled()) {
            this.toggle();
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.state != XbowCartState.IDLE) {
                if (++this.elapsedTicks > 80) {
                    this.abort();
                } else {
                    switch (this.state) {
                        case SWITCH_RAIL:
                            int railSlot = this.findRailSlot();
                            if (railSlot == -1) {
                                this.abort();
                                return;
                            }

                            if (mc.crosshairTarget instanceof BlockHitResult crosshairHit && crosshairHit.getSide() == Direction.UP) {
                                BlockPos basePos = crosshairHit.getBlockPos();
                                this.railX = basePos.getX();
                                this.railY = basePos.getY() + 1;
                                this.railZ = basePos.getZ();
                                if (!mc.world.getBlockState(basePos.up()).isReplaceable()) {
                                    this.abort();
                                    return;
                                }

                                if (!this.isInReach(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5)) {
                                    this.abort();
                                    return;
                                }

                                InventoryUtil.selectSlot(railSlot);
                                this.interactBlockAndSwing(
                                    new BlockHitResult(new Vec3d(this.railX + 0.5, basePos.getY() + 1.0, this.railZ + 0.5), Direction.UP, basePos, false)
                                );
                                this.delayTicks = this.switchDelay.getValue();
                                this.state = XbowCartState.SWITCH_CART;
                                break;
                            }

                            if (++this.railRetryTicks > 10) {
                                this.abort();
                            }

                            return;
                        case SWITCH_CART:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            Integer cartSlot = this.findOrPullCartSlot();
                            if (cartSlot == null) {
                                this.abort();
                                return;
                            }

                            if (mc.player.getInventory().getStack(cartSlot).getItem() != Items.TNT_MINECART) {
                                return;
                            }

                            if (!this.isInReach(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5)) {
                                this.abort();
                                return;
                            }

                            InventoryUtil.selectSlot(cartSlot);
                            PlayerInteractions.interactBlock(
                                new BlockHitResult(
                                        new Vec3d(this.railX + 0.5, this.railY + 0.125, this.railZ + 0.5), Direction.UP, new BlockPos(this.railX, this.railY, this.railZ), false
                                    )
                            );
                            this.delayTicks = this.switchDelay.getValue();
                            this.state = XbowCartState.SWITCH_FIRE;
                            break;
                        case SWITCH_FIRE:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            if (this.firePos == null) {
                                this.firePos = this.findFirePos();
                                if (this.firePos == null) {
                                    this.abort();
                                    return;
                                }
                            }

                            int flintSlot = this.findFlintAndSteelSlot();
                            if (flintSlot == -1) {
                                this.abort();
                                return;
                            }

                            BlockPos fireBase = this.firePos.down();
                            InventoryUtil.selectSlot(flintSlot);
                            this.interactBlockAndSwing(
                                new BlockHitResult(new Vec3d(fireBase.getX() + 0.5, fireBase.getY() + 1.0, fireBase.getZ() + 0.5), Direction.UP, fireBase, false)
                            );
                            this.delayTicks = this.switchDelay.getValue();
                            this.state = XbowCartState.SWITCH_XBOW;
                            break;
                        case SWITCH_XBOW:
                            if (this.delayTicks > 0) {
                                this.delayTicks--;
                                return;
                            }

                            int crossbowSlot = this.findLoadedCrossbowSlot();
                            if (crossbowSlot == -1) {
                                this.abort();
                                return;
                            }

                            InventoryUtil.selectSlot(crossbowSlot);
                            this.aimWaitTicks = 0;
                            this.state = XbowCartState.FIRE;
                            break;
                        case FIRE:
                            if (this.shotRotation == null) {
                                this.abort();
                                return;
                            }

                            if (InventoryUtil.isHolding(Items.CROSSBOW)) {
                                Rotation activeRotation = Prestige.silentRotationManager.getActiveRotation();
                                if (activeRotation != null && activeRotation.isClamped() && this.aimWaitTicks++ < 5) {
                                    return;
                                }

                                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                                Prestige.silentRotationManager.setActedThisTick(true);
                                this.finishAndSwitchBack();
                            } else {
                                int reselectCrossbowSlot = this.findLoadedCrossbowSlot();
                                if (reselectCrossbowSlot == -1) {
                                    this.abort();
                                    return;
                                }

                                InventoryUtil.selectSlot(reselectCrossbowSlot);
                            }
                    }
                }
            }
        }
    }

    private Vec3d getCartAimPoint() {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d cartCenter = new Vec3d(this.railX + 0.5, this.railY + 0.45, this.railZ + 0.5);
        int flightTicks = (int)Math.ceil(eyePos.distanceTo(cartCenter) / 3.15);
        return cartCenter.add(0.0, 0.05 * flightTicks * (flightTicks + 1) / 2.0, 0.0);
    }

    private Rotation computeRotationTo(Vec3d target) {
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dy = target.y - (eyePos.y - 0.1);
        double dz = target.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-6) {
            return null;
        } else {
            return new Rotation((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float)(-Math.toDegrees(Math.asin(dy / distance))));
        }
    }

    private boolean doesShotIgniteCart(float yaw, float pitch, BlockPos fireBlock) {
        Vec3d eyePos = mc.player.getEyePos();
        double posX = eyePos.x;
        double posY = eyePos.y - 0.1;
        double posZ = eyePos.z;
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

        Box cartBox = new Box(this.railX + 0.01, this.railY + 0.0625, this.railZ + 0.01, this.railX + 0.99, this.railY + 0.7625, this.railZ + 0.99);
        BlockPos cartPos = new BlockPos(this.railX, this.railY, this.railZ);
        boolean passedFire = false;

        for (int tick = 0; tick < 60; tick++) {
            Vec3d tickStart = new Vec3d(posX, posY, posZ);
            int substeps = Math.max(1, (int)Math.ceil(Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ) / 0.25));

            for (int substep = 0; substep < substeps; substep++) {
                posX += motionX / substeps;
                posY += motionY / substeps;
                posZ += motionZ / substeps;
                if (!passedFire && fireBlock != null && new Box(fireBlock).contains(posX, posY, posZ)) {
                    passedFire = true;
                }

                if (cartBox.contains(posX, posY, posZ)) {
                    return passedFire;
                }
            }

            Vec3d tickEnd = new Vec3d(posX, posY, posZ);
            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(tickStart, tickEnd, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
            if (blockHit != null && blockHit.getType() == Type.BLOCK && !blockHit.getBlockPos().equals(cartPos)) {
                return false;
            }

            if (posY < this.railY - 3) {
                return false;
            }

            motionX *= 0.99;
            motionZ *= 0.99;
            motionY = motionY * 0.99 - 0.05;
        }

        return false;
    }

    private BlockPos findFirePos() {
        Rotation aimRotation = this.computeRotationTo(this.getCartAimPoint());
        if (aimRotation == null) {
            return null;
        } else {
            Vec3d eyePos = mc.player.getEyePos();
            double posX = eyePos.x;
            double posY = eyePos.y - 0.1;
            double posZ = eyePos.z;
            double yawRad = Math.toRadians(aimRotation.getYaw());
            double pitchRad = Math.toRadians(aimRotation.getPitch());
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

            BlockPos cartPos = new BlockPos(this.railX, this.railY, this.railZ);
            Box playerBox = mc.player.getBoundingBox().expand(0.3);

            for (int tick = 0; tick < 60; tick++) {
                int substeps = Math.max(1, (int)Math.ceil(Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ) / 0.25));

                for (int substep = 0; substep < substeps; substep++) {
                    posX += motionX / substeps;
                    posY += motionY / substeps;
                    posZ += motionZ / substeps;
                    BlockPos candidate = BlockPos.ofFloored(posX, posY, posZ);
                    if (candidate.equals(cartPos) || candidate.equals(cartPos.up())) {
                        return null;
                    }

                    if (!playerBox.intersects(new Box(candidate))) {
                        double centerX = candidate.getX() + 0.5;
                        double baseY = candidate.getY();
                        if (this.isInReach(centerX, baseY, candidate.getZ() + 0.5)
                            && mc.world.getBlockState(candidate).isReplaceable()
                            && mc.world.getBlockState(candidate.down()).isSideSolidFullSquare(mc.world, candidate.down(), Direction.UP)) {
                            return candidate;
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

    @Override
    public Rotation provideRotation() {
        if (mc.player == null || mc.world == null || !this.silentAim.getValue()) {
            return null;
        } else if (this.state != XbowCartState.SWITCH_XBOW && this.state != XbowCartState.FIRE) {
            Vec3d placeTarget = null;
            if (this.state == XbowCartState.SWITCH_CART) {
                placeTarget = new Vec3d(this.railX + 0.5, this.railY + 0.5, this.railZ + 0.5);
            } else if (this.state == XbowCartState.SWITCH_FIRE && this.firePos != null) {
                placeTarget = this.firePos.toCenterPos();
            }

            if (placeTarget != null) {
                Rotation placeRotation = Prestige.silentRotationManager.getRotationTowards(placeTarget);
                if (placeRotation != null) {
                    if (Math.abs(RotationUtil.wrapDegrees(placeRotation.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                        return placeRotation;
                    }
                }
            }

            return null;
        } else {
            Rotation shotAim = this.computeRotationTo(this.getCartAimPoint());
            if (shotAim != null) {
                if (this.doesShotIgniteCart(shotAim.getYaw(), shotAim.getPitch(), this.firePos)) {
                    this.shotRotation = shotAim;
                    if (Math.abs(RotationUtil.wrapDegrees(shotAim.getYaw() - mc.player.getYaw())) <= this.silentAimFov.getValue()) {
                        return shotAim;
                    }
                }
            }

            return null;
        }
    }

    private void interactBlockAndSwing(BlockHitResult hitResult) {
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted()) {
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

    private int findLoadedCrossbowSlot() {
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
}

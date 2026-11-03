package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.AuraCartPlacement;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CartAura extends Module implements RotationProvider {
    private RangeSetting reactionDelay;
    private FloatSetting swapDelay;
    private BoolSetting swapBack;
    private FloatSetting swapBackDelay;
    private RangeSetting attackDelay;
    private FloatSetting targetFov;
    private BoolSetting humanizedAim;
    private StopWatch reactionTimer = new StopWatch();
    private StopWatch swapTimer = new StopWatch();
    private StopWatch swapBackTimer = new StopWatch();
    private StopWatch attackTimer = new StopWatch();
    private List ownCartPlacements = new ArrayList();
    private TntMinecartEntity target;
    private int previousSlot = -1;
    private int swapStage = 0;

    public CartAura() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.swapBackDelay.visibleWhen((Predicate<Float>)ignored -> this.swapBack.getValue());
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            if ((interactPacket.getHand().equals(Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem() == Items.TNT_MINECART) {
                BlockHitResult hitResult = interactPacket.getBlockHitResult();
                long now = System.currentTimeMillis();
                this.ownCartPlacements.add(new AuraCartPlacement(hitResult.getBlockPos(), now));
                this.ownCartPlacements.add(new AuraCartPlacement(BlockUtil.getPlacePos(hitResult), now));
            }
        }
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.ownCartPlacements.clear();
    }

    @Override
    public Rotation provideRotation() {
        if (mc.player == null || mc.world == null) {
            this.resetState();
            return null;
        } else if (this.swapStage == 2) {
            if (this.swapBackTimer.hasElapsed(this.swapBackDelay.getValue())) {
                this.restorePreviousSlot();
            }

            return null;
        } else if (mc.player.isUsingItem()) {
            return null;
        } else if (AutoObbyCrystal.sequenceActive) {
            return null;
        } else if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else {
            if (this.target != null) {
                Vec3d targetCenter = this.getBoxCenter(this.target);
                if (!this.target.isAlive()
                    || this.isOwnCart(this.target)
                    || Math.sqrt(mc.player.squaredDistanceTo(targetCenter.x, targetCenter.y, targetCenter.z)) > 5.0) {
                    this.target = null;
                }
            }

            if (this.target == null) {
                this.target = this.findTarget(this.targetFov.getValue());
                if (this.target != null) {
                    this.reactionDelay.reroll();
                    this.reactionTimer.reset();
                }
            }

            if (this.target == null) {
                if (this.swapStage == 1) {
                    this.restorePreviousSlot();
                }

                return null;
            } else {
                Vec3d aimPoint = this.getBoxCenter(this.target);
                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                if (this.humanizedAim.getValue() && !rotation.isClamped()) {
                    rotation = RotationUtil.addRandomJitter(rotation, aimPoint);
                }

                if (!mc.player.canSee(this.target)) {
                    return null;
                } else if (this.getDistanceToBox(this.target) > 3.0) {
                    return null;
                } else if (!this.reactionTimer.hasElapsedRandom(this.reactionDelay)) {
                    return rotation;
                } else {
                    if (this.swapStage == 1) {
                        if (!this.swapTimer.hasElapsed(this.swapDelay.getValue())) {
                            return rotation;
                        }

                        if (!InventoryUtil.isSword(mc.player.getMainHandStack())) {
                            this.restorePreviousSlot();
                            return rotation;
                        }
                    } else if (!InventoryUtil.isSword(mc.player.getMainHandStack())) {
                        int swordSlot = this.findSwordSlot();
                        if (swordSlot == -1) {
                            return null;
                        }

                        this.previousSlot = InventoryUtil.getSelectedSlot();
                        InventoryUtil.selectSlot(swordSlot);
                        this.swapStage = 1;
                        this.swapTimer.reset();
                        return rotation;
                    }

                    if (!this.attackTimer.hasElapsedRandom(this.attackDelay)) {
                        return rotation;
                    } else if (rotation.isClamped()) {
                        return rotation;
                    } else {
                        PlayerInteractions.attackEntity(this.target);
                        this.attackTimer.reset();
                        this.attackDelay.reroll();
                        if (this.swapStage == 1) {
                            if (this.swapBack.getValue()) {
                                this.swapStage = 2;
                                this.swapBackTimer.reset();
                            } else {
                                this.previousSlot = -1;
                                this.swapStage = 0;
                            }
                        }

                        return rotation;
                    }
                }
            }
        }
    }

    private TntMinecartEntity findTarget(float fov) {
        if (mc.world == null) {
            return null;
        } else {
            long now = System.currentTimeMillis();
            this.ownCartPlacements.removeIf(placement -> now - ((dev.zprestige.prestige.data.AuraCartPlacement)placement).time() > 5000L);
            TntMinecartEntity bestCart = null;
            double bestDistSq = Double.MAX_VALUE;
            Rotation viewRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof TntMinecartEntity cart && cart.isAlive() && !this.isOwnCart(cart)) {
                    Vec3d center = this.getBoxCenter(cart);
                    double distSq = mc.player.squaredDistanceTo(center.x, center.y, center.z);
                    if (!(Math.sqrt(distSq) > 5.0)) {
                        if (fov < 360.0F) {
                            if (!RotationUtil.isWithinAngle(viewRotation, RotationUtil.getRotationToPoint((float)center.x, (float)center.y, (float)center.z), fov)) {
                                continue;
                            }
                        }

                        if (distSq < bestDistSq) {
                            bestCart = cart;
                            bestDistSq = distSq;
                        }
                    }
                }
            }

            return bestCart;
        }
    }

    private boolean isOwnCart(TntMinecartEntity cart) {
        Vec3d cartPos = PositionUtil.getEntityPos(cart);
        long now = System.currentTimeMillis();

        for (AuraCartPlacement placement : (Iterable<AuraCartPlacement>)(this.ownCartPlacements)) {
            if (now - placement.time() <= 5000L && cartPos.isInRange(placement.pos().toCenterPos(), 1.5)) {
                return true;
            }
        }

        return false;
    }

    private int findSwordSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (InventoryUtil.isSword(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private Vec3d getBoxCenter(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private double getDistanceToBox(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double closestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void restorePreviousSlot() {
        if (this.previousSlot != -1) {
            Prestige.silentRotationManager.scheduleSlotSwitch(this.previousSlot);
            this.previousSlot = -1;
        }

        this.swapStage = 0;
    }

    private void resetState() {
        this.target = null;
        this.ownCartPlacements.clear();
        this.previousSlot = -1;
        this.swapStage = 0;
    }

    @Override
    public void onEnable() {
        this.resetState();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.restorePreviousSlot();
        this.target = null;
        this.ownCartPlacements.clear();
    }
}

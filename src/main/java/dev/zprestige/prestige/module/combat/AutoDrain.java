package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class AutoDrain extends Module implements RotationProvider {
    private EnumSetting mode;
    private MultiSetting liquid;
    private RangeSetting delay;
    private EnumSetting swap;
    private BoolSetting fallbackToCobweb;
    private BoolSetting interact;
    private BoolSetting swapBack;
    private FloatSetting silentAimFov;
    private ArrayList ownPlacedLiquids = new ArrayList();
    private ArrayList drainedPositions = new ArrayList();
    private int previousSlot = -1;
    private boolean awaitingSwapBack = false;
    private StopWatch actionTimer = new StopWatch();

    public AutoDrain() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.swapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.swap.getValue().equals("Normal") && this.interact.getValue()
            );
        this.silentAimFov.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Silent Aim"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        this.previousSlot = -1;
        this.awaitingSwapBack = false;
        this.actionTimer.reset();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        if (this.swapBack.getValue() && this.previousSlot != -1) {
            if (Prestige.silentRotationManager.hasActedThisTick()) {
                Prestige.silentRotationManager.scheduleSlotSwitch(this.previousSlot);
            } else {
                InventoryUtil.selectSlot(this.previousSlot);
            }

            this.previousSlot = -1;
        }

        this.drainedPositions.clear();
        this.awaitingSwapBack = false;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.ownPlacedLiquids.clear();
        this.drainedPositions.clear();
        this.previousSlot = -1;
        this.awaitingSwapBack = false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.ownPlacedLiquids.removeIf(pos -> !this.isLiquid(mc.world.getBlockState(((net.minecraft.util.math.BlockPos)pos)).getBlock()));
        this.drainedPositions.removeIf(pos -> !this.isLiquid(mc.world.getBlockState(((net.minecraft.util.math.BlockPos)pos)).getBlock()));
        if (this.mode.getValue().equals("Legit")) {
            if (!mc.player.isUsingItem()) {
                if (!Prestige.silentRotationManager.hasActedThisTick()) {
                    if (this.awaitingSwapBack && this.previousSlot != -1) {
                        if (this.actionTimer.hasElapsedRandom(this.delay)) {
                            if (this.swapBack.getValue() && this.interact.getValue()) {
                                InventoryUtil.selectSlot(this.previousSlot);
                            }

                            this.previousSlot = -1;
                            this.awaitingSwapBack = false;
                            this.actionTimer.reset();
                            this.delay.reroll();
                        }
                    } else if (this.actionTimer.hasElapsedRandom(this.delay)) {
                        if (InventoryUtil.findHotbarSlot(Items.BUCKET) != null
                            || this.fallbackToCobweb.getValue() && InventoryUtil.findHotbarSlot(Items.COBWEB) != null) {
                            PlayerEntity enemy = TargetSelector.getClosestEnemy();
                            if (enemy != null
                                && !(PositionUtil.getEntityPos(enemy).distanceTo(PositionUtil.getPlayerPos()) > 10.0)) {
                                BlockHitResult hitResult = this.raycastCrosshairLiquid();
                                if (hitResult != null) {
                                    BlockPos liquidPos = hitResult.getBlockPos();
                                    if (this.isDrainable(liquidPos)) {
                                        if (this.tryDrain(liquidPos, hitResult)) {
                                            this.actionTimer.reset();
                                            this.delay.reroll();
                                        }
                                    }
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
        if (Prestige.silentRotationManager.hasActedThisTick()) {
            return null;
        } else if (!this.mode.getValue().equalsIgnoreCase("Silent Aim")) {
            return null;
        } else if (mc.player.isUsingItem()) {
            return null;
        } else if (this.awaitingSwapBack && this.previousSlot != -1) {
            if (!this.actionTimer.hasElapsedRandom(this.delay)) {
                return null;
            } else {
                if (this.swapBack.getValue() && this.interact.getValue()) {
                    InventoryUtil.selectSlot(this.previousSlot);
                }

                this.previousSlot = -1;
                this.awaitingSwapBack = false;
                this.actionTimer.reset();
                this.delay.reroll();
                return null;
            }
        } else if (!this.actionTimer.hasElapsedRandom(this.delay)) {
            return null;
        } else if (InventoryUtil.findHotbarSlot(Items.BUCKET) != null
            || this.fallbackToCobweb.getValue() && InventoryUtil.findHotbarSlot(Items.COBWEB) != null) {
            PlayerEntity enemy = TargetSelector.getClosestEnemy();
            if (enemy != null && !(PositionUtil.getEntityPos(enemy).distanceTo(PositionUtil.getPlayerPos()) > 10.0)) {
                BlockHitResult hitResult = this.findReachableLiquidHit();
                if (hitResult == null) {
                    return null;
                } else {
                    BlockPos liquidPos = hitResult.getBlockPos();
                    boolean useCobweb = !(InventoryUtil.findHotbarSlot(Items.BUCKET) != null) && this.fallbackToCobweb.getValue();
                    Rotation rotation;
                    if (useCobweb) {
                        Vec3d aimPoint = Vec3d.ofCenter(liquidPos.down()).add(0.0, 0.5, 0.0);
                        rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                        HitResult cobwebHit = RotationUtil.raycastRotation(rotation);
                        if (cobwebHit == null || cobwebHit.getType() != Type.BLOCK || !(cobwebHit instanceof BlockHitResult cobwebBlockHit)) {
                            return null;
                        }

                        hitResult = cobwebBlockHit;
                    } else {
                        rotation = Prestige.silentRotationManager.getRotationTowards(hitResult.getPos());
                    }

                    if (Math.abs(rotation.getYaw() - mc.player.getYaw()) > this.silentAimFov.getValue()) {
                        return null;
                    } else if (rotation.isClamped()) {
                        return rotation;
                    } else {
                        Prestige.silentRotationManager.setActiveRotation(rotation);
                        if (this.tryDrain(liquidPos, hitResult)) {
                            this.actionTimer.reset();
                            this.delay.reroll();
                        }

                        return rotation;
                    }
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            ItemStack usedStack = interactPacket.getHand() == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
            if (usedStack.getItem() == Items.WATER_BUCKET || usedStack.getItem() == Items.LAVA_BUCKET) {
                BlockHitResult hitResult = interactPacket.getBlockHitResult();
                this.ownPlacedLiquids.add(hitResult.getBlockPos().add(hitResult.getSide().getOffsetX(), hitResult.getSide().getOffsetY(), hitResult.getSide().getOffsetZ()));
            }
        }
    }

    private boolean tryDrain(BlockPos liquidPos, BlockHitResult hitResult) {
        if (this.drainedPositions.contains(liquidPos)) {
            return false;
        } else {
            if (InventoryUtil.findHotbarSlot(Items.BUCKET) != null) {
                if (!InventoryUtil.isHolding(Items.BUCKET)) {
                    if (this.swap.getValue().equals("None")) {
                        return false;
                    } else {
                        if (this.previousSlot == -1) {
                            this.previousSlot = InventoryUtil.getSelectedSlot();
                        }

                        InventoryUtil.switchToItem(Items.BUCKET);
                        return true;
                    }
                } else {
                    if (this.interact.getValue()) {
                        PlayerInteractions.interactItem(Hand.MAIN_HAND);
                        this.awaitingSwapBack = true;
                    }

                    this.drainedPositions.add(liquidPos);
                    return true;
                }
            } else {
                return this.fallbackToCobweb.getValue() && this.tryPlaceCobweb(liquidPos, hitResult);
            }
        }
    }

    private boolean tryPlaceCobweb(BlockPos liquidPos, BlockHitResult hitResult) {
        if (!this.interact.getValue()) {
            return false;
        } else if (!InventoryUtil.isHolding(Items.COBWEB)) {
            if (this.swap.getValue().equals("None")) {
                return false;
            } else if (InventoryUtil.findHotbarSlot(Items.COBWEB) == null) {
                return false;
            } else {
                if (this.previousSlot == -1) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                }

                InventoryUtil.switchToItem(Items.COBWEB);
                return true;
            }
        } else {
            if (!this.mode.getValue().equals("Silent Aim")) {
                if (!(mc.crosshairTarget instanceof BlockHitResult)) {
                    return false;
                }

                hitResult = (BlockHitResult)mc.crosshairTarget;
            }

            PlayerInteractions.interactBlock(hitResult);
            this.drainedPositions.add(liquidPos);
            this.awaitingSwapBack = this.previousSlot != -1;
            return true;
        }
    }

    private BlockHitResult raycastCrosshairLiquid() {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);
        BlockHitResult hitResult = mc.world
            .raycast(
                new RaycastContext(
                    eyePos, eyePos.add(lookVec.multiply(PlayerInteractions.getReachDistance())), ShapeType.OUTLINE, FluidHandling.SOURCE_ONLY, mc.player
                )
            );
        return hitResult != null && hitResult.getType() == Type.BLOCK ? hitResult : null;
    }

    private BlockHitResult findReachableLiquidHit() {
        Vec3d eyePos = mc.player.getEyePos();

        for (BlockPos pos : BlockPos.iterateOutwards(mc.player.getBlockPos(), 5, 5, 5)) {
            if (!(PositionUtil.getPlayerPos().distanceTo(pos.toCenterPos()) > PlayerInteractions.getReachDistance())
                && this.isDrainable(pos)) {
                Box blockBox = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                if (!(eyePos.distanceTo(this.getClosestPointInBox(eyePos, blockBox)) > PlayerInteractions.getReachDistance())) {
                    BlockHitResult hitResult = mc.world
                        .raycast(new RaycastContext(eyePos, blockBox.getCenter(), ShapeType.OUTLINE, FluidHandling.SOURCE_ONLY, mc.player));
                    if (hitResult.getType() != Type.MISS && hitResult.getBlockPos().getManhattanDistance(pos) == 0) {
                        return hitResult;
                    }
                }
            }
        }

        return null;
    }

    private boolean isDrainable(BlockPos pos) {
        if (!this.isLiquid(mc.world.getBlockState(pos).getBlock())) {
            return false;
        } else if (!mc.world.getFluidState(pos).isStill()) {
            return false;
        } else if (this.drainedPositions.contains(pos)) {
            return false;
        } else {
            return this.ownPlacedLiquids.contains(pos) ? false : this.isSelectedLiquid(mc.world.getBlockState(pos).getBlock(), pos);
        }
    }

    private boolean isSelectedLiquid(Block block, BlockPos pos) {
        if (this.liquid.isSelected("Water") && block == Blocks.WATER) {
            return true;
        } else if (this.liquid.isSelected("Lava") && block == Blocks.LAVA) {
            return true;
        } else {
            return this.liquid.isSelected("Water On Web") && block == Blocks.WATER && this.isNextToCobweb(pos)
                ? true
                : this.liquid.isSelected("Lava On Web") && block == Blocks.LAVA && this.isNextToCobweb(pos);
        }
    }

    private boolean isLiquid(Block block) {
        return block == Blocks.WATER || block == Blocks.LAVA;
    }

    private boolean isNextToCobweb(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (mc.world.getBlockState(pos.offset(direction)).getBlock() == Blocks.COBWEB) {
                return true;
            }
        }

        return false;
    }

    private Vec3d getClosestPointInBox(Vec3d point, Box box) {
        return new Vec3d(
            Math.max(box.minX, Math.min(point.x, box.maxX)),
            Math.max(box.minY, Math.min(point.y, box.maxY)),
            Math.max(box.minZ, Math.min(point.z, box.maxZ))
        );
    }
}

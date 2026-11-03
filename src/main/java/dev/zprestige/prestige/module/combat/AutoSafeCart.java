package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CartBlockerPlacement;
import dev.zprestige.prestige.data.SafeCartPlacement;
import dev.zprestige.prestige.data.CartThreat;
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
import dev.zprestige.prestige.world.BlockUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class AutoSafeCart extends Module implements RotationProvider {
    private EnumSetting mode;
    private EnumSetting protect;
    private FloatSetting minDamage;
    private FloatSetting maxDamage;
    private MultiSetting blocks;
    private IntSetting maxBlocks;
    private RangeSetting delay;
    private EnumSetting swap;
    private FloatSetting range;
    private FloatSetting fov;
    private static final String[] BLOCK_OPTIONS = new String[]{"Logs", "Planks", "Cobblestone", "Obsidian"};
    private Map blocksPlacedPerCart = new HashMap();
    private List ownCartPlacements = new ArrayList();
    private BlockPos lastPlacePos;
    private long lastPlaceTime;
    private boolean hadThreats;
    private StopWatch placeTimer = new StopWatch();

    public AutoSafeCart() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.fov.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("Silent"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Cart);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.blocksPlacedPerCart.clear();
        this.ownCartPlacements.clear();
        this.lastPlacePos = null;
        this.hadThreats = false;
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interactPacket) {
            if ((interactPacket.getHand().equals(Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem() == Items.TNT_MINECART) {
                BlockHitResult hitResult = interactPacket.getBlockHitResult();
                long now = System.currentTimeMillis();
                this.ownCartPlacements.add(new SafeCartPlacement(hitResult.getBlockPos(), now));
                this.ownCartPlacements.add(new SafeCartPlacement(BlockUtil.getPlacePos(hitResult), now));
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        this.pruneTracking();
        if (this.mode.getValue().equals("Legit") && mc.player != null && mc.world != null) {
            if (!mc.player.isUsingItem() && !Prestige.silentRotationManager.hasActedThisTick()) {
                Block coverBlock = this.findCoverBlock();
                if (coverBlock != null) {
                    List threats = this.findThreats();
                    this.updateThreatState(threats);
                    if (!threats.isEmpty() && this.placeTimer.hasElapsedRandom(this.delay)) {
                        if (mc.crosshairTarget instanceof BlockHitResult crosshairHit) {
                            BlockPos placePos = !BlockUtil.isOf(crosshairHit.getBlockPos(), Blocks.FIRE)
                                    && !BlockUtil.isOf(
                                        crosshairHit.getBlockPos(), BlockUtil.getShortGrass()
                                    )
                                ? BlockUtil.getPlacePos(crosshairHit)
                                : crosshairHit.getBlockPos();
                            if (mc.world.getBlockState(placePos).isReplaceable() && !BlockUtil.hasEntityAt(placePos)) {
                                if (this.lastPlacePos == null || !this.lastPlacePos.equals(placePos)) {
                                    BlockPos pendingPos = this.getPendingPlacePos();

                                    for (CartThreat threat : (Iterable<CartThreat>)(threats)) {
                                        double damageAfter = Prestige.damageCalculator.getTntCartDamage(mc.player, threat.explosionPos(), threat.horizontalSpeed(), pendingPos, placePos, coverBlock.getDefaultState());
                                        if (!(damageAfter >= threat.damage() - 0.5)
                                            && this.tryPlaceCover(coverBlock, new CartBlockerPlacement(placePos, crosshairHit, damageAfter), threat)
                                            )
                                         {
                                            return;
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
        if (this.mode.getValue().equals("Silent") && mc.player != null && mc.world != null) {
            if (!mc.player.isUsingItem() && !Prestige.silentRotationManager.hasActedThisTick()) {
                Block coverBlock = this.findCoverBlock();
                if (coverBlock == null) {
                    return null;
                } else {
                    List threats = this.findThreats();
                    this.updateThreatState(threats);
                    if (!threats.isEmpty() && this.placeTimer.hasElapsedRandom(this.delay)) {
                        for (CartThreat threat : (Iterable<CartThreat>)(threats)) {
                            CartBlockerPlacement placement = this.findBestCoverPlacement(threat, coverBlock);
                            if (placement != null) {
                                Rotation rotation = Prestige.silentRotationManager.getRotationTowards(placement.hitResult().getPos());
                                float yawDelta = MathHelper.wrapDegrees(rotation.getYaw() - mc.player.getYaw());
                                if (!(yawDelta > this.fov.getValue()) && !(yawDelta < -this.fov.getValue())) {
                                    if (rotation.isClamped()) {
                                        return rotation;
                                    }

                                    if (!(RotationUtil.raycastRotation(rotation).getPos().distanceTo(placement.hitResult().getPos()) > 0.5)
                                        && this.tryPlaceCover(coverBlock, placement, threat)) {
                                        return rotation;
                                    }
                                }
                            }
                        }

                        return null;
                    } else {
                        return null;
                    }
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private List findThreats() {
        ArrayList threats = new ArrayList();
        Vec3d playerPos = PositionUtil.getPlayerPos();
        BlockPos pendingPos = this.getPendingPlacePos();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof TntMinecartEntity cart) {
                Vec3d cartPos = PositionUtil.getEntityPos(cart);
                if (!(playerPos.distanceTo(cartPos) > this.range.getValue().floatValue())
                    && this.shouldProtectFrom(cart)
                    && (Integer)this.blocksPlacedPerCart.getOrDefault(cart.getUuid(), 0) < this.maxBlocks.getValue()) {
                    double horizontalSpeed = Math.hypot(cart.getVelocity().x, cart.getVelocity().z);
                    double damage = Prestige.damageCalculator.getTntCartDamage(mc.player, cartPos, horizontalSpeed, pendingPos, null, null);
                    if (!(damage < this.minDamage.getValue().floatValue()) && !(damage <= this.maxDamage.getValue().floatValue())) {
                        threats.add(new CartThreat(cart, cartPos, horizontalSpeed, damage));
                    }
                }
            }
        }

        threats.sort((left, right) -> Double.compare(((dev.zprestige.prestige.data.CartThreat)right).damage(), ((dev.zprestige.prestige.data.CartThreat)left).damage()));
        return threats;
    }

    private List getCoverCandidates(TntMinecartEntity cart) {
        LinkedHashSet positions = new LinkedHashSet();
        BlockPos playerFeet = mc.player.getBlockPos();
        BlockPos cartFeet = cart.getBlockPos();
        BlockPos[] anchors = new BlockPos[]{playerFeet, playerFeet.up(), cartFeet, cartFeet.up()};
        BlockPos[] anchorArray = anchors;
        int anchorCount = anchors.length;

        for (int anchorIndex = 0; anchorIndex < anchorCount; anchorIndex++) {
            BlockPos anchor = anchorArray[anchorIndex];

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        positions.add(anchor.add(dx, 0, dz));
                    }
                }
            }
        }

        return new ArrayList(positions);
    }

    private CartBlockerPlacement findBestCoverPlacement(CartThreat threat, Block block) {
        BlockPos pendingPos = this.getPendingPlacePos();
        CartBlockerPlacement best = null;

        for (BlockPos pos : (Iterable<BlockPos>)(this.getCoverCandidates(threat.cart()))) {
            BlockHitResult hit = this.getPlaceHit(pos);
            if (hit != null) {
                double damage = Prestige.damageCalculator.getTntCartDamage(mc.player, threat.explosionPos(), threat.horizontalSpeed(), pendingPos, pos, block.getDefaultState());
                if (!(damage >= threat.damage() - 0.5) && (best == null || damage < best.damage())) {
                    best = new CartBlockerPlacement(pos, hit, damage);
                }
            }
        }

        return best;
    }

    private BlockHitResult getPlaceHit(BlockPos pos) {
        if (mc.world.getBlockState(pos).isReplaceable() && !BlockUtil.hasEntityAt(pos)) {
            if (this.lastPlacePos != null && this.lastPlacePos.equals(pos)) {
                return null;
            } else {
                Vec3d eyePos = mc.player.getEyePos();
                float reach = PlayerInteractions.getReachDistance();
                Direction[] directions = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

                for (Direction direction : directions) {
                    BlockPos neighbor = pos.offset(direction);
                    if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                        Direction face = direction.getOpposite();
                        Vec3d hitVec = neighbor.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                        if (!(eyePos.distanceTo(hitVec) > reach)) {
                            BlockHitResult raycastHit = mc.world.raycast(new RaycastContext(eyePos, hitVec, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
                            if (raycastHit != null && !(raycastHit.getPos().distanceTo(hitVec) > 0.1F)) {
                                return new BlockHitResult(hitVec, face, neighbor, false);
                            }
                        }
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    private boolean tryPlaceCover(Block block, CartBlockerPlacement placement, CartThreat threat) {
        boolean placed;
        if (this.swap.getValue().equals("None")) {
            if (!InventoryUtil.isHolding(block.asItem())) {
                return false;
            }

            placed = PlayerInteractions.interactBlock(placement.hitResult());
        } else {
            placed = InventoryUtil.runWithBlock(block, () -> PlayerInteractions.interactBlock(placement.hitResult()), this.swap.getValue().equals("Silent"));
        }

        if (placed) {
            this.blocksPlacedPerCart.merge(threat.cart().getUuid(), 1, (java.util.function.BiFunction)(oldCount, increment) -> Integer.sum((Integer)oldCount, (Integer)increment));
            this.lastPlacePos = placement.pos();
            this.lastPlaceTime = System.currentTimeMillis();
            this.placeTimer.reset();
            this.delay.reroll();
        }

        return placed;
    }

    private Block findCoverBlock() {
        boolean heldOnly = this.swap.getValue().equals("None");

        for (String option : BLOCK_OPTIONS) {
            if (this.blocks.isSelected(option)) {
                Block block = switch (option) {
                    case "Logs" -> {
                        TagKey logsTag = ItemTags.LOGS;
                        yield this.findAvailableTaggedBlock(logsTag, heldOnly);
                    }
                    case "Planks" -> {
                        TagKey planksTag = ItemTags.PLANKS;
                        yield this.findAvailableTaggedBlock(planksTag, heldOnly);
                    }
                    case "Cobblestone" -> {
                        Block cobblestone = Blocks.COBBLESTONE;
                        yield this.findAvailableBlock(cobblestone, heldOnly);
                    }
                    case "Obsidian" -> {
                        Block obsidian = Blocks.OBSIDIAN;
                        yield this.findAvailableBlock(obsidian, heldOnly);
                    }
                    default -> null;
                };
                if (block != null) {
                    return block;
                }
            }
        }

        return null;
    }

    private Block findAvailableBlock(Block block, boolean heldOnly) {
        if (heldOnly) {
            return InventoryUtil.isHolding(block.asItem()) ? block : null;
        } else {
            return InventoryUtil.findHotbarSlot(block.asItem()) != null ? block : null;
        }
    }

    private Block findAvailableTaggedBlock(TagKey tag, boolean heldOnly) {
        if (heldOnly) {
            return this.getTaggedBlock(mc.player.getMainHandStack(), tag);
        } else {
            for (int slot = 0; slot <= 8; slot++) {
                Block block = this.getTaggedBlock(mc.player.getInventory().getStack(slot), tag);
                if (block != null) {
                    return block;
                }
            }

            return null;
        }
    }

    private Block getTaggedBlock(ItemStack stack, TagKey tag) {
        return !stack.isEmpty() && stack.isIn(tag) && stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    private boolean shouldProtectFrom(TntMinecartEntity cart) {
        if (this.protect.getValue().equals("Both")) {
            return true;
        } else {
            boolean ownCart = this.isOwnCart(cart);
            return this.protect.getValue().equals("Only Self") ? ownCart : !ownCart;
        }
    }

    private boolean isOwnCart(TntMinecartEntity cart) {
        Vec3d cartPos = PositionUtil.getEntityPos(cart);
        long now = System.currentTimeMillis();

        for (SafeCartPlacement placement : (Iterable<SafeCartPlacement>)(this.ownCartPlacements)) {
            if (now - placement.time() <= 5000L && cartPos.isInRange(placement.pos().toCenterPos(), 1.5)) {
                return true;
            }
        }

        return false;
    }

    private void updateThreatState(List threats) {
        boolean hasThreats = !threats.isEmpty();
        if (hasThreats && !this.hadThreats) {
            this.placeTimer.reset();
            this.delay.reroll();
        }

        this.hadThreats = hasThreats;
    }

    private BlockPos getPendingPlacePos() {
        if (this.lastPlacePos == null) {
            return null;
        } else if (System.currentTimeMillis() - this.lastPlaceTime <= 1000L && mc.world.getBlockState(this.lastPlacePos).isReplaceable()) {
            return this.lastPlacePos;
        } else {
            this.lastPlacePos = null;
            return null;
        }
    }

    private void pruneTracking() {
        if (mc.world != null) {
            if (!this.blocksPlacedPerCart.isEmpty()) {
                HashSet aliveCartIds = new HashSet();

                for (Entity entity : mc.world.getEntities()) {
                    if (entity instanceof TntMinecartEntity) {
                        aliveCartIds.add(entity.getUuid());
                    }
                }

                this.blocksPlacedPerCart.keySet().removeIf(cartId -> !aliveCartIds.contains(((java.util.UUID)cartId)));
            }

            long now = System.currentTimeMillis();
            this.ownCartPlacements.removeIf(placement -> now - ((dev.zprestige.prestige.data.SafeCartPlacement)placement).time() > 5000L);
        }
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        this.blocksPlacedPerCart.clear();
        this.ownCartPlacements.clear();
        this.lastPlacePos = null;
        this.hadThreats = false;
    }
}

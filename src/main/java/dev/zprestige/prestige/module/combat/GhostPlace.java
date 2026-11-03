package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class GhostPlace extends Module {
    private BoolSetting cobwebOnly;
    private BoolSetting useOffhand;
    private long lastPlaceTime;
    private static final long NEVER_PLACED = -1L;

    public GhostPlace() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.lastPlaceTime = NEVER_PLACED;
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (mc.player != null && mc.world != null) {
            if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
                if (!Prestige.silentRotationManager.hasActedThisTick()) {
                    if (mc.options.useKey.isPressed()) {
                        Hand hand = this.getPlaceHand();
                        if (hand != null) {
                            if (isEnemyHit(mc.crosshairTarget)) {
                                BlockHitResult blockHit = this.raycastBlockBehind();
                                if (blockHit != null && blockHit.getType() == Type.BLOCK) {
                                    PlayerInteractions.interactBlock(blockHit, hand);
                                    this.lastPlaceTime = mc.world.getTime();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (mc.player != null && mc.world != null) {
            if (isEnemyHit(mc.crosshairTarget)) {
                if (this.getPlaceHand() != null) {
                    if (this.lastPlaceTime == mc.world.getTime()) {
                        event.cancel();
                    } else if (mc.options.useKey.isPressed()) {
                        BlockHitResult blockHit = this.raycastBlockBehind();
                        if (blockHit != null && blockHit.getType() == Type.BLOCK) {
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    private Hand getPlaceHand() {
        if (this.isPlaceable(mc.player.getMainHandStack())) {
            return Hand.MAIN_HAND;
        } else {
            return this.useOffhand.getValue() && this.isPlaceable(mc.player.getOffHandStack()) ? Hand.OFF_HAND : null;
        }
    }

    private boolean isPlaceable(ItemStack stack) {
        return this.cobwebOnly.getValue() ? stack.getItem() == Items.COBWEB : stack.getItem() instanceof BlockItem;
    }

    private static boolean isEnemyHit(HitResult hitResult) {
        if (!(hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingEntity)) {
            return false;
        } else {
            return Prestige.friendManager.isFriend(livingEntity.getName().getString()) ? false : Prestige.botFilter.isValidTarget(livingEntity);
        }
    }

    private BlockHitResult raycastBlockBehind() {
        Vec3d eyePos = mc.player.getEyePos();
        Rotation serverRotation = Prestige.silentRotationManager.getServerRotation();
        Vec3d endPos = eyePos.add((serverRotation != null ? mc.player.getRotationVector(serverRotation.getPitch(), serverRotation.getYaw()) : mc.player.getRotationVec(1.0F)).multiply(PlayerInteractions.getReachDistance()));
        return mc.world.raycast(new RaycastContext(eyePos, endPos, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
    }
}

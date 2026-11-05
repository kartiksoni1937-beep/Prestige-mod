package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.BlockUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

public class AntiAction extends Module {
    public AntiAction() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting doubleGlowstone;
    private BoolSetting openEChest;
    private BoolSetting placeGlowstone;
    private BoolSetting groundFirework;
    private BoolSetting swordShield;

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.groundFirework.getValue()
            && InventoryUtil.isHolding(Items.FIREWORK_ROCKET)
            && !PositionUtil.isPlayerGliding()
            && !mc.player.getOffHandStack().isOf(Items.CROSSBOW)) {
            event.cancel();
        } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            if (blockHit.getBlockPos() != null) {
                boolean targetingAnchor = BlockUtil.isOf(blockHit.getBlockPos(), Blocks.RESPAWN_ANCHOR);
                if (targetingAnchor && this.doubleGlowstone.getValue() && !mc.player.isSneaking() && InventoryUtil.isHolding(Items.GLOWSTONE)) {
                    if (BlockUtil.getAnchorCharges(blockHit.getBlockPos()) != 0) {
                        event.cancel();
                    }
                }

                if (BlockUtil.isOf(blockHit.getBlockPos(), Blocks.ENDER_CHEST)
                    && this.openEChest.getValue()
                    && !mc.player.isSneaking()
                    && (
                        this.isCombatItem(mc.player.getMainHandStack())
                            || this.isCombatItem(mc.player.getOffHandStack())
                    )) {
                    event.cancel();
                }

                if (InventoryUtil.isHolding(Items.GLOWSTONE)
                    && (!targetingAnchor || mc.player.isSneaking())
                    && this.placeGlowstone.getValue()
                    && BlockUtil.isClickableBlock(blockHit.getBlockPos())) {
                    event.cancel();
                }
            }
        }
    }

    private boolean isCombatItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            return !InventoryUtil.isSword(stack) && !InventoryUtil.isAxe(stack) && !InventoryUtil.isMace(stack)
                ? stack.isOf(Items.END_CRYSTAL)
                    || stack.isOf(Items.OBSIDIAN)
                    || stack.isOf(Items.CRYING_OBSIDIAN)
                    || stack.isOf(Items.TOTEM_OF_UNDYING)
                    || stack.isOf(Items.EXPERIENCE_BOTTLE)
                    || stack.isOf(Items.GLOWSTONE)
                : true;
        }
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof PlayerEntity player) {
                if (this.swordShield.getValue()
                    && InventoryUtil.isSword(mc.player.getMainHandStack())
                    && player.isUsingItem()
                    && player.getActiveItem().isOf(Items.SHIELD)) {
                    event.cancel();
                }
            }
        }
    }
}

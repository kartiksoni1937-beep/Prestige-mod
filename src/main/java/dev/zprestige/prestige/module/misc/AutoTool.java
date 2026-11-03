package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import net.minecraft.block.AirBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AutoTool extends Module {
    public AutoTool() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private int previousSlot = -1;

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (mc.player != null && mc.world != null && mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos blockPos = blockHit.getBlockPos();
            if (!mc.world.getBlockState(blockPos).isAir()) {
                int toolSlot = this.findBestToolSlot(blockPos);
                if (toolSlot != -1 && mc.options.attackKey.isPressed()) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    InventoryUtil.selectSlot(toolSlot);
                } else {
                    InventoryUtil.selectSlot(this.previousSlot);
                }
            }
        }
    }

    private int findBestToolSlot(BlockPos pos) {
        int bestSlot = -1;
        float bestScore = 1.0F;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getMaxDamage() - stack.getDamage() > 10) {
                float efficiencyLevel = EnchantmentHelper.getLevel(
                    mc.world.getRegistryManager().getOptional(Enchantments.EFFICIENCY.getRegistryRef()).get().getEntry(Enchantments.EFFICIENCY.getValue()).get(),
                    stack
                );
                float miningSpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                if (mc.world.getBlockState(pos).getBlock() instanceof AirBlock) {
                    return -1;
                }

                if (efficiencyLevel + miningSpeed > bestScore) {
                    bestScore = efficiencyLevel + miningSpeed;
                    bestSlot = slot;
                }
            }
        }

        return bestSlot;
    }
}

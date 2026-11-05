package dev.zprestige.prestige.inventory;

import dev.zprestige.prestige.module.combat.AnchorExploder;
import dev.zprestige.prestige.module.combat.AnchorMacro;
import dev.zprestige.prestige.module.combat.AnchorPearlCatch;
import dev.zprestige.prestige.module.combat.AnchorPlacer;
import dev.zprestige.prestige.module.combat.AutoAnchor;
import dev.zprestige.prestige.module.combat.DoubleAnchor;
import dev.zprestige.prestige.module.combat.SafeAnchor;
import dev.zprestige.prestige.render.MinecraftAccess;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.TridentItem;
import net.minecraft.item.WindChargeItem;
import net.minecraft.util.hit.BlockHitResult;

public final class AnchorUseGuard implements MinecraftAccess {
    private AnchorUseGuard() {
    }

    public static boolean shouldCancelItemUse() {
        if (mc.player != null && mc.world != null) {
            if (!isAnchorModuleActive()) {
                return false;
            } else if (mc.player.getMainHandStack().getItem() != Items.RESPAWN_ANCHOR && mc.player.getOffHandStack().getItem() != Items.RESPAWN_ANCHOR) {
                if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
                    return mc.world.getBlockState(blockHit.getBlockPos()).getBlock() != Blocks.RESPAWN_ANCHOR
                        ? false
                        : !isUsableItem(mc.player.getMainHandStack()) && !isUsableItem(mc.player.getOffHandStack());
                } else {
                    return false;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private static boolean isAnchorModuleActive() {
        return AutoAnchor.sequenceActive || AnchorExploder.lookingAtAnchor || AnchorMacro.acting || AnchorPearlCatch.active || AnchorPlacer.acting || DoubleAnchor.acting || SafeAnchor.isCoverPending();
    }

    private static boolean isUsableItem(ItemStack stack) {
        Item item = stack.getItem();
        return stack.get(DataComponentTypes.FOOD) != null
            || item instanceof BowItem
            || item instanceof CrossbowItem
            || item instanceof EnderPearlItem
            || item instanceof SnowballItem
            || item instanceof EggItem
            || item instanceof TridentItem
            || item instanceof WindChargeItem
            || item instanceof PotionItem
            || item instanceof ExperienceBottleItem
            || item instanceof ShieldItem
            || item instanceof BucketItem
            || item instanceof FishingRodItem;
    }
}

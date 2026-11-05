package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.ThreatAssessment;
import dev.zprestige.prestige.type.ThreatType;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

public final class ThreatAssessor {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final ThreatAssessment NO_THREAT = new ThreatAssessment(null, 0.0F, 0.0F);

    private ThreatAssessor() {
    }

    public static ThreatAssessment findBiggestThreat(PlayerEntity enemy, EnumSet threatTypes, float sensitivity) {
        if (mc.player != null && mc.world != null && enemy != null) {
            ThreatAssessment biggest = NO_THREAT;
            if (threatTypes.contains(ThreatType.CRYSTAL)) {
                for (Entity entity : mc.world.getEntities()) {
                    if (entity instanceof EndCrystalEntity) {
                        biggest = keepBiggerThreat(biggest, ThreatType.CRYSTAL, (float)Prestige.damageCalculator.getCrystalDamage(mc.player, entity.getBlockPos().toCenterPos()), sensitivity);
                    }
                }
            }

            if (threatTypes.contains(ThreatType.ANCHOR)) {
                for (BlockPos anchorPos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(5, (Predicate<BlockPos>)anchorCandidate -> isNearPlayer(enemy, anchorCandidate)
                        && mc.world.getBlockState(anchorCandidate).getBlock() == Blocks.RESPAWN_ANCHOR
                        && mc.world.getBlockState(anchorCandidate).get(Properties.CHARGES) != 0))) {
                    biggest = keepBiggerThreat(biggest, ThreatType.ANCHOR, (float)Prestige.damageCalculator.getAnchorDamage(mc.player, anchorPos.toCenterPos()), sensitivity);
                }
            }

            if (threatTypes.contains(ThreatType.OBSIDIAN) && !mc.player.isOnGround()) {
                for (BlockPos obsidianPos : (Iterable<BlockPos>)(BlockUtil.getBlocksInRadius(5, (Predicate<BlockPos>)obsidianCandidate -> obsidianCandidate.getY() < mc.player.getY()
                        && isNearPlayer(enemy, obsidianCandidate)
                        && mc.world.getBlockState(obsidianCandidate).getBlock() == Blocks.OBSIDIAN
                        && mc.world.getBlockState(obsidianCandidate.up()).getBlock() == Blocks.AIR))) {
                    biggest = keepBiggerThreat(biggest, ThreatType.OBSIDIAN, (float)Prestige.damageCalculator.getCrystalDamage(mc.player, obsidianPos.up().toCenterPos()), sensitivity);
                }
            }

            if (threatTypes.contains(ThreatType.MACE)) {
                float maceDamage = getMaceDamage(enemy);
                if (maceDamage > 0.0F) {
                    biggest = keepBiggerThreat(biggest, ThreatType.MACE, maceDamage, sensitivity);
                }
            }

            return biggest;
        } else {
            return NO_THREAT;
        }
    }

    public static ThreatAssessment findBiggestThreat(PlayerEntity enemy, EnumSet threatTypes) {
        return findBiggestThreat(enemy, threatTypes, 1.0F);
    }

    public static float getMaceDamage(PlayerEntity attacker) {
        return InventoryUtil.isMace(attacker.getMainHandStack()) && !(attacker.fallDistance <= 1.5) && !(attacker.getY() <= mc.player.getY())
            ? (float)(6.0 + attacker.fallDistance * 5.0)
            : 0.0F;
    }

    private static boolean isNearPlayer(PlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 25.0;
    }

    private static ThreatAssessment keepBiggerThreat(ThreatAssessment current, ThreatType type, float damage, float sensitivity) {
        if (damage <= 0.0F) {
            return current;
        } else {
            float weightedDamage = damage * type.confidence * sensitivity;
            return weightedDamage <= current.weightedDamage ? current : new ThreatAssessment(type, damage, weightedDamage);
        }
    }
}

package dev.zprestige.prestige.combat;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.ExplosionDamageKey;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.math.Vec3dMutator;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.DifficultySwitchMap;
import dev.zprestige.prestige.world.RaycastContextAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class ExplosionDamageCalculator implements MinecraftAccess {
    private final Vec3d scratchPos;
    private RaycastContext raycastContext;
    private volatile long cacheTime;
    private final ConcurrentHashMap damageCache;
    private static final ThreadLocal ENCHANTMENT_BUFFER = ThreadLocal.withInitial(Object2IntOpenHashMap::new);
    private static final long NO_CACHE_TIME = Long.MIN_VALUE;

    public ExplosionDamageCalculator() {
        super();
        this.scratchPos = new Vec3d(0.0, 0.0, 0.0);
        this.cacheTime = NO_CACHE_TIME;
        this.damageCache = new ConcurrentHashMap();
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.raycastContext = new RaycastContext(null, null, ShapeType.COLLIDER, FluidHandling.ANY, mc.player);
    }

    public double getCrystalDamage(LivingEntity target, Vec3d crystalPos) {
        return this.getCrystalDamage(target, crystalPos, false, null, false);
    }

    public double getCrystalDamage(LivingEntity target, Vec3d crystalPos, boolean predictMotion, BlockPos obsidianPos, boolean ignoreTerrain) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) {
            return 0.0;
        }

        long worldTime = mc.world.getTime();
        if (worldTime != this.cacheTime) {
            this.damageCache.clear();
            this.cacheTime = worldTime;
        }

        ExplosionDamageKey cacheKey = new ExplosionDamageKey(target, crystalPos.x, crystalPos.y, crystalPos.z, predictMotion, obsidianPos, ignoreTerrain);
        Double cached = (Double)this.damageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        } else {
            double damage = this.computeCrystalDamage(target, crystalPos, predictMotion, obsidianPos, ignoreTerrain);
            this.damageCache.put(cacheKey, damage);
            return damage;
        }
    }

    private double computeCrystalDamage(LivingEntity target, Vec3d crystalPos, boolean predictMotion, BlockPos obsidianPos, boolean ignoreTerrain) {
        if (target instanceof PlayerEntity
            && mc.getNetworkHandler().getPlayerListEntry(target.getUuid()) != null
            && mc.getNetworkHandler().getPlayerListEntry(target.getUuid()).getGameMode() == GameMode.CREATIVE) {
            return 0.0;
        } else {
            Vec3d targetPos = PositionUtil.getEntityPos(target);
            new Vec3dMutator(this.scratchPos).set(targetPos.x, targetPos.y, targetPos.z);
            if (predictMotion) {
                new Vec3dMutator(this.scratchPos).set(this.scratchPos.x + target.getVelocity().x, this.scratchPos.y + target.getVelocity().y, this.scratchPos.z + target.getVelocity().z);
            }

            double distance = Math.sqrt(this.scratchPos.squaredDistanceTo(crystalPos));
            if (distance > 12.0) {
                return 0.0;
            } else {
                double exposure = this.getExposure(crystalPos, target, predictMotion, this.raycastContext, obsidianPos, ignoreTerrain, null, null, null);
                double impact = (1.0 - distance / 12.0) * exposure;
                double damage = (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
                damage = this.scaleByDifficulty(damage);
                damage = net.minecraft.entity.DamageUtil.getDamageLeft(
                    target,
                    (float)damage,
                    mc.world.getDamageSources().explosion(null),
                    target.getArmor(),
                    (float)target.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue()
                );
                damage = this.applyResistance(target, damage);
                damage = applyProtectionEnchantments(target, damage, mc.world.getDamageSources().explosion(null));
                return damage < 0.0 ? 0.0 : damage;
            }
        }
    }

    public double getCrystalDamageAt(LivingEntity target, Vec3d crystalPos, Vec3d predictedPos, BlockPos obsidianPos) {
        if (target instanceof PlayerEntity
            && mc.getNetworkHandler().getPlayerListEntry(target.getUuid()) != null
            && mc.getNetworkHandler().getPlayerListEntry(target.getUuid()).getGameMode() == GameMode.CREATIVE) {
            return 0.0;
        } else {
            new Vec3dMutator(this.scratchPos).set(predictedPos.x, predictedPos.y, predictedPos.z);
            double distance = Math.sqrt(this.scratchPos.squaredDistanceTo(crystalPos));
            if (distance > 12.0) {
                return 0.0;
            } else {
                double exposure = this.getExposureWithOffset(crystalPos, target, predictedPos.subtract(PositionUtil.getEntityPos(target)), this.raycastContext, obsidianPos, false, null, null, null);
                double impact = (1.0 - distance / 12.0) * exposure;
                double damage = (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
                damage = this.scaleByDifficulty(damage);
                damage = net.minecraft.entity.DamageUtil.getDamageLeft(
                    target,
                    (float)damage,
                    mc.world.getDamageSources().explosion(null),
                    target.getArmor(),
                    (float)target.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue()
                );
                damage = this.applyResistance(target, damage);
                damage = applyProtectionEnchantments(target, damage, mc.world.getDamageSources().explosion(null));
                return damage < 0.0 ? 0.0 : damage;
            }
        }
    }

    private double computeAnchorDamage(LivingEntity target, Vec3d anchorPos, BlockPos anchorBlockPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        if (target instanceof PlayerEntity && ((PlayerEntity)target).getAbilities().creativeMode) {
            return 0.0;
        } else {
            double distance = Math.sqrt(target.squaredDistanceTo(anchorPos));
            if (distance > 10.0) {
                return 0.0;
            } else {
                double exposure = this.getExposure(anchorPos, target, false, this.raycastContext, null, false, anchorBlockPos, placedBlockPos, placedBlockState);
                double impact = (1.0 - distance / 10.0) * exposure;
                double damage = (impact * impact + impact) / 2.0 * 7.0 * 10.0 + 1.0;
                damage = this.scaleByDifficulty(damage);
                damage = this.applyResistance(target, damage);
                damage = net.minecraft.entity.DamageUtil.getDamageLeft(
                    target,
                    (float)damage,
                    mc.world.getDamageSources().explosion(null),
                    target.getArmor(),
                    (float)target.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue()
                );
                damage = applyProtectionEnchantments(target, damage, mc.world.getDamageSources().explosion(null));
                return damage < 0.0 ? 0.0 : damage;
            }
        }
    }

    public double getTntCartDamage(LivingEntity target, Vec3d cartPos, double cartSpeed) {
        return this.computeTntCartDamage(target, cartPos, cartSpeed, null, null, null);
    }

    public double getTntCartDamage(LivingEntity target, Vec3d cartPos, double cartSpeed, BlockPos obsidianPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        return this.computeTntCartDamage(target, cartPos, cartSpeed, obsidianPos, placedBlockPos, placedBlockState);
    }

    private double computeTntCartDamage(LivingEntity target, Vec3d cartPos, double cartSpeed, BlockPos obsidianPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        if (target instanceof PlayerEntity && ((PlayerEntity)target).getAbilities().creativeMode) {
            return 0.0;
        } else {
            double power = 4.0 + 1.5 * Math.min(cartSpeed, 5.0);
            double distance = Math.sqrt(target.squaredDistanceTo(cartPos));
            if (distance > power * 2.0) {
                return 0.0;
            } else {
                double exposure = this.getExposure(cartPos, target, false, this.raycastContext, obsidianPos, false, null, placedBlockPos, placedBlockState);
                double impact = (1.0 - distance / (power * 2.0)) * exposure;
                double damage = (impact * impact + impact) / 2.0 * 7.0 * (power * 2.0) + 1.0;
                damage = this.scaleByDifficulty(damage);
                damage = this.applyResistance(target, damage);
                damage = net.minecraft.entity.DamageUtil.getDamageLeft(
                    target,
                    (float)damage,
                    mc.world.getDamageSources().explosion(null),
                    target.getArmor(),
                    (float)target.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue()
                );
                damage = applyProtectionEnchantments(target, damage, mc.world.getDamageSources().explosion(null));
                return damage < 0.0 ? 0.0 : damage;
            }
        }
    }

    private static double applyProtectionEnchantments(LivingEntity target, double damage, DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return damage;
        } else {
            int protectionFactor = 0;
            Object2IntMap enchantments = (Object2IntMap)ENCHANTMENT_BUFFER.get();

            for (EquipmentSlot slot : AttributeModifierSlot.ARMOR) {
                collectEnchantments(target.getEquippedStack(slot), enchantments);
                int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
                if (protection > 0) {
                    protectionFactor += protection;
                }

                int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
                if (fireProtection > 0 && source.isIn(DamageTypeTags.IS_FIRE)) {
                    protectionFactor += 2 * fireProtection;
                }

                int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
                if (blastProtection > 0 && source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                    protectionFactor += 2 * blastProtection;
                }

                int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
                if (projectileProtection > 0 && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                    protectionFactor += 2 * projectileProtection;
                }

                int featherFalling = getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING);
                if (featherFalling > 0 && source.isIn(DamageTypeTags.IS_FALL)) {
                    protectionFactor += 3 * featherFalling;
                }
            }

            return net.minecraft.entity.DamageUtil.getInflictedDamage((float)damage, protectionFactor);
        }
    }

    public static void collectEnchantments(ItemStack stack, Object2IntMap enchantments) {
        enchantments.clear();
        if (!stack.isEmpty()) {
            for (Entry entry : stack.getItem() == Items.ENCHANTED_BOOK
                ? stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentEntries()
                : stack.getEnchantments().getEnchantmentEntries()) {
                enchantments.put((RegistryEntry)entry.getKey(), entry.getIntValue());
            }
        }
    }

    public static int getEnchantmentLevel(Object2IntMap enchantments, RegistryKey enchantmentKey) {
        for (Entry entry : (Iterable<Entry>)(Object2IntMaps.fastIterable(enchantments))) {
            if (((RegistryEntry)entry.getKey()).matchesKey(enchantmentKey)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    public double getAnchorDamage(LivingEntity target, Vec3d anchorPos) {
        return this.computeAnchorDamage(target, anchorPos, BlockPos.ofFloored(anchorPos), null, null);
    }

    public double getAnchorDamage(LivingEntity target, Vec3d anchorPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        return this.computeAnchorDamage(target, anchorPos, BlockPos.ofFloored(anchorPos), placedBlockPos, placedBlockState);
    }

    private double scaleByDifficulty(double damage) {
        return switch (DifficultySwitchMap.CASE_BY_ORDINAL[mc.world.getDifficulty().ordinal()]) {
            case 1 -> 0.0;
            case 2 -> Math.min(damage / 2.0 + 1.0, damage);
            case 3 -> damage * 3.0 / 2.0;
            default -> damage;
        };
    }

    private double applyResistance(LivingEntity target, double damage) {
        double reduced = damage;
        if (target.hasStatusEffect(StatusEffects.RESISTANCE)) {
            reduced = damage * (1.0 - (target.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 0.2);
        }

        return reduced < 0.0 ? 0.0 : reduced;
    }

    private double getExposure(Vec3d source, Entity entity, boolean predictMotion, RaycastContext context, BlockPos obsidianPos, boolean ignoreTerrain, BlockPos airPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        return this.getExposureWithOffset(source, entity, predictMotion ? entity.getVelocity() : null, context, obsidianPos, ignoreTerrain, airPos, placedBlockPos, placedBlockState);
    }

    private double getExposureWithOffset(Vec3d source, Entity entity, Vec3d boxOffset, RaycastContext context, BlockPos obsidianPos, boolean ignoreTerrain, BlockPos airPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        if (context == null) {
            context = this.raycastContext = new RaycastContext(null, null, ShapeType.COLLIDER, FluidHandling.ANY, mc.player);
        }

        Box box = entity.getBoundingBox();
        if (boxOffset != null) {
            box = box.offset(boxOffset.x, boxOffset.y, boxOffset.z);
        }

        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        if (stepX >= 0.0 && stepY >= 0.0 && stepZ >= 0.0) {
            int unobstructed = 0;
            int samples = 0;
            Vec3dMutator sampleSetter = new Vec3dMutator(this.scratchPos);
            RaycastContextAccessor contextSetter = new RaycastContextAccessor(context);

            for (double fracX = 0.0; fracX <= 1.0; fracX += stepX) {
                for (double fracY = 0.0; fracY <= 1.0; fracY += stepY) {
                    for (double fracZ = 0.0; fracZ <= 1.0; fracZ += stepZ) {
                        sampleSetter.set(MathHelper.lerp(fracX, box.minX, box.maxX) + offsetX, MathHelper.lerp(fracY, box.minY, box.maxY), MathHelper.lerp(fracZ, box.minZ, box.maxZ) + offsetZ);
                        contextSetter.setAll(this.scratchPos, source, ShapeType.COLLIDER, FluidHandling.NONE, entity);
                        if (this.raycastWithOverrides(context, obsidianPos, ignoreTerrain, airPos, placedBlockPos, placedBlockState).getType() == Type.MISS) {
                            unobstructed++;
                        }

                        samples++;
                    }
                }
            }

            return (double)unobstructed / samples;
        } else {
            return 0.0;
        }
    }

    private BlockHitResult raycastWithOverrides(RaycastContext context, BlockPos obsidianPos, boolean ignoreTerrain, BlockPos airPos, BlockPos placedBlockPos, BlockState placedBlockState) {
        return BlockView.raycast(context.getStart(), context.getEnd(), context, (ctx, blockPos) -> {
            BlockState state;
            if (airPos != null && blockPos.equals(airPos)) {
                state = Blocks.AIR.getDefaultState();
            } else if (placedBlockPos != null && blockPos.equals(placedBlockPos)) {
                state = placedBlockState;
            } else if (blockPos.equals(obsidianPos)) {
                state = Blocks.OBSIDIAN.getDefaultState();
            } else {
                state = mc.world.getBlockState(blockPos);
                if (state.getBlock().getBlastResistance() < 600.0F && ignoreTerrain) {
                    state = Blocks.AIR.getDefaultState();
                }
            }

            Vec3d start = ctx.getStart();
            Vec3d end = ctx.getEnd();
            VoxelShape shape = ctx.getBlockShape(state, mc.world, blockPos);
            BlockHitResult blockHit = mc.world.raycastBlock(start, end, blockPos, shape, state);
            BlockHitResult emptyHit = VoxelShapes.empty().raycast(start, end, blockPos);
            return (blockHit == null ? Double.MAX_VALUE : ctx.getStart().squaredDistanceTo(blockHit.getPos())) <= (emptyHit == null ? Double.MAX_VALUE : ctx.getStart().squaredDistanceTo(emptyHit.getPos())) ? blockHit : emptyHit;
        }, missCtx -> {
            Vec3d delta = missCtx.getStart().subtract(missCtx.getEnd());
            return BlockHitResult.createMissed(missCtx.getEnd(), Direction.getFacing(delta.x, delta.y, delta.z), BlockPos.ofFloored(missCtx.getEnd()));
        });
    }
}

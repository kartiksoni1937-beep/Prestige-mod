package dev.zprestige.prestige.module.spear;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ExtendedReachAttackEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.RotationUtil;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;

public class ExtendedReach extends Module {
    private FloatSetting chance;
    private MultiSetting weapons;
    private BoolSetting worksWithTriggerbot;
    public static ExtendedReach instance;
    private int previousSlot = -1;
    private int ticksUntilSwapBack = 0;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Spear);
    }

    public ExtendedReach() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        instance = this;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.ticksUntilSwapBack == 0) {
            if (this.previousSlot != -1) {
                InventoryUtil.selectSlot(this.previousSlot);
                this.previousSlot = -1;
            }
        } else {
            this.ticksUntilSwapBack--;
        }
    }

    @Subscribe
    public void onExtendedReachAttack(ExtendedReachAttackEvent event) {
        this.swapToSpear();
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        this.swapToSpear();
    }

    public void swapToSpear() {
        if (findExtendedReachTarget() != null) {
            if (RandomUtil.RANDOM.nextFloat() * 100.0F > this.chance.getValue()) {
                this.ticksUntilSwapBack = 1;
            }

            if (this.ticksUntilSwapBack <= 0) {
                if (!(mc.player.fallDistance > 1.5)) {
                    if (this.previousSlot == -1) {
                        if (this.isHoldingSelectedWeapon()) {
                            this.previousSlot = InventoryUtil.getSelectedSlot();
                            int spearSlot = findNonLungeSpearSlot();
                            if (spearSlot != -1) {
                                InventoryUtil.selectSlot(spearSlot);
                                this.ticksUntilSwapBack = 1;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isHoldingSelectedWeapon() {
        return this.weapons.isSelected("Sword") && InventoryUtil.isSword(mc.player.getMainHandStack())
            || this.weapons.isSelected("Axe") && InventoryUtil.isAxe(mc.player.getMainHandStack())
            || this.weapons.isSelected("Mace") && InventoryUtil.isMace(mc.player.getMainHandStack());
    }

    private static int findNonLungeSpearSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isIn(ItemTags.SPEARS)) {
                if (getEnchantmentLevel(stack, Enchantments.LUNGE) == 0) {
                    return slot;
                }
            }
        }

        return -1;
    }

    public static int getEnchantmentLevel(ItemStack stack, RegistryKey enchantmentKey) {
        if (stack.isEmpty()) {
            return 0;
        } else {
            Object2IntArrayMap enchantmentLevels = new Object2IntArrayMap();
            collectEnchantments(stack, enchantmentLevels);
            return findEnchantmentLevel(enchantmentLevels, enchantmentKey);
        }
    }

    public static int findEnchantmentLevel(Object2IntMap enchantmentLevels, RegistryKey enchantmentKey) {
        for (Entry entry : (Iterable<Entry>)(Object2IntMaps.fastIterable(enchantmentLevels))) {
            if (((RegistryEntry)entry.getKey()).matchesKey(enchantmentKey)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    public static void collectEnchantments(ItemStack stack, Object2IntMap enchantmentLevels) {
        enchantmentLevels.clear();
        if (!stack.isEmpty()) {
            for (Entry entry : stack.getItem() == Items.ENCHANTED_BOOK
                ? stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentEntries()
                : stack.getEnchantments().getEnchantmentEntries()) {
                enchantmentLevels.put((RegistryEntry)entry.getKey(), entry.getIntValue());
            }
        }
    }

    public static Entity findExtendedReachTarget() {
        if (!instance.isEnabled()) {
            return null;
        } else if (findNonLungeSpearSlot() == -1) {
            return null;
        } else if (mc.player.getAttackCooldownProgress(0.0F) < 1.0F) {
            return null;
        } else if (mc.crosshairTarget instanceof EntityHitResult) {
            return null;
        } else {
            HitResult hitResult = RotationUtil.raycastRotationWithRange(new Rotation(mc.player.getYaw(), mc.player.getPitch()), 4.75F);
            if (hitResult.getType() != Type.MISS) {
                return null;
            } else {
                Vec3d rayEnd = hitResult.getPos();
                Entity target = null;

                for (Entity entity : mc.world.getEntities()) {
                    if (entity != mc.player
                        && !(entity.distanceTo(mc.player) > 6.0F)
                        && mc.player.canSee(entity)
                        && entity.getBoundingBox().intersects(mc.player.getEyePos(), rayEnd)) {
                        target = entity;
                        break;
                    }
                }

                return target;
            }
        }
    }
}

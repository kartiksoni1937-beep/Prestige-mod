package dev.zprestige.prestige.combat;

import net.minecraft.entity.EquipmentSlot;

// $VF: synthetic class
public class EquipmentSlotSwitchMap {
    public static final int[] SWITCH_MAP = new int[EquipmentSlot.values().length];

    static {
        try {
            SWITCH_MAP[EquipmentSlot.HEAD.ordinal()] = 1;
        } catch (NoSuchFieldError headMissing) {
        }

        try {
            SWITCH_MAP[EquipmentSlot.CHEST.ordinal()] = 2;
        } catch (NoSuchFieldError chestMissing) {
        }

        try {
            SWITCH_MAP[EquipmentSlot.LEGS.ordinal()] = 3;
        } catch (NoSuchFieldError legsMissing) {
        }

        try {
            SWITCH_MAP[EquipmentSlot.FEET.ordinal()] = 4;
        } catch (NoSuchFieldError feetMissing) {
        }
    }
}

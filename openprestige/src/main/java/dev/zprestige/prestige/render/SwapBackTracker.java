package dev.zprestige.prestige.render;

import dev.zprestige.prestige.inventory.InventoryUtil;

public final class SwapBackTracker implements MinecraftAccess {
    private static Object owner;
    private static int originalSlot = -1;

    public static int claim(Object caller) {
        if (owner == null) {
            owner = caller;
            originalSlot = InventoryUtil.getSelectedSlot();
        } else if (owner != caller) {
            owner = caller;
        }

        return originalSlot;
    }

    public static boolean isOwner(Object caller) {
        return owner == caller;
    }

    public static void release(Object caller) {
        if (owner == caller) {
            owner = null;
            originalSlot = -1;
        }
    }

    public static int getOriginalSlot() {
        return originalSlot;
    }
}

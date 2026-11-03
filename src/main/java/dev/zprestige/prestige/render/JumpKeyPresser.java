package dev.zprestige.prestige.render;

import dev.zprestige.prestige.client.Prestige;
import net.minecraft.client.util.InputUtil;

public final class JumpKeyPresser implements MinecraftAccess {
    public static int ticksRemaining = 0;
    private static boolean listenerRegistered = false;

    private JumpKeyPresser() {
    }

    public static void pressJump() {
        if (!listenerRegistered) {
            listenerRegistered = true;
            Prestige.eventBus.register(new JumpKeyReleaser());
        }

        mc.options.jumpKey.setPressed(true);
        ticksRemaining = 2;
    }

    public static void cancelPress() {
        if (ticksRemaining > 0) {
            ticksRemaining = 0;
            releaseJumpKey();
        }
    }

    public static void releaseJumpKey() {
        if (!InputUtil.isKeyPressed(mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode())) {
            mc.options.jumpKey.setPressed(false);
        }
    }
}

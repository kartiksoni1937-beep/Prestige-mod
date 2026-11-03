package dev.zprestige.prestige.module.movement;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.world.PositionUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;

public class FastBridge extends Module {
    public FastBridge() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private boolean sneakForced = false;

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        boolean releaseSneak = !(mc.player.getMainHandStack().getItem() instanceof BlockItem) && !(mc.player.getOffHandStack().getItem() instanceof BlockItem);
        if (mc.player.getPitch() < 70.0F) {
            releaseSneak = true;
        }

        BlockPos belowPos = BlockPos.ofFloored(PositionUtil.getPlayerPos()).down();
        if (!mc.world.getBlockState(belowPos).isReplaceable()
            || !mc.world.getBlockState(belowPos.down()).isReplaceable()
            || !mc.world.getBlockState(belowPos.down().down()).isReplaceable()) {
            releaseSneak = true;
        }

        if (releaseSneak) {
            if (this.sneakForced) {
                mc.options.sneakKey.setPressed(false);
                this.sneakForced = false;
            }
        } else {
            mc.options.sneakKey.setPressed(true);
            this.sneakForced = true;
        }
    }

    @Override
    public void onDisable() {
        if (this.sneakForced) {
            mc.options.sneakKey.setPressed(false);
            this.sneakForced = false;
        }
    }
}

package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ShieldRenderSettingsEvent;
import dev.zprestige.prestige.event.ShieldBreakEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.math.Vec3d;

public class ShieldRenderer extends Module {
    public ShieldRenderer() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting overlay;
    private BoolSetting renderSelf;
    private EnumSetting renderMode;
    private FloatSetting xOffset;
    private FloatSetting yOffset;
    private FloatSetting zOffset;
    private FloatSetting xScale;
    private FloatSetting yScale;
    private FloatSetting zScale;
    private ColorSetting defaultColor;
    private ColorSetting brokenColor;
    private static final Map blockingTicks = new HashMap();
    private static final Map brokenTicksRemaining = new HashMap();
    private static final long BROKEN_DURATION_TICKS = 5370769767999209572L;

    @Subscribe
    public void onShieldRenderSettings(ShieldRenderSettingsEvent event) {
        event.enabled = this.isEnabled();
        event.overlay = this.overlay.getValue();
        event.offset = new Vec3d(this.xOffset.getValue().floatValue(), this.yOffset.getValue().floatValue(), this.zOffset.getValue().floatValue());
        event.scale = new Vec3d(this.xScale.getValue().floatValue(), this.yScale.getValue().floatValue(), this.zScale.getValue().floatValue());
        event.renderSelf = this.renderSelf.getValue();
        event.renderMode = this.renderMode.getValue().toString();
        event.defaultColor = this.defaultColor.getValue();
        event.brokenColor = this.brokenColor.getValue();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world == null) {
            blockingTicks.clear();
            brokenTicksRemaining.clear();
            return;
        }

        java.util.Set<UUID> presentIds = new java.util.HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();
            presentIds.add(uuid);
            if (player.isUsingItem() && player.getStackInHand(player.getActiveHand()).getItem() instanceof ShieldItem) {
                blockingTicks.put(uuid, (Integer)blockingTicks.getOrDefault(uuid, 0) + 1);
            } else {
                blockingTicks.remove(uuid);
            }
        }

        blockingTicks.keySet().retainAll(presentIds);
        brokenTicksRemaining.entrySet().removeIf(entry -> (Integer)((java.util.Map.Entry)entry).getValue() <= 1);
        brokenTicksRemaining.replaceAll((playerId, ticksLeft) -> ((java.lang.Integer)ticksLeft) > 1 ? ((java.lang.Integer)ticksLeft) - 1 : ((java.lang.Integer)ticksLeft));
    }

    @Subscribe
    public void onShieldBreak(ShieldBreakEvent event) {
        if (event.entity instanceof PlayerEntity player) {
            brokenTicksRemaining.put(player.getUuid(), (int)BROKEN_DURATION_TICKS);
        }
    }
}

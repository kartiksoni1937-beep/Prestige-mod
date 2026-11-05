package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.ItemUseCooldownAccessor;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.Set;
import net.minecraft.item.BlockItem;

public class FastPlace extends Module {
    public FastPlace() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting blocksOnly;
    private IntSetting minSpeed;
    private IntSetting maxSpeed;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null) {
            return;
        }

        if (!this.blocksOnly.getValue()
            || mc.player.getMainHandStack().getItem() instanceof BlockItem
            || mc.player.getOffHandStack().getItem() instanceof BlockItem) {
            if (this.minSpeed.getValue() > this.maxSpeed.getValue()) {
                this.minSpeed.setValue(this.maxSpeed.getValue());
            }

            ItemUseCooldownAccessor cooldownAccessor = new ItemUseCooldownAccessor(mc);
            cooldownAccessor.setItemUseCooldown(Math.min(cooldownAccessor.getItemUseCooldown(), RandomUtil.randomInt(this.minSpeed.getValue(), this.maxSpeed.getValue())));
        }
    }
}

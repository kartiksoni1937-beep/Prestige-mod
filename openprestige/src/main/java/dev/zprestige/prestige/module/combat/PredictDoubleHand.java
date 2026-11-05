package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.combat.ThreatAssessor;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.ThreatAssessment;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.ThreatType;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

public class PredictDoubleHand extends Module {
    public PredictDoubleHand() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private MultiSetting triggers;
    private MultiSetting pause;
    private RangeSetting reswapDelay;
    private FloatSetting sensitivity;
    private StopWatch reswapTimer = new StopWatch();
    private long lastCheckedTick = Long.MIN_VALUE;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if ((!this.pause.isSelected("Auto Crystal") || !AutoCrystal.active)
            && (!this.pause.isSelected("Auto Hit Crystal") || !AutoHitCrystal.active)) {
            if (this.triggers.isSelected("Pearl")
                && event.getPacket() instanceof PlayerInteractItemC2SPacket
                && mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL
                && !InventoryUtil.isHolding(Items.TOTEM_OF_UNDYING)) {
                Integer totemSlot = InventoryUtil.findHotbarSlot(Items.TOTEM_OF_UNDYING);
                if (totemSlot != null) {
                    InventoryUtil.selectSlot(totemSlot);
                }
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if ((!this.pause.isSelected("Auto Crystal") || !AutoCrystal.active)
            && (!this.pause.isSelected("Auto Hit Crystal") || !AutoHitCrystal.active)) {
            if (this.reswapTimer.hasElapsedRandom(this.reswapDelay) && mc.currentScreen == null && mc.isWindowFocused()) {
                long worldTime = mc.world.getTime();
                if (worldTime != this.lastCheckedTick) {
                    this.lastCheckedTick = worldTime;
                    if (!InventoryUtil.isHolding(Items.TOTEM_OF_UNDYING)) {
                        Integer totemSlot = InventoryUtil.findHotbarSlot(Items.TOTEM_OF_UNDYING);
                        if (totemSlot != null) {
                            PlayerEntity enemy = TargetSelector.getClosestEnemy();
                            if (enemy != null) {
                                float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                                if (!(mc.player.distanceTo(enemy) > 10.0F)) {
                                    if (!this.pause.isSelected("Shield")
                                        || !InventoryUtil.isHolding(Items.SHIELD)
                                        || !mc.player.isUsingItem()) {
                                        EnumSet threatTypes = EnumSet.noneOf(ThreatType.class);
                                        if (this.triggers.isSelected("Anchors")) {
                                            threatTypes.add(ThreatType.ANCHOR);
                                        }

                                        if (this.triggers.isSelected("Crystals")) {
                                            threatTypes.add(ThreatType.CRYSTAL);
                                        }

                                        if (this.triggers.isSelected("Obsidian")) {
                                            threatTypes.add(ThreatType.OBSIDIAN);
                                        }

                                        if (this.triggers.isSelected("Mace")) {
                                            threatTypes.add(ThreatType.MACE);
                                        }

                                        if (!threatTypes.isEmpty()) {
                                            ThreatAssessment threat = ThreatAssessor.findBiggestThreat(enemy, threatTypes, this.sensitivity.getValue());
                                            if (threat.hasThreat()) {
                                                if (threat.isLethal(health)) {
                                                    this.switchToTotem(totemSlot);
                                                    return;
                                                }
                                            }
                                        }

                                        if (this.triggers.isSelected("Sword Pop")
                                            && (
                                                InventoryUtil.isSword(enemy.getMainHandStack())
                                                    || enemy.getMainHandStack().getItem() instanceof AxeItem
                                            )
                                            && health < 1.0F) {
                                            this.switchToTotem(totemSlot);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void switchToTotem(int totemSlot) {
        if (!mc.player.isUsingItem()) {
            InventoryUtil.selectSlot(totemSlot);
            this.reswapDelay.reroll();
            this.reswapTimer.reset();
        }
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.KeyEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.JumpKeyPresser;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoElytra extends Module {
    private BoolSetting requireLand;
    private RangeSetting delay;
    private BoolSetting firework;
    private BoolSetting autoDisable;
    private FloatSetting flyTime;
    private BoolSetting silentInventory;
    private BoolSetting autoJump;
    private BoolSetting fireworkOnSpace;
    private RangeSetting spaceCooldown;
    private StopWatch actionTimer = new StopWatch();
    private StopWatch flightTimer = new StopWatch();
    private StopWatch spaceTimer = new StopWatch();
    private boolean glideStarted;
    private boolean restoring;
    private boolean hasLanded;
    private boolean skipInventoryScreen;
    private int stage = 0;
    private int fireworkSlot = -1;
    private int restoreSlot = -1;
    public static boolean active;
    public static AutoElytra instance;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal, Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    public AutoElytra() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        instance = this;
        this.flyTime.visibleWhen((Predicate<Float>)ignored -> this.autoDisable.getValue());
        this.spaceCooldown.visibleWhen((Predicate<Float>)ignored -> this.fireworkOnSpace.getValue());
    }

    @Override
    public void onDisable() {
        if (!this.autoDisable.getValue()) {
            if (this.restoring) {
                this.restoring = false;
                return;
            }

            this.restoring = true;
            this.toggle();
        }

        if (!this.restoring && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            this.restoring = true;
        }

        active = false;
    }

    @Override
    public void onEnable() {
        if (!this.restoring) {
            this.glideStarted = false;
            this.stage = 0;
            this.actionTimer.reset();
            this.delay.reroll();
            this.flightTimer.reset();
            this.fireworkSlot = -1;
            this.restoreSlot = -1;
            this.hasLanded = false;
            active = true;
            this.skipInventoryScreen = false;
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.isWindowFocused() && (mc.currentScreen == null || mc.currentScreen instanceof InventoryScreen)) {
            if (mc.player.isOnGround() && !this.restoring) {
                if (this.autoJump.getValue() && this.stage == 0) {
                    JumpKeyPresser.pressJump();
                }

                this.glideStarted = false;
                this.flightTimer.reset();
                this.hasLanded = true;
                if (this.stage >= 3) {
                    return;
                }
            }

            if (this.actionTimer.hasElapsedRandom(this.delay) && (this.hasLanded || !this.requireLand.getValue())) {
                List elytraSlots = InventoryUtil.findInventorySlots(Items.ELYTRA, true);
                List fireworkSlots = InventoryUtil.findInventorySlots(Items.FIREWORK_ROCKET, true);
                boolean alreadyWearing = false;
                if (this.stage == 0 && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                    this.stage = 1;
                    alreadyWearing = true;
                }

                if (this.stage < 2) {
                    if (!alreadyWearing && elytraSlots.isEmpty()) {
                        this.toggle();
                        return;
                    }

                    if (this.firework.getValue() && fireworkSlots.isEmpty()) {
                        this.toggle();
                        return;
                    }

                    int firstElytraSlot = alreadyWearing ? 0 : (Integer)elytraSlots.getFirst();
                    int firstFireworkSlot = !this.firework.getValue() ? 0 : (Integer)fireworkSlots.getFirst();
                    this.skipInventoryScreen = this.silentInventory.getValue() || firstElytraSlot < 9 && firstFireworkSlot < 9;
                }

                if (this.stage == 8 && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                    this.toggle();
                } else {
                    switch (this.stage) {
                        case 0:
                        case 5:
                            if (!this.skipInventoryScreen) {
                                mc.setScreen(new InventoryScreen(mc.player));
                            }

                            this.actionTimer.reset();
                            this.delay.reroll();
                            this.stage++;
                            break;
                        case 1:
                            if (mc.currentScreen == null && !this.skipInventoryScreen) {
                                this.stage = 0;
                            } else {
                                int syncId = mc.player.currentScreenHandler.syncId;
                                if (!elytraSlots.isEmpty()) {
                                    int elytraSlot = (Integer)elytraSlots.getFirst();
                                    if (elytraSlot < 9) {
                                        InventoryUtil.selectSlot(elytraSlot);
                                    } else {
                                        mc.interactionManager.clickSlot(syncId, elytraSlot, 0, SlotActionType.PICKUP, mc.player);
                                        mc.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                                        mc.interactionManager.clickSlot(syncId, elytraSlot, 0, SlotActionType.PICKUP, mc.player);
                                    }

                                    this.restoreSlot = elytraSlot;
                                } else {
                                    List chestplateSlots = InventoryUtil.findInventorySlots(Items.NETHERITE_CHESTPLATE, true);
                                    if (chestplateSlots.isEmpty()) {
                                        chestplateSlots = InventoryUtil.findInventorySlots(Items.DIAMOND_CHESTPLATE, true);
                                    }

                                    if (!chestplateSlots.isEmpty()) {
                                        this.restoreSlot = (Integer)chestplateSlots.getFirst();
                                    }
                                }

                                if (this.firework.getValue()) {
                                    int rocketSlot = (Integer)fireworkSlots.getFirst();
                                    if (rocketSlot < 9) {
                                        if (!elytraSlots.isEmpty() && (Integer)elytraSlots.getFirst() > 9) {
                                            InventoryUtil.selectSlot(rocketSlot);
                                        }
                                    } else {
                                        mc.interactionManager
                                            .clickSlot(syncId, rocketSlot, InventoryUtil.getSelectedSlot(), SlotActionType.SWAP, mc.player);
                                    }

                                    this.fireworkSlot = rocketSlot;
                                }

                                this.stage++;
                                this.actionTimer.reset();
                                this.delay.reroll();
                            }
                            break;
                        case 2:
                        case 7:
                            mc.setScreen(null);
                            this.stage++;
                            this.actionTimer.reset();
                            this.delay.reroll();
                            break;
                        case 3:
                            if (mc.currentScreen != null) {
                                return;
                            }

                            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                                if (mc.player.getMainHandStack().isOf(Items.ELYTRA)) {
                                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                }

                                this.actionTimer.reset();
                                this.delay.reroll();
                                return;
                            }

                            if (mc.player.isOnGround()) {
                                return;
                            }

                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
                            PlayerInteractions.startGliding();
                            this.glideStarted = true;
                            this.flightTimer.reset();
                            this.stage++;
                            this.actionTimer.reset();
                            this.delay.reroll();
                            break;
                        case 4:
                            if (this.firework.getValue()) {
                                if (!mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
                                    if (fireworkSlots.isEmpty()) {
                                        return;
                                    }

                                    InventoryUtil.selectSlot((Integer)fireworkSlots.getFirst());
                                    this.actionTimer.reset();
                                    this.delay.reroll();
                                    return;
                                }

                                PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                this.actionTimer.reset();
                                this.delay.reroll();
                            }

                            this.stage++;
                            break;
                        case 6:
                            if (mc.currentScreen == null && !this.skipInventoryScreen) {
                                this.stage = 5;
                            } else {
                                if (this.firework.getValue()) {
                                    if (this.fireworkSlot < 9) {
                                        InventoryUtil.selectSlot(this.fireworkSlot);
                                    } else {
                                        mc.interactionManager
                                            .clickSlot(mc.player.currentScreenHandler.syncId, this.fireworkSlot, InventoryUtil.getSelectedSlot(), SlotActionType.SWAP, mc.player);
                                    }

                                    this.actionTimer.reset();
                                    this.delay.reroll();
                                }

                                this.stage++;
                            }
                            break;
                        case 8:
                            if (this.autoDisable.getValue()) {
                                if (!this.flightTimer.hasElapsed(this.flyTime.getValue())) {
                                    return;
                                }
                            } else if (!this.restoring) {
                                return;
                            }

                            if (!this.skipInventoryScreen) {
                                mc.setScreen(new InventoryScreen(mc.player));
                            }

                            this.actionTimer.reset();
                            this.delay.reroll();
                            this.stage++;
                            break;
                        case 9:
                            if (mc.currentScreen == null && !this.skipInventoryScreen) {
                                this.stage = 8;
                            } else {
                                if (this.restoreSlot == -1) {
                                    List emptySlots = InventoryUtil.findInventorySlots(Items.AIR, false);
                                    if (emptySlots.isEmpty()) {
                                        this.stage++;
                                        this.actionTimer.reset();
                                        this.delay.reroll();
                                        break;
                                    }

                                    this.restoreSlot = (Integer)emptySlots.getFirst();
                                }

                                if (this.restoreSlot < 9) {
                                    InventoryUtil.selectSlot(this.restoreSlot);
                                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                } else {
                                    int screenSyncId = mc.player.currentScreenHandler.syncId;
                                    mc.interactionManager.clickSlot(screenSyncId, this.restoreSlot, 0, SlotActionType.PICKUP, mc.player);
                                    mc.interactionManager.clickSlot(screenSyncId, 6, 0, SlotActionType.PICKUP, mc.player);
                                    mc.interactionManager.clickSlot(screenSyncId, this.restoreSlot, 0, SlotActionType.PICKUP, mc.player);
                                }

                                this.stage++;
                                this.actionTimer.reset();
                                this.delay.reroll();
                            }
                            break;
                        case 10:
                            mc.setScreen(null);
                            if (mc.currentScreen == null) {
                                this.toggle();
                            }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (this.glideStarted) {
            if (event.getPacket() instanceof ClientCommandC2SPacket packet && packet.getMode() == Mode.START_FALL_FLYING) {
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onKey(KeyEvent event) {
        if (this.fireworkOnSpace.getValue()) {
            if (event.getAction() == 1) {
                if (event.getKey() == 32) {
                    if (mc.player != null && mc.world != null) {
                        if (mc.currentScreen == null) {
                            if (mc.isWindowFocused()) {
                                if (PositionUtil.isPlayerGliding()) {
                                    if (!active || this.stage >= 4 && this.stage <= 8) {
                                        if (this.spaceTimer.hasElapsedRandom(this.spaceCooldown)) {
                                            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                                                if (InventoryUtil.isHolding(Items.FIREWORK_ROCKET)) {
                                                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                                    Prestige.silentRotationManager.setActedThisTick(true);
                                                    this.spaceTimer.reset();
                                                    this.spaceCooldown.reroll();
                                                } else {
                                                    boolean[] rocketUsed = new boolean[]{false};
                                                    InventoryUtil.runWithItem(Items.FIREWORK_ROCKET, () -> {
                                                        if (mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
                                                            PlayerInteractions.interactItem(Hand.MAIN_HAND);
                                                            Prestige.silentRotationManager.setActedThisTick(true);
                                                            rocketUsed[0] = true;
                                                        }
                                                    }, true);
                                                    if (rocketUsed[0]) {
                                                        this.spaceTimer.reset();
                                                        this.spaceCooldown.reroll();
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
            }
        }
    }
}

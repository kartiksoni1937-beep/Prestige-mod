package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.combat.ThreatAssessor;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.gui.HandledScreenAccessor;
import dev.zprestige.prestige.gui.LockedInventoryScreen;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.network.PacketSender;
import dev.zprestige.prestige.reflect.FieldAccessor;
import dev.zprestige.prestige.render.DamageSample;
import dev.zprestige.prestige.render.ThreatAssessment;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.type.ArmorState;
import dev.zprestige.prestige.type.ThreatType;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.type.SwapQueueResult;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.damage.DamageEffects;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.registry.entry.RegistryEntry.Type;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoTotem extends Module {
    private EnumSetting mode;
    private RangeSetting delay;
    private BoolSetting dynamicDelay;
    private MultiSetting measures;
    private MultiSetting actions;
    private IntSetting totemSlot;
    private BoolSetting legit;
    private RangeSetting hoverDelay;
    private BoolSetting legitMouseMovement;
    private RangeSetting mouseSpeed;
    private RangeSetting mouseMoveDelay;
    private MultiSetting extra;
    private FloatSetting health;
    private BoolSetting waitWhenShielding;
    private BoolSetting antiMace;
    private StopWatch actionTimer = new StopWatch();
    private StopWatch hoverTimer = new StopWatch();
    private boolean totemPopped = false;
    private boolean swapQueued = false;
    private InventoryScreen openedInventory;
    private int totemOriginSlot = -1;
    private int slotToReselect = -1;
    private ArmorState offhandSwapPhase = ArmorState.NONE;
    private int offhandSwapSlot = -1;
    private int swapSettleTicks = 0;
    private long swapRequestId = 0L;
    private float lastEnemyY = Float.NaN;
    private Deque damageSamples = new ArrayDeque();
    private float lastHealth = Float.NaN;
    private Robot robot;
    private Random random = new Random();
    private Slot mouseTargetSlot;
    private StopWatch inventoryOpenTimer = new StopWatch();
    private List mousePath = new ArrayList();
    private int mousePathIndex = 0;
    private Point mousePathTarget = null;
    private StopWatch mouseStepTimer = new StopWatch();
    private volatile long ignorePopUntil = 0L;

    public AutoTotem() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.actions.visibleWhen((Predicate<String>)ignored -> this.mode.getValue().equals("Crystal"));
        this.mouseMoveDelay
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Crystal") && this.legitMouseMovement.getValue()
            );
        this.health.visibleWhen((Predicate<Float>)ignored -> this.mode.getValue().equals("SMP"));
        this.antiMace.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("SMP"));
        this.measures
            .visibleWhen(
                (Predicate<String>)ignored -> this.mode.getValue().equals("Crystal") && this.dynamicDelay.getValue()
            );
        this.legit
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystal") && this.actions.isSelected("Switch")
            );
        this.legitMouseMovement.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystal"));
        this.extra.visibleWhen((Predicate<String>)ignored -> this.mode.getValue().equals("Crystal"));
        this.totemSlot.visibleWhen((Predicate<Integer>)ignored -> this.mode.getValue().equals("Crystal"));
        this.hoverDelay
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Crystal")
                        && this.legit.getValue()
                        && this.actions.isSelected("Switch")
            );
        this.mouseSpeed
            .visibleWhen(
                (Predicate<Float>)ignored -> this.mode.getValue().equals("Crystal") && this.legitMouseMovement.getValue()
            );
        this.waitWhenShielding.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("SMP"));
        this.dynamicDelay.visibleWhen((Predicate<Boolean>)ignored -> this.mode.getValue().equals("Crystal"));
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.closeOpenedInventory();
        this.resetState();
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState();
    }

    private void resetState() {
        if (Prestige.inventorySwapQueue != null) {
            Prestige.inventorySwapQueue.cancelSwaps(this);
        }

        this.totemPopped = false;
        this.swapQueued = false;
        this.ignorePopUntil = 0L;
        this.totemOriginSlot = -1;
        this.slotToReselect = -1;
        this.clearOffhandSwap();
        this.damageSamples.clear();
        this.lastHealth = Float.NaN;
        this.lastEnemyY = Float.NaN;
        this.openedInventory = null;
        this.mouseTargetSlot = null;
        this.mousePath.clear();
        this.mousePathIndex = 0;
        this.mousePathTarget = null;
        this.delay.reroll();
        this.hoverDelay.reroll();
        this.mouseMoveDelay.reroll();
        this.actionTimer.reset();
        this.hoverTimer.reset();
        this.inventoryOpenTimer.reset();
        this.mouseStepTimer.reset();
    }

    private void closeOpenedInventory() {
        InventoryScreen screen = this.openedInventory;
        this.openedInventory = null;
        if (screen != null && mc.currentScreen == screen) {
            if (mc.getNetworkHandler() != null) {
                PacketSender.sendPacket(new CloseHandledScreenC2SPacket(screen.getScreenHandler().syncId));
            }

            mc.setScreen(null);
        }
    }

    private void closeScreen(HandledScreen screen) {
        PacketSender.sendPacket(new CloseHandledScreenC2SPacket(screen.getScreenHandler().syncId));
        mc.setScreen(null);
        if (this.openedInventory == screen) {
            this.openedInventory = null;
        }
    }

    private boolean isQueuedSwapPending() {
        if (!this.swapQueued) {
            return false;
        } else if (Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
            return true;
        } else {
            this.swapQueued = false;
            return false;
        }
    }

    private boolean requestSwap(int slotId, int button) {
        if (Prestige.inventorySwapQueue == null) {
            return InventoryUtil.swapSlot(slotId, button);
        } else {
            SwapQueueResult result = Prestige.inventorySwapQueue.requestSwapFor(this, slotId, button);
            this.swapQueued = result == SwapQueueResult.QUEUED;
            return result != SwapQueueResult.REJECTED;
        }
    }

    private boolean isOffhandSwapPending() {
        if (this.offhandSwapPhase == ArmorState.NONE) {
            return false;
        } else if (Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
            this.swapSettleTicks = 0;
            return true;
        } else if (this.swapSettleTicks++ == 0) {
            return true;
        } else {
            this.clearOffhandSwap();
            return false;
        }
    }

    private boolean requestOffhandSwap(int slotId, ArmorState phase) {
        if (this.offhandSwapPhase != ArmorState.NONE) {
            return false;
        } else {
            long requestId = ++this.swapRequestId;
            this.offhandSwapPhase = phase;
            this.offhandSwapSlot = slotId;
            this.swapSettleTicks = 0;
            if (Prestige.inventorySwapQueue == null) {
                InventoryUtil.swapSlot(slotId, 40);
                this.onOffhandSwapComplete(requestId, phase, slotId);
                return true;
            } else {
                SwapQueueResult result = Prestige.inventorySwapQueue.requestSwap(this, slotId, 40, (Runnable)() -> {
                    this.onOffhandSwapComplete(requestId, phase, slotId);
                });
                if (result == SwapQueueResult.IMMEDIATE) {
                    this.onOffhandSwapComplete(requestId, phase, slotId);
                    return true;
                } else if (result == SwapQueueResult.QUEUED) {
                    return true;
                } else {
                    this.clearOffhandSwap();
                    return false;
                }
            }
        }
    }

    private void onOffhandSwapComplete(long requestId, ArmorState phase, int slotId) {
        if (this.isEnabled() && requestId == this.swapRequestId && this.offhandSwapPhase == phase && this.offhandSwapSlot == slotId) {
            if (phase == ArmorState.EQUIP) {
                this.totemOriginSlot = slotId;
            } else if (phase == ArmorState.RESTORE) {
                this.totemOriginSlot = -1;
            }

            this.clearOffhandSwap();
        }
    }

    private void clearOffhandSwap() {
        this.offhandSwapPhase = ArmorState.NONE;
        this.offhandSwapSlot = -1;
        this.swapSettleTicks = 0;
        this.swapRequestId++;
    }

    private boolean shouldIgnorePop() {
        long ignoreDeadline = this.ignorePopUntil;
        this.ignorePopUntil = 0L;
        return this.extra.isSelected("Anti Totem Guard Test") && ignoreDeadline != 0L && System.currentTimeMillis() <= ignoreDeadline;
    }

    private float computeDangerScore() {
        PlayerEntity enemy = TargetSelector.getClosestEnemy();
        if (enemy != null && !(mc.player.distanceTo(enemy) > 20.0F)) {
            float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            float enemyDistance = mc.player.distanceTo(enemy);
            float heightFactor = (float)Math.max(0.0, Math.min(1.0, (mc.player.getY() - enemy.getY()) / 100.0));
            float dangerSum = 0.0F;
            int measureCount = 0;
            if (this.measures.isSelected("Health")) {
                dangerSum += Math.max(0.0F, Math.min(1.0F, (20.0F - currentHealth) / 18.0F));
                measureCount++;
            }

            if (this.measures.isSelected("Distance")) {
                dangerSum += Math.max(0.0F, Math.min(1.0F, (10.0F - enemyDistance) / 6.0F));
                measureCount++;
            }

            if (this.measures.isSelected("Y Level")) {
                dangerSum += heightFactor;
                measureCount++;
            }

            if (this.measures.isSelected("Nearby Threats")) {
                ThreatAssessment threat = ThreatAssessor.findBiggestThreat(
                    enemy, EnumSet.of(ThreatType.CRYSTAL, ThreatType.ANCHOR, ThreatType.OBSIDIAN)
                );
                float threatFactor = 0.0F;
                if (threat.hasThreat()) {
                    if (threat.isLethal(currentHealth)) {
                        threatFactor = 1.0F;
                    } else {
                        if (threat.getHealthAfter(currentHealth) < 6.0F) {
                            threatFactor = 0.6F;
                        }
                    }
                }

                dangerSum += threatFactor;
                measureCount++;
            }

            if (this.measures.isSelected("Recent Damage Rate")) {
                long now = System.currentTimeMillis();
                this.damageSamples.removeIf(oldSample -> now - ((dev.zprestige.prestige.render.DamageSample)oldSample).timestamp > 5000L);
                float recentDamage = 0.0F;

                for (DamageSample sample : (Iterable<DamageSample>)(this.damageSamples)) {
                    recentDamage += sample.damage;
                }

                dangerSum += Math.min(1.0F, recentDamage / 20.0F);
                measureCount++;
            }

            return measureCount > 0 ? dangerSum / measureCount * 100.0F : 0.0F;
        } else {
            return 0.0F;
        }
    }

    private List generateMousePath(Point start, Point end) {
        ArrayList path = new ArrayList();
        double distance = start.distance(end);
        int steps = (int)Math.max(8.0, Math.min(60.0, distance / this.mouseSpeed.randomInRange() * 3.5));
        double angle = Math.atan2(end.y - start.y, end.x - start.x);
        double normalAngle = angle + (Math.PI / 2);
        double curveAmplitude = Math.min(distance * 0.18, 22.0);
        double firstOffset = (this.random.nextGaussian() * 0.4 + (this.random.nextBoolean() ? 1 : -1)) * curveAmplitude * (0.4 + this.random.nextDouble() * 0.6);
        double secondOffset = this.random.nextGaussian() * 0.3 * curveAmplitude * 0.5;
        double control1X = start.x + (end.x - start.x) * (0.25 + this.random.nextDouble() * 0.15) + Math.cos(normalAngle) * firstOffset;
        double control1Y = start.y + (end.y - start.y) * (0.25 + this.random.nextDouble() * 0.15) + Math.sin(normalAngle) * firstOffset;
        double control2X = start.x + (end.x - start.x) * (0.6 + this.random.nextDouble() * 0.15) + Math.cos(normalAngle) * secondOffset;
        double control2Y = start.y + (end.y - start.y) * (0.6 + this.random.nextDouble() * 0.15) + Math.sin(normalAngle) * secondOffset;
        boolean overshoot = this.random.nextDouble() < 0.25 && distance > 30.0;
        double overshootDistance = overshoot ? 3.0 + this.random.nextDouble() * 5.0 : 0.0;
        double overshootAngle = angle + this.random.nextGaussian() * 0.15;
        Point curveEnd = overshoot ? new Point((int)(end.x + Math.cos(overshootAngle) * overshootDistance), (int)(end.y + Math.sin(overshootAngle) * overshootDistance)) : end;

        for (int step = 0; step <= steps; step++) {
            double progress = (double)step / steps;
            double eased = progress < 0.5 ? 4.0 * progress * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0;
            double invEased = 1.0 - eased;
            path.add(new Point((int)(
                invEased * invEased * invEased * start.x + 3.0 * invEased * invEased * eased * control1X + 3.0 * invEased * eased * eased * control2X + eased * eased * eased * curveEnd.x
            ), (int)(
                invEased * invEased * invEased * start.y + 3.0 * invEased * invEased * eased * control1Y + 3.0 * invEased * eased * eased * control2Y + eased * eased * eased * curveEnd.y
            )));
        }

        if (overshoot) {
            int correctionSteps = 4 + this.random.nextInt(4);

            for (int correctionStep = 1; correctionStep <= correctionSteps; correctionStep++) {
                double correctionProgress = (double)correctionStep / correctionSteps;
                double smoothed = correctionProgress * correctionProgress * (3.0 - 2.0 * correctionProgress);
                path.add(new Point((int)(curveEnd.x + (end.x - curveEnd.x) * smoothed), (int)(curveEnd.y + (end.y - curveEnd.y) * smoothed)));
            }
        }

        path.add(end);
        return path;
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.isWindowFocused()) {
            if (!this.mode.getValue().equals("SMP")) {
                if (this.totemPopped && !this.isDelayActive()) {
                    if (this.legitMouseMovement.getValue() && this.robot == null) {
                        this.createRobot();
                    }

                    if (!(InventoryUtil.isHolding(Items.SHIELD) && mc.player.isUsingItem())) {
                        Integer hotbarTotemSlot = InventoryUtil.findHotbarSlot(Items.TOTEM_OF_UNDYING);
                        if (hotbarTotemSlot != null
                            && mc.currentScreen == null
                            && this.actions.isSelected("Double Hand")
                            && !InventoryUtil.isHolding(Items.TOTEM_OF_UNDYING)) {
                            InventoryUtil.selectSlot(hotbarTotemSlot);
                            if (this.startActionDelay()) {
                                return;
                            }
                        }

                        List inventoryTotemSlots = InventoryUtil.findInventorySlots(Items.TOTEM_OF_UNDYING, true);
                        if (inventoryTotemSlots.isEmpty()) {
                            if (mc.currentScreen instanceof InventoryScreen && this.actions.isSelected("Close Inventory")) {
                                this.closeScreen((HandledScreen)mc.currentScreen);
                            }

                            this.totemPopped = false;
                        } else {
                            boolean needsHotbarRefill = hotbarTotemSlot == null && this.actions.isSelected("Refill Hotbar Totem");
                            boolean shouldOpenInventory = mc.currentScreen == null && (needsHotbarRefill || !InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING));
                            if (this.extra.isSelected("Only Inventory on Both Pops")) {
                                shouldOpenInventory = mc.currentScreen == null && needsHotbarRefill && !InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING);
                                if (!shouldOpenInventory
                                    && mc.currentScreen == null
                                    && (needsHotbarRefill || !InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING))
                                    && (!needsHotbarRefill || InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING))
                                    && this.startActionDelay()) {
                                    return;
                                }
                            }

                            if (shouldOpenInventory) {
                                this.inventoryOpenTimer.reset();
                                this.mouseMoveDelay.reroll();
                                this.mousePath = new ArrayList();
                                this.mousePathIndex = 0;
                                this.mousePathTarget = null;
                                this.mouseTargetSlot = null;
                                if (!this.actions.isSelected("Open Inventory")) {
                                    return;
                                }

                                Object inventoryScreen = this.actions.isSelected("Switch")
                                    ? new LockedInventoryScreen(mc.player)
                                    : new InventoryScreen(mc.player);
                                mc.setScreen((Screen)inventoryScreen);
                                if (mc.currentScreen == inventoryScreen) {
                                    this.openedInventory = (InventoryScreen)inventoryScreen;
                                }

                                if (this.startActionDelay()) {
                                    return;
                                }
                            }

                            if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof LockedInventoryScreen) {
                                if (this.isQueuedSwapPending()) {
                                    return;
                                }

                                if (this.legitMouseMovement.getValue() && this.inventoryOpenTimer.hasElapsedRandom(this.mouseMoveDelay)) {
                                    HandledScreenAccessor focusAccessor = new HandledScreenAccessor((HandledScreen)mc.currentScreen);
                                    if (focusAccessor.getFocusedSlot() == null
                                        || focusAccessor.getFocusedSlot().getStack().getItem() != Items.TOTEM_OF_UNDYING) {
                                        if (this.mouseTargetSlot != null && this.mouseTargetSlot.getStack().getItem() != Items.TOTEM_OF_UNDYING) {
                                            this.mouseTargetSlot = null;
                                        }

                                        if (this.mouseTargetSlot == null) {
                                            ArrayList candidateSlots = new ArrayList();

                                            for (Slot slot : ((HandledScreen)mc.currentScreen).getScreenHandler().slots) {
                                                if (slot.id != 45
                                                    && slot.id != 36 + (this.totemSlot.getValue() - 1)
                                                    && slot.getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                                                    candidateSlots.add(slot);
                                                }
                                            }

                                            if (!candidateSlots.isEmpty()) {
                                                this.mouseTargetSlot = (Slot)candidateSlots.get(this.random.nextInt(candidateSlots.size()));
                                            }
                                        }

                                        if (this.mouseTargetSlot != null) {
                                            double scaleFactor = mc.getWindow().getScaleFactor();
                                            int windowX = mc.getWindow().getX();
                                            int windowY = mc.getWindow().getY();
                                            int screenLeft = (Integer)FieldAccessor.of(HandledScreen.class, "x")
                                                .get((InventoryScreen)mc.currentScreen);
                                            int screenTop = (Integer)FieldAccessor.of(HandledScreen.class, "y")
                                                .get((InventoryScreen)mc.currentScreen);
                                            Point slotPoint = new Point(windowX + (int)((screenLeft + this.mouseTargetSlot.x + 8) * scaleFactor), windowY + (int)((screenTop + this.mouseTargetSlot.y + 8) * scaleFactor));
                                            Point cursorPoint = MouseInfo.getPointerInfo().getLocation();
                                            if (this.mousePathTarget == null || !this.mousePathTarget.equals(slotPoint) || this.mousePathIndex >= this.mousePath.size()) {
                                                this.mousePathTarget = slotPoint;
                                                this.mousePath = this.generateMousePath(cursorPoint, slotPoint);
                                                this.mousePathIndex = 0;
                                            }

                                            float stepDelayMs = (21.0F - this.mouseSpeed.randomInRange()) * 2.0F;
                                            if (this.mousePathIndex < this.mousePath.size()) {
                                                if (this.mouseStepTimer.hasElapsed(stepDelayMs)) {
                                                    Point nextPoint = (Point)this.mousePath.get(this.mousePathIndex);
                                                    double jitterScale = Math.max(0.0, 1.0 - ((double)this.mousePathIndex / this.mousePath.size()) * 2.5);
                                                    int jitterX = (int)(this.random.nextGaussian() * 0.8 * jitterScale);
                                                    int jitterY = (int)(this.random.nextGaussian() * 0.8 * jitterScale);
                                                    this.robot.mouseMove(nextPoint.x + jitterX, nextPoint.y + jitterY);
                                                    this.mousePathIndex++;
                                                    this.mouseStepTimer.reset();
                                                }
                                            }
                                        }
                                    }
                                }

                                boolean offhandHasTotem = InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING);
                                if (!offhandHasTotem && this.actions.isSelected("Switch")) {
                                    if (!this.legit.getValue() && !this.legitMouseMovement.getValue()) {
                                        List mainInventoryTotems = InventoryUtil.findInventorySlots(Items.TOTEM_OF_UNDYING, false);
                                        List sourceSlots = mainInventoryTotems.isEmpty() ? inventoryTotemSlots : mainInventoryTotems;
                                        int sourceSlot = (Integer)sourceSlots.get(RandomUtil.randomInt(0, sourceSlots.size() - 1));
                                        if (this.requestSwap(mainInventoryTotems.isEmpty() ? 36 + sourceSlot : sourceSlot, 40) && this.startActionDelay()) {
                                            return;
                                        }
                                    } else {
                                        HandledScreenAccessor offhandFocusAccessor = new HandledScreenAccessor((HandledScreen)mc.currentScreen);
                                        if (offhandFocusAccessor.getFocusedSlot() == null
                                            || offhandFocusAccessor.getFocusedSlot().getStack().getItem() != Items.TOTEM_OF_UNDYING) {
                                            this.hoverTimer.reset();
                                            this.hoverDelay.reroll();
                                            return;
                                        }

                                        if (this.hoverTimer.hasElapsedRandom(this.hoverDelay)) {
                                            if (this.requestSwap(offhandFocusAccessor.getFocusedSlot().id, 40) && this.startActionDelay()) {
                                                return;
                                            }
                                        }
                                    }
                                }

                                if (needsHotbarRefill && this.actions.isSelected("Switch")) {
                                    List refillTotems = InventoryUtil.findInventorySlots(Items.TOTEM_OF_UNDYING, false);
                                    if (refillTotems.size() > 0) {
                                        if (!this.legit.getValue() && !this.legitMouseMovement.getValue()) {
                                            if (this.requestSwap((Integer)refillTotems.get(RandomUtil.randomInt(0, refillTotems.size() - 1)), this.totemSlot.getValue() - 1) && this.startActionDelay()) {
                                                return;
                                            }
                                        } else {
                                            HandledScreenAccessor refillFocusAccessor = new HandledScreenAccessor((HandledScreen)mc.currentScreen);
                                            if (refillFocusAccessor.getFocusedSlot() == null
                                                || refillFocusAccessor.getFocusedSlot().getStack().getItem() != Items.TOTEM_OF_UNDYING) {
                                                this.hoverTimer.reset();
                                                this.hoverDelay.reroll();
                                                return;
                                            }

                                            if (this.hoverTimer.hasElapsedRandom(this.hoverDelay)) {
                                                if (this.requestSwap(refillFocusAccessor.getFocusedSlot().id, this.totemSlot.getValue() - 1) && this.startActionDelay()) {
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!this.actions.isSelected("Close Inventory")) {
                                    return;
                                }

                                if (needsHotbarRefill || !offhandHasTotem) {
                                    return;
                                }

                                this.closeScreen((HandledScreen)mc.currentScreen);
                                if (this.startActionDelay()) {
                                    return;
                                }
                            }

                            if (this.actionTimer.hasElapsed(150.0F)) {
                                this.totemPopped = false;
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerRespawnS2CPacket) {
            mc.execute(this::resetState);
        } else if (mc.world != null && mc.player != null) {
            if (this.extra.isSelected("Anti Totem Guard Test")
                && event.getPacket() instanceof EntityDamageS2CPacket damagePacket
                && damagePacket.entityId() == mc.player.getId()) {
                DamageType damageType = damagePacket.sourceType().value();
                if (damageType.msgId().equals("generic")
                    && damageType.exhaustion() == 0.0F
                    && damageType.effects().equals(DamageEffects.HURT)
                    && damagePacket.sourceCauseId() == -1
                    && damagePacket.sourceType().getType().equals(Type.REFERENCE)) {
                    ModuleNotificationsElement.postNotification(this, "Detected a manual totem check, ignoring pop.", NotificationType.WARNING);
                    mc.world.playSoundFromEntity(mc.player, mc.player, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.AMBIENT, 13.0F, 5.0F);
                    this.ignorePopUntil = System.currentTimeMillis() + 500L;
                }
            }

            if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {
                if (statusPacket.getEntity(mc.world) == mc.player && statusPacket.getStatus() == 35) {
                    if (this.shouldIgnorePop()) {
                        return;
                    }

                    this.startActionDelay();
                    this.totemPopped = true;
                }
            }

            if (event.getPacket() instanceof BundlePacket bundlePacket) {
                for (Packet bundledPacket : (Iterable<Packet>)(bundlePacket.getPackets())) {
                    if (bundledPacket instanceof EntityStatusS2CPacket bundledStatus) {
                        if (bundledStatus.getEntity(mc.world) == mc.player && bundledStatus.getStatus() == 35) {
                            if (this.shouldIgnorePop()) {
                                return;
                            }

                            this.startActionDelay();
                            this.totemPopped = true;
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (this.mode.getValue().equals("Crystal") && this.extra.isSelected("Pause Interactions")) {
            if (event.getPacket() instanceof PlayerInteractBlockC2SPacket || event.getPacket() instanceof PlayerInteractItemC2SPacket) {
                if (this.openedInventory != null && mc.currentScreen != this.openedInventory) {
                    this.openedInventory = null;
                }

                if (this.openedInventory != null || Prestige.inventorySwapQueue != null && Prestige.inventorySwapQueue.hasPendingSwaps()) {
                    event.cancel();
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.dynamicDelay.getValue()) {
            float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (!Float.isNaN(this.lastHealth)) {
                float damageTaken = Math.max(0.0F, this.lastHealth - currentHealth);
                if (damageTaken > 0.0F) {
                    this.damageSamples.addLast(new DamageSample(System.currentTimeMillis(), damageTaken));
                }
            }

            this.lastHealth = currentHealth;
        }

        if (this.mode.getValue().equals("SMP")) {
            if (mc.isWindowFocused()) {
                if (!this.isOffhandSwapPending()) {
                    boolean maceDetected = false;
                    if (this.antiMace.getValue() && !PlayerInteractions.isBlocking()) {
                        PlayerEntity enemy = TargetSelector.getClosestEnemy();
                        if (enemy != null) {
                            if (!Float.isNaN(this.lastEnemyY) && enemy.getY() - this.lastEnemyY >= 0.4F && mc.player.getY() < enemy.getY()) {
                                Vec3d enemyAtPlayerY = new Vec3d(enemy.getX(), mc.player.getY(), enemy.getZ());
                                if (Math.sqrt(mc.player.squaredDistanceTo(enemyAtPlayerY)) < 8.0) {
                                    maceDetected = true;
                                    Prestige.notificationManager.addNotification("Auto Totem", "Detected mace look, switching to totem!", 3000L, NotificationType.WARNING);
                                }
                            }

                            this.lastEnemyY = (float)enemy.getY();
                        }
                    }

                    if (!this.isDelayActive() || maceDetected) {
                        if (this.slotToReselect != -1) {
                            InventoryUtil.selectSlot(this.slotToReselect);
                            this.slotToReselect = -1;
                        } else if (!this.waitWhenShielding.getValue() || !PlayerInteractions.isBlocking()) {
                            if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > this.health.getValue() && !maceDetected) {
                                if (this.totemOriginSlot != -1 && !mc.player.isUsingItem()) {
                                    if (this.totemOriginSlot < 9) {
                                        this.slotToReselect = InventoryUtil.getSelectedSlot();
                                        if (InventoryUtil.getSelectedSlot() != this.totemOriginSlot) {
                                            InventoryUtil.selectSlot(this.totemOriginSlot);
                                            PacketSender.sendPacket(
                                                new UpdateSelectedSlotC2SPacket(InventoryUtil.getSelectedSlot())
                                            );
                                        }

                                        PacketSender.sendPacket(
                                            new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN)
                                        );
                                        this.totemOriginSlot = -1;
                                    } else {
                                        this.requestOffhandSwap(this.totemOriginSlot, ArmorState.RESTORE);
                                    }
                                }

                                this.startActionDelay();
                            } else if (!InventoryUtil.isHoldingOffhand(Items.TOTEM_OF_UNDYING) && this.totemOriginSlot == -1) {
                                Iterator totemSlotIterator = InventoryUtil.findInventorySlots(Items.TOTEM_OF_UNDYING, true).iterator();
                                if (totemSlotIterator.hasNext()) {
                                    Integer firstTotemSlot = (Integer)totemSlotIterator.next();
                                    if (firstTotemSlot < 9) {
                                        this.slotToReselect = InventoryUtil.getSelectedSlot();
                                        if (InventoryUtil.getSelectedSlot() != firstTotemSlot) {
                                            InventoryUtil.selectSlot(firstTotemSlot);
                                            PacketSender.sendPacket(
                                                new UpdateSelectedSlotC2SPacket(InventoryUtil.getSelectedSlot())
                                            );
                                        }

                                        PacketSender.sendPacket(
                                            new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN)
                                        );
                                        this.totemOriginSlot = firstTotemSlot;
                                    } else {
                                        this.requestOffhandSwap(firstTotemSlot, ArmorState.EQUIP);
                                    }
                                }

                                this.startActionDelay();
                            } else {
                                this.startActionDelay();
                            }
                        }
                    }
                }
            }
        } else {
            if (this.extra.isSelected("Double Hand On GUI") && mc.currentScreen != null) {
                Integer hotbarTotemSlot = InventoryUtil.findHotbarSlot(Items.TOTEM_OF_UNDYING);
                if (hotbarTotemSlot != null) {
                    InventoryUtil.selectSlot(hotbarTotemSlot);
                }
            }
        }
    }

    private boolean isDelayActive() {
        return !this.actionTimer.hasElapsedRandom(this.delay);
    }

    private boolean startActionDelay() {
        this.delay.reroll();
        if (this.dynamicDelay.getValue() && this.mode.getValue().equals("Crystal")) {
            float danger = this.computeDangerScore();
            float rolledDelay = this.delay.randomInRange();
            float reduction = (rolledDelay - this.delay.getMin()) * (float)Math.pow(danger / 100.0F, 2.0);
            float adjustedDelay = Math.max(this.delay.getMin(), rolledDelay - reduction);
            this.delay.setRolled(adjustedDelay);
        }

        this.actionTimer.reset();
        return true;
    }

    private void createRobot() {
        try {
            FieldAccessor.of(GraphicsEnvironment.class, "headless").set(false);
            this.robot = new Robot();
        } catch (Exception ignored) {
        }
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.TargetSelector;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.type.DoubleAnchorState;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.world.RaycastUtil;
import dev.zprestige.prestige.world.BlockUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class DoubleAnchor extends Module {
    private FloatSetting chance;
    private BoolSetting glowStone;
    private RangeSetting delay;
    private KeybindSetting macroBind;
    private BoolSetting macroHoldRepeat;
    private RangeSetting macroDelay;
    private EnumSetting macroSwap;
    private BoolSetting macroSwapBack;
    private IntSetting macroExplodeSlot;
    private BoolSetting macroDamageTick;
    private FloatSetting macroDamageTickRange;
    private IntSetting macroDamageTickMaxWait;
    private StopWatch doubleAnchorTimer = new StopWatch();
    private StopWatch glowstoneTimer = new StopWatch();
    private BlockPos lastAnchorPos = null;
    private int doubleAnchorCount = 0;
    private boolean glowstonePending = false;
    public static boolean acting;
    private int actingUntilAge = 0;
    private StopWatch macroStageTimer = new StopWatch();
    private StopWatch damageTickWaitTimer = new StopWatch();
    private DoubleAnchorState macroState = DoubleAnchorState.IDLE;
    private BlockHitResult startHit = null;
    private BlockPos macroAnchorPos = null;
    private int startSlot = -1;
    private boolean startedThisPress = false;
    private boolean runningMacroStage = false;
    private boolean damageTickWaitNotified = false;

    public DoubleAnchor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.macroSwap.visibleWhen((Predicate<String>)ignored -> this.macroBind.getValue() != -1);
        this.macroDelay.visibleWhen((Predicate<Float>)ignored -> this.macroBind.getValue() != -1);
        this.macroDamageTickRange
            .visibleWhen(
                (Predicate<Float>)ignored -> this.macroBind.getValue() != -1 && this.macroDamageTick.getValue()
            );
        this.macroDamageTickMaxWait
            .visibleWhen(
                (Predicate<Integer>)ignored -> this.macroBind.getValue() != -1 && this.macroDamageTick.getValue()
            );
        this.macroSwapBack
            .visibleWhen(
                (Predicate<Boolean>)ignored -> this.macroBind.getValue() != -1 && this.macroSwap.getValue().equals("Swap")
            );
        this.macroDamageTick.visibleWhen((Predicate<Boolean>)ignored -> this.macroBind.getValue() != -1);
        this.macroHoldRepeat.visibleWhen((Predicate<Boolean>)ignored -> this.macroBind.getValue() != -1);
        this.macroExplodeSlot
            .visibleWhen(
                (Predicate<Integer>)ignored -> this.macroBind.getValue() != -1 && this.macroSwap.getValue().equals("Swap")
            );
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal);
    }

    @Override
    public void onEnable() {
        this.resetMacro();
        this.startedThisPress = false;
    }

    @Override
    public void onDisable() {
        this.resetMacro();
        this.startedThisPress = false;
        acting = false;
        this.actingUntilAge = 0;
    }

    private void resetMacro() {
        this.macroState = DoubleAnchorState.IDLE;
        this.startHit = null;
        this.macroAnchorPos = null;
        this.startSlot = -1;
        this.runningMacroStage = false;
        this.damageTickWaitNotified = false;
    }

    private boolean isChargedAnchor(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR) && mc.world.getBlockState(pos).get(Properties.CHARGES) > 0;
    }

    private boolean isAnchor(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private boolean shouldWaitForDamageTick() {
        if (!this.macroDamageTick.getValue()) {
            return false;
        } else {
            PlayerEntity enemy = TargetSelector.getClosestEnemy();
            if (enemy == null) {
                return false;
            } else if (mc.player.distanceTo(enemy) >= this.macroDamageTickRange.getValue()) {
                return false;
            } else if (enemy.hurtTime == 0) {
                return false;
            } else {
                StopWatch waitTimer = this.damageTickWaitTimer;
                float maxWaitMs = this.macroDamageTickMaxWait.getValue().intValue();
                return !waitTimer.hasElapsed(maxWaitMs);
            }
        }
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        acting = this.runningMacroStage || mc.player != null && mc.player.age <= this.actingUntilAge;
        if (this.glowStone.getValue() && this.glowstonePending) {
            if (this.glowstoneTimer.hasElapsed(this.delay.getValue())) {
                if (!(mc.crosshairTarget instanceof BlockHitResult crosshairHit)) {
                    this.glowstonePending = false;
                    return;
                }

                if (mc.world.getBlockState(crosshairHit.getBlockPos()).getBlock() == Blocks.RESPAWN_ANCHOR
                    && mc.world.getBlockState(crosshairHit.getBlockPos()).get(Properties.CHARGES) == 0) {
                    this.glowstonePending = false;
                    this.actingUntilAge = mc.player.age + 2;
                    acting = true;
                    InventoryUtil.runWithItem(Items.GLOWSTONE, () -> PlayerInteractions.interactBlock(crosshairHit), false);
                    return;
                }

                this.glowstonePending = false;
                return;
            }
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (!this.runningMacroStage) {
            if (event.getPacket() instanceof PlayerInteractBlockC2SPacket) {
                if (this.doubleAnchorTimer.hasElapsed(200.0F)) {
                    if (!InventoryUtil.isHolding(Items.RESPAWN_ANCHOR)) {
                        return;
                    }

                    BlockHitResult crosshairHit = mc.crosshairTarget instanceof BlockHitResult blockCrosshair ? blockCrosshair : null;
                    if (crosshairHit != null
                        && mc.world.getBlockState(crosshairHit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)
                        && mc.world.getBlockState(crosshairHit.getBlockPos()).get(Properties.CHARGES) != 0) {
                        if (RandomUtil.randomFloat(0.0F, 100.0F) > this.chance.getValue()) {
                            return;
                        }

                        if (crosshairHit.getBlockPos().equals(this.lastAnchorPos)) {
                            if (this.doubleAnchorCount >= 1) {
                                return;
                            }
                        } else {
                            this.lastAnchorPos = crosshairHit.getBlockPos();
                            this.doubleAnchorCount = 0;
                        }

                        this.doubleAnchorTimer.reset();
                        this.actingUntilAge = mc.player.age + 2;
                        acting = true;
                        PlayerInteractions.interactBlock(crosshairHit);
                        this.glowstonePending = true;
                        this.delay.reroll();
                        this.glowstoneTimer.reset();
                        this.doubleAnchorCount++;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.macroBind.getValue() != -1) {
            if (mc.player != null && mc.world != null) {
                if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem() && !mc.player.isSneaking()) {
                    if (this.macroState != DoubleAnchorState.IDLE) {
                        if (this.macroStageTimer.hasElapsedRandom(this.macroDelay)) {
                            this.runMacroStage();
                        }
                    } else {
                        if (!this.macroBind.isPressed()) {
                            this.startedThisPress = false;
                        } else if (!this.startedThisPress || this.macroHoldRepeat.getValue()) {
                            if (this.macroStageTimer.hasElapsedRandom(this.macroDelay)) {
                                if (mc.crosshairTarget instanceof BlockHitResult crosshairHit && crosshairHit.getType() == Type.BLOCK) {
                                    if (BlockUtil.isClickableBlockHit(crosshairHit)) {
                                        if (!mc.world.getBlockState(crosshairHit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
                                            BlockPos placePos = mc.world.getBlockState(crosshairHit.getBlockPos()).isReplaceable()
                                                ? crosshairHit.getBlockPos()
                                                : BlockUtil.getPlacePos(crosshairHit);
                                            if (!mc.player.getBoundingBox().intersects(new Box(placePos))) {
                                                this.startedThisPress = true;
                                                this.startHit = crosshairHit;
                                                this.macroAnchorPos = placePos;
                                                this.startSlot = InventoryUtil.getSelectedSlot();
                                                this.macroState = DoubleAnchorState.PLACE_1;
                                                this.macroStageTimer.reset();
                                                this.macroDelay.reroll();
                                                this.notifyInfo("Macro: sequence started");
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

    private void runMacroStage() {
        this.runningMacroStage = true;

        label329: {
            try {
                switch (this.macroState) {
                    case PLACE_1:
                        if (mc.player.getBoundingBox().intersects(new Box(this.macroAnchorPos))) {
                            this.abortMacro("player blocking placement");
                            return;
                        }

                        if (this.placeAnchor(this.startHit)) {
                            this.notifyInfo("Macro: placed anchor");
                            this.macroState = DoubleAnchorState.GLOW_1;
                            break label329;
                        }

                        this.abortMacro("no respawn anchor");
                        return;
                    case GLOW_1:
                        if (!this.isAnchor(this.macroAnchorPos)) {
                            this.abortMacro("anchor missing");
                            return;
                        }

                        if (this.chargeAnchor(this.createTopFaceHit(this.macroAnchorPos))) {
                            this.notifyInfo("Macro: charged anchor");
                            this.macroState = DoubleAnchorState.EXPLODE_DOUBLE_1;
                            break label329;
                        }

                        this.abortMacro("no glowstone");
                        return;
                    case EXPLODE_DOUBLE_1:
                        if (!this.isChargedAnchor(this.macroAnchorPos)) {
                            this.abortMacro("anchor not charged");
                            return;
                        }

                        if (this.explodeAndDoublePlace(this.createTopFaceHit(this.macroAnchorPos))) {
                            this.notifyInfo("Macro: exploded + double-placed");
                            this.macroState = DoubleAnchorState.GLOW_2;
                            break label329;
                        }

                        this.abortMacro("explode failed");
                        return;
                    case GLOW_2:
                        if (!this.isAnchor(this.macroAnchorPos)) {
                            this.abortMacro("double failed");
                            return;
                        }

                        if (this.chargeAnchor(this.createTopFaceHit(this.macroAnchorPos))) {
                            this.notifyInfo("Macro: charged again");
                            this.macroState = DoubleAnchorState.EXPLODE_2;
                            this.damageTickWaitTimer.reset();
                            this.damageTickWaitNotified = false;
                            break label329;
                        }

                        this.abortMacro("no glowstone (2)");
                        return;
                    case EXPLODE_2:
                        if (!this.isChargedAnchor(this.macroAnchorPos)) {
                            this.abortMacro("second anchor not charged");
                            return;
                        }

                        if (!this.shouldWaitForDamageTick()) {
                            this.damageTickWaitNotified = false;
                            if (this.explodeAnchor(this.createTopFaceHit(this.macroAnchorPos))) {
                                this.notifyInfo("Macro: exploded again");
                                this.macroState = DoubleAnchorState.RESTORE;
                                break label329;
                            }

                            this.abortMacro("explode failed (2)");
                            return;
                        }

                        if (!this.damageTickWaitNotified) {
                            this.notifyInfo("Macro: waiting for damage tick");
                            this.damageTickWaitNotified = true;
                        }
                        break;
                    case RESTORE:
                        if (this.macroSwapBack.getValue() && "Swap".equals(this.macroSwap.getValue()) && this.startSlot != -1) {
                            Prestige.silentRotationManager.scheduleSlotSwitch(this.startSlot);
                        }

                        this.notifyInfo("Macro: done");
                        this.resetMacro();
                    default:
                        break label329;
                }
            } finally {
                this.runningMacroStage = false;
            }

            return;
        }

        this.macroStageTimer.reset();
        this.macroDelay.reroll();
    }

    private void abortMacro(String reason) {
        if ("Swap".equals(this.macroSwap.getValue()) && this.startSlot != -1) {
            Prestige.silentRotationManager.scheduleSlotSwitch(this.startSlot);
        }

        this.notifyInfo("Macro aborted: " + reason);
        this.resetMacro();
        this.macroStageTimer.reset();
        this.macroDelay.reroll();
    }

    private void notifyInfo(String message) {
        ModuleNotificationsElement.postNotification(this, message, NotificationType.INFO);
    }

    private BlockHitResult createTopFaceHit(BlockPos anchorPos) {
        Vec3d hitVec = RaycastUtil.getVisibleTopPoint(anchorPos);
        if (hitVec == null) {
            hitVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 1.0, anchorPos.getZ() + 0.5);
        }

        return new BlockHitResult(hitVec, Direction.UP, anchorPos, false);
    }

    private boolean placeAnchor(BlockHitResult hitResult) {
        if ("None".equals(this.macroSwap.getValue())) {
            if (!InventoryUtil.isHolding(Items.RESPAWN_ANCHOR)) {
                return false;
            }
        } else if (!InventoryUtil.switchToBlock(Blocks.RESPAWN_ANCHOR)) {
            return false;
        }

        PlayerInteractions.interactBlock(hitResult);
        return true;
    }

    private boolean chargeAnchor(BlockHitResult hitResult) {
        if ("None".equals(this.macroSwap.getValue())) {
            if (!InventoryUtil.isHolding(Items.GLOWSTONE)) {
                return false;
            }
        } else if (!InventoryUtil.switchToBlock(Blocks.GLOWSTONE)) {
            return false;
        }

        PlayerInteractions.interactBlock(hitResult);
        return true;
    }

    private boolean explodeAnchor(BlockHitResult hitResult) {
        if ("None".equals(this.macroSwap.getValue())) {
            if (InventoryUtil.isHolding(Items.GLOWSTONE)) {
                return false;
            }
        } else {
            InventoryUtil.selectSlot(this.macroExplodeSlot.getValue() - 1);
        }

        PlayerInteractions.interactBlock(hitResult);
        return true;
    }

    private boolean explodeAndDoublePlace(BlockHitResult hitResult) {
        if (!this.explodeAnchor(hitResult)) {
            return false;
        } else {
            if ("None".equals(this.macroSwap.getValue())) {
                if (InventoryUtil.isHolding(Items.RESPAWN_ANCHOR)) {
                    PlayerInteractions.interactBlock(hitResult);
                }
            } else if (InventoryUtil.switchToBlock(Blocks.RESPAWN_ANCHOR)) {
                PlayerInteractions.interactBlock(hitResult);
            }

            return true;
        }
    }
}

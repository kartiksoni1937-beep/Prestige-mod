package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.type.ScoopState;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AntiWeb extends Module implements RotationProvider {
    private EnumSetting mode;
    private RangeSetting delay;
    private EnumSetting swap;
    private BoolSetting swapBack;
    private BoolSetting partialWebs;
    private ScoopState state = ScoopState.IDLE;
    private BlockPos topWebPos;
    private BlockPos webPos;
    private int previousSlot = -1;
    private int waitTicks;
    private int scoopAttempts;
    private StopWatch actionTimer = new StopWatch();
    private StopWatch notifyTimer = new StopWatch();

    public AntiWeb() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.swapBack.visibleWhen((Predicate<Boolean>)ignored -> this.swap.getValue().equals("Normal"));
    }

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.UHC);
    }

    @Override
    public void onEnable() {
        this.resetState();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        if (this.swapBack.getValue() && this.previousSlot != -1) {
            if (Prestige.silentRotationManager.hasActedThisTick()) {
                Prestige.silentRotationManager.scheduleSlotSwitch(this.previousSlot);
            } else {
                InventoryUtil.selectSlot(this.previousSlot);
            }

            this.previousSlot = -1;
        }

        this.resetState();
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState();
    }

    private void resetState() {
        this.state = ScoopState.IDLE;
        this.topWebPos = null;
        this.webPos = null;
        this.waitTicks = 0;
        this.scoopAttempts = 0;
        this.actionTimer.reset();
        this.delay.reroll();
    }

    private void notifyThrottled(String message) {
        if (this.notifyTimer.hasElapsed(3000.0F)) {
            ModuleNotificationsElement.postNotification(this, message, NotificationType.INFO);
            this.notifyTimer.reset();
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.resetState();
        } else if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
            if (!Prestige.silentRotationManager.hasActedThisTick()) {
                switch (this.state) {
                    case IDLE:
                        this.detectWeb();
                    case PLACE:
                    case SCOOP:
                    default:
                        break;
                    case WAIT:
                        if (this.isWebCleared()) {
                            this.state = ScoopState.SCOOP;
                            this.actionTimer.reset();
                            this.delay.reroll();
                        } else if (++this.waitTicks > 40) {
                            this.notifyThrottled("The water never cleared the web.");
                            this.abortEscape();
                        }
                        break;
                    case BACK:
                        if (!this.actionTimer.hasElapsedRandom(this.delay)) {
                            return;
                        }

                        if (this.swapBack.getValue() && this.previousSlot != -1 && !Prestige.silentRotationManager.hasActedThisTick()) {
                            InventoryUtil.selectSlot(this.previousSlot);
                            this.previousSlot = -1;
                        }

                        this.resetState();
                }

                if (this.mode.getValue().equalsIgnoreCase("Legit")) {
                    this.tickLegit();
                }
            }
        }
    }

    private void detectWeb() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos foundWeb = null;
        if (this.isCobweb(playerPos.down())) {
            foundWeb = playerPos.down();
        } else if (this.isCobweb(playerPos)) {
            foundWeb = playerPos;
        } else if (this.isCobweb(playerPos.up())) {
            foundWeb = playerPos.up();
        }

        if (foundWeb == null && this.partialWebs.getValue()) {
            Box playerBox = mc.player.getBoundingBox();

            for (int dy = 0; dy <= 1 && foundWeb == null; dy++) {
                for (int dx = -1; dx <= 1 && foundWeb == null; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            BlockPos candidate = playerPos.add(dx, dy, dz);
                            if (this.isCobweb(candidate)
                                && playerBox.maxX > candidate.getX()
                                && playerBox.minX < candidate.getX() + 1
                                && playerBox.maxZ > candidate.getZ()
                                && playerBox.minZ < candidate.getZ() + 1
                                && playerBox.maxY > candidate.getY()
                                && playerBox.minY < candidate.getY() + 1) {
                                foundWeb = candidate;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (foundWeb != null) {
            this.webPos = foundWeb;
            this.topWebPos = foundWeb;

            while (this.isCobweb(this.topWebPos.up())) {
                this.topWebPos = this.topWebPos.up();
            }

            if (this.swap.getValue().equals("Normal") && InventoryUtil.findHotbarSlot(Items.WATER_BUCKET) == null) {
                this.notifyThrottled("No water bucket in hotbar.");
            } else if (mc.world.getFluidState(this.topWebPos.up()).isStill()) {
                this.state = ScoopState.WAIT;
                this.waitTicks = 0;
            } else if (!mc.world.getBlockState(this.topWebPos.up()).isReplaceable()) {
                this.notifyThrottled("No room above the web for water.");
            } else {
                this.state = ScoopState.PLACE;
                this.waitTicks = 0;
                this.scoopAttempts = 0;
                this.actionTimer.reset();
                this.delay.reroll();
            }
        }
    }

    private boolean isCobweb(BlockPos pos) {
        return pos != null && mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB;
    }

    private boolean isWebCleared() {
        for (BlockPos pos = this.webPos; !this.isCobweb(pos); pos = pos.up()) {
            if (pos.equals(this.topWebPos)) {
                return true;
            }
        }

        return false;
    }

    private Vec3d getAimPoint(BlockPos pos) {
        float yawRadians = (float)Math.toRadians(mc.player.getYaw());
        return pos.toCenterPos().add(-Math.sin(yawRadians) * 0.25, 0.0, Math.cos(yawRadians) * 0.25);
    }

    private void tickLegit() {
        switch (this.state) {
            case PLACE:
                if (!this.actionTimer.hasElapsedRandom(this.delay)) {
                    return;
                }

                if (!this.isCobweb(this.topWebPos)) {
                    if (mc.world.getFluidState(this.topWebPos.up()).isStill()) {
                        this.state = ScoopState.WAIT;
                        this.waitTicks = 0;
                        this.actionTimer.reset();
                        this.delay.reroll();
                    } else {
                        this.startSwapBack();
                    }

                    return;
                }

                if (!this.ensureHolding(Items.WATER_BUCKET)) {
                    return;
                }

                if (!(mc.crosshairTarget instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(this.topWebPos)) {
                    return;
                }

                if (PlayerInteractions.interactItem(Hand.MAIN_HAND)) {
                    this.state = ScoopState.WAIT;
                    this.waitTicks = 0;
                    this.actionTimer.reset();
                    this.delay.reroll();
                }
                break;
            case SCOOP:
                if (!this.actionTimer.hasElapsedRandom(this.delay)) {
                    return;
                }

                if (!this.ensureHolding(Items.BUCKET)) {
                    return;
                }

                if (!mc.world.getFluidState(this.topWebPos.up()).isStill()) {
                    this.startSwapBack();
                    return;
                }

                if (this.useItemFacing(this.topWebPos.up().toCenterPos())) {
                    this.startSwapBack();
                } else if (++this.scoopAttempts > 6) {
                    this.notifyThrottled("Couldn't scoop the water.");
                    this.startSwapBack();
                }
        }
    }

    private boolean useItemFacing(Vec3d point) {
        Rotation rotation = RotationUtil.getEntityRotationTo(mc.player, point);
        float savedYaw = mc.player.getYaw();
        float savedPitch = mc.player.getPitch();
        mc.player.setYaw(rotation.getYaw());
        mc.player.setPitch(rotation.getPitch());

        boolean used;
        try {
            used = PlayerInteractions.interactItem(Hand.MAIN_HAND);
        } finally {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }

        return used;
    }

    @Override
    public Rotation provideRotation() {
        if (!this.mode.getValue().equalsIgnoreCase("Blatant")) {
            return null;
        } else if (mc.player == null || mc.world == null) {
            return null;
        } else if (mc.currentScreen == null && mc.isWindowFocused() && !mc.player.isUsingItem()) {
            if (Prestige.silentRotationManager.hasActedThisTick()) {
                return null;
            } else {
                ScoopState currentState = this.state;
                if (currentState != ScoopState.PLACE && currentState != ScoopState.SCOOP) {
                    return null;
                } else if (!this.actionTimer.hasElapsedRandom(this.delay)) {
                    return null;
                } else {
                    Vec3d aimPoint = this.getAimPoint((BlockPos)(currentState == ScoopState.PLACE ? this.topWebPos : this.topWebPos.up()));
                    Rotation rotation = Prestige.silentRotationManager.getRotationTowards(aimPoint);
                    if (rotation.isClamped()) {
                        return rotation;
                    } else {
                        Prestige.silentRotationManager.setActiveRotation(rotation);
                        if (currentState == ScoopState.PLACE) {
                            if (!this.ensureHolding(Items.WATER_BUCKET)) {
                                return null;
                            }

                            if (this.useItemWithRotation(rotation)) {
                                this.state = ScoopState.WAIT;
                                this.waitTicks = 0;
                                this.actionTimer.reset();
                                this.delay.reroll();
                            }
                        } else {
                            if (!this.ensureHolding(Items.BUCKET)) {
                                return null;
                            }

                            if (!mc.world.getFluidState(this.topWebPos.up()).isStill()) {
                                this.startSwapBack();
                                return null;
                            }

                            if (this.useItemWithRotation(rotation)) {
                                this.startSwapBack();
                            } else if (++this.scoopAttempts > 6) {
                                this.notifyThrottled("Couldn't scoop the water.");
                                this.startSwapBack();
                            }
                        }

                        return rotation;
                    }
                }
            }
        } else {
            return null;
        }
    }

    private boolean useItemWithRotation(Rotation rotation) {
        float savedYaw = mc.player.getYaw();
        float savedPitch = mc.player.getPitch();
        mc.player.setYaw(rotation.getYaw());
        mc.player.setPitch(rotation.getPitch());

        boolean used;
        try {
            used = PlayerInteractions.interactItem(Hand.MAIN_HAND);
        } finally {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }

        return used;
    }

    private boolean ensureHolding(Item item) {
        if (InventoryUtil.isHolding(item)) {
            return true;
        } else if (!this.swap.getValue().equals("Normal")) {
            return false;
        } else if (InventoryUtil.findHotbarSlot(item) == null) {
            this.abortEscape();
            return false;
        } else {
            if (this.previousSlot == -1) {
                this.previousSlot = InventoryUtil.getSelectedSlot();
            }

            InventoryUtil.switchToItem(item);
            return false;
        }
    }

    private void startSwapBack() {
        this.state = ScoopState.BACK;
        this.actionTimer.reset();
        this.delay.reroll();
    }

    private void abortEscape() {
        if (this.swapBack.getValue() && this.previousSlot != -1 && !Prestige.silentRotationManager.hasActedThisTick()) {
            InventoryUtil.selectSlot(this.previousSlot);
            this.previousSlot = -1;
        }

        this.resetState();
    }
}

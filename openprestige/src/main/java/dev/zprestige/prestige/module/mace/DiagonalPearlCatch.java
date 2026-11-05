package dev.zprestige.prestige.module.mace;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.math.MotionState;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import dev.zprestige.prestige.render.PearlThrowSolution;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.type.DiagonalPearlState;
import dev.zprestige.prestige.world.RotationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class DiagonalPearlCatch extends Module implements RotationProvider {
    public DiagonalPearlCatch() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private EnumSetting mode;
    private FloatSetting windChargeDelay;
    private BoolSetting silentAim;
    private FloatSetting maxYawDelta;
    private DiagonalPearlState state = DiagonalPearlState.IDLE;
    private int ticksUntilWindCharge;
    private List pearlPath;
    private PearlThrowSolution solution;
    private StopWatch pearlTimer = new StopWatch();
    private boolean macroPearlThrown;
    private Rotation lockedRotation;
    private boolean rotationSynced;
    private boolean rotationApplied;
    private boolean itemUsed;
    private boolean disableAfterRelease;
    private Object lockWorld;
    private Object trackedWorld;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Mace);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void onEnable() {
        this.resetState();
        Prestige.silentRotationManager.addRotationProvider(this);
    }

    @Override
    public void onDisable() {
        this.resetState();
        this.trackedWorld = null;
        Prestige.silentRotationManager.removeRotationProvider(this);
    }

    private void resetState() {
        this.state = DiagonalPearlState.IDLE;
        this.ticksUntilWindCharge = 0;
        this.pearlPath = null;
        this.solution = null;
        this.macroPearlThrown = false;
        this.pearlTimer.reset();
        this.releaseRotationLock();
        this.trackedWorld = mc.world;
    }

    @Subscribe
    public void onGameJoin(GameJoinEvent event) {
        this.resetState();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.trackedWorld != mc.world) {
            this.resetState();
        }

        if (!Prestige.silentRotationManager.canRotate()) {
            this.resetState();
        } else if (mc.player != null && mc.world != null && mc.isWindowFocused() && mc.currentScreen == null) {
            int windChargeSlot = this.findHotbarSlot(
                (Predicate<ItemStack>)windChargeCandidate -> !windChargeCandidate.isEmpty() && InventoryUtil.isWindCharge(windChargeCandidate)
            );
            if (windChargeSlot == -1) {
                this.toggle();
            } else {
                boolean needsPearl = this.mode.getValue().equals("Macro") ? !this.macroPearlThrown : this.state == DiagonalPearlState.IDLE || this.state == DiagonalPearlState.THROW_PEARL;
                int pearlSlot = -1;
                if (needsPearl) {
                    pearlSlot = this.findHotbarSlot(
                        (Predicate<ItemStack>)pearlCandidate -> !pearlCandidate.isEmpty() && pearlCandidate.getItem() == Items.ENDER_PEARL
                    );
                    if (pearlSlot == -1) {
                        this.toggle();
                        return;
                    }
                }

                if (this.mode.getValue().equals("Macro")) {
                    this.runMacroMode(pearlSlot, windChargeSlot);
                } else {
                    this.runTrajectoryMode(pearlSlot, windChargeSlot);
                }
            }
        } else {
            if (!this.itemUsed) {
                this.releaseRotationLock();
            }
        }
    }

    private void runMacroMode(int pearlSlot, int windChargeSlot) {
        if (!this.macroPearlThrown) {
            if (this.throwFromSlot(pearlSlot, this.getViewRotation())) {
                this.macroPearlThrown = true;
                this.pearlTimer.reset();
            }
        } else {
            if (this.pearlTimer.hasElapsed(this.windChargeDelay.getValue())) {
                if (this.throwFromSlot(windChargeSlot, this.getViewRotation())) {
                    this.disableAfterRelease = true;
                }
            }
        }
    }

    private void runTrajectoryMode(int pearlSlot, int windChargeSlot) {
        switch (this.state) {
            case IDLE:
                if (!this.solveIntercept(this.getViewRotation())) {
                    this.toggle();
                    return;
                }

                this.state = DiagonalPearlState.THROW_PEARL;
                break;
            case THROW_PEARL:
                if (this.rotationSynced
                    && !this.itemUsed
                    && !Prestige.silentRotationManager.hasActedThisTick()
                    && !this.solveIntercept(this.lockedRotation)) {
                    this.toggle();
                    return;
                }

                if (this.throwFromSlot(pearlSlot, this.getViewRotation())) {
                    this.ticksUntilWindCharge = this.solution.throwTick;
                    this.state = this.ticksUntilWindCharge > 0 ? DiagonalPearlState.WAIT_FOR_LAUNCH : DiagonalPearlState.THROW_WIND_CHARGE;
                }
                break;
            case WAIT_FOR_LAUNCH:
                if (--this.ticksUntilWindCharge <= 0) {
                    this.state = DiagonalPearlState.THROW_WIND_CHARGE;
                    this.throwWindCharge(windChargeSlot);
                }
                break;
            case THROW_WIND_CHARGE:
                this.throwWindCharge(windChargeSlot);
        }
    }

    private void throwWindCharge(int windChargeSlot) {
        if (!this.throwFromSlot(windChargeSlot, this.getWindChargeRotation()) && !this.itemUsed) {
            this.toggle();
        } else {
            this.disableAfterRelease = true;
        }
    }

    @Override
    public Rotation provideRotation() {
        if (this.trackedWorld != mc.world) {
            this.resetState();
        }

        if (!Prestige.silentRotationManager.canRotate()) {
            this.resetState();
            return null;
        } else if (this.lockedRotation != null) {
            return this.lockedRotation;
        } else if (!this.mode.getValue().equals("Macro")) {
            if (this.state == DiagonalPearlState.THROW_PEARL) {
                return this.lockRotation(this.getViewRotation());
            } else if (this.state == DiagonalPearlState.WAIT_FOR_LAUNCH) {
                Rotation interceptRotation = this.getInterceptRotation();
                return interceptRotation == null && this.ticksUntilWindCharge > 2 ? null : this.lockRotation((Rotation)(interceptRotation != null ? interceptRotation : this.getViewRotation()));
            } else {
                return this.state == DiagonalPearlState.THROW_WIND_CHARGE ? this.lockRotation(this.getWindChargeRotation()) : null;
            }
        } else {
            return (!this.macroPearlThrown || this.pearlTimer.getElapsed() + 55.0F >= this.windChargeDelay.getValue()) ? this.lockRotation(this.getViewRotation()) : null;
        }
    }

    private Rotation getInterceptRotation() {
        if (!this.mode.getValue().equals("Trajectory")) {
            return null;
        } else if (!this.silentAim.getValue()) {
            return null;
        } else if (mc.player == null) {
            return null;
        } else if (this.solution == null) {
            return null;
        } else if (this.state != DiagonalPearlState.WAIT_FOR_LAUNCH && this.state != DiagonalPearlState.THROW_WIND_CHARGE) {
            return null;
        } else {
            return Math.abs(RotationUtil.wrapDegrees(this.solution.yaw - mc.player.getYaw())) > this.maxYawDelta.getValue() ? null : new Rotation(this.solution.yaw, this.solution.pitch);
        }
    }

    private Rotation getWindChargeRotation() {
        Rotation interceptRotation = this.getInterceptRotation();
        return interceptRotation != null ? interceptRotation : this.getViewRotation();
    }

    private Rotation getViewRotation() {
        return new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private boolean solveIntercept(Rotation pearlRotation) {
        List playerStates = this.simulatePlayerMotion(mc.player.getEyePos(), mc.player.getVelocity(), mc.player.isOnGround(), 17);
        this.pearlPath = this.simulatePearlPath((MotionState)playerStates.get(0), pearlRotation.getYaw(), pearlRotation.getPitch());
        this.solution = this.findBestIntercept(playerStates, this.pearlPath);
        return this.solution != null;
    }

    private boolean throwFromSlot(int slot, Rotation rotation) {
        if (Prestige.silentRotationManager.hasActedThisTick()) {
            return false;
        } else {
            Rotation throwRotation = this.lockRotation(rotation);
            if (this.rotationSynced && !this.itemUsed) {
                InventoryUtil.selectSlot(slot);
                float previousYaw = mc.player.getYaw();
                float previousPitch = mc.player.getPitch();

                boolean success;
                try {
                    mc.player.setYaw(throwRotation.getYaw());
                    mc.player.setPitch(throwRotation.getPitch());
                    boolean used = PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    this.itemUsed = true;
                    success = used;
                } finally {
                    mc.player.setYaw(previousYaw);
                    mc.player.setPitch(previousPitch);
                }

                return success;
            } else {
                return false;
            }
        }
    }

    private Rotation lockRotation(Rotation requestedRotation) {
        if (this.lockedRotation == null) {
            float adjustedYaw = requestedRotation.getYaw();
            if (mc.player != null) {
                adjustedYaw = mc.player.getYaw() + RotationUtil.wrapDegrees(adjustedYaw - mc.player.getYaw());
            }

            this.lockedRotation = new Rotation(adjustedYaw, requestedRotation.getPitch());
            this.rotationSynced = false;
            this.rotationApplied = false;
            this.itemUsed = false;
            this.lockWorld = mc.world;
        }

        Prestige.silentRotationManager.tryLockRotation(this, this.lockedRotation);
        return this.lockedRotation;
    }

    @Subscribe
    public void onSendMovementPackets(SendMovementPacketsEvent event) {
        if (this.lockedRotation != null) {
            if (event.getPhase() == EventPhase.PRE) {
                if (!Prestige.silentRotationManager.tryLockRotation(this, this.lockedRotation)) {
                    this.rotationApplied = false;
                } else {
                    event.setYaw(this.lockedRotation.getYaw());
                    event.setPitch(this.lockedRotation.getPitch());
                    this.rotationApplied = true;
                }
            } else if (event.getPhase() == EventPhase.POST && this.rotationApplied) {
                this.rotationApplied = false;
                if (Prestige.silentRotationManager.isLockHeldBy(this)) {
                    if (this.itemUsed) {
                        boolean shouldDisable = this.disableAfterRelease;
                        this.releaseRotationLock();
                        if (shouldDisable && this.isEnabled()) {
                            this.toggle();
                        }
                    } else {
                        this.rotationSynced = true;
                    }
                }
            }
        }
    }

    private void releaseRotationLock() {
        Prestige.silentRotationManager.releaseLock(this);
        this.lockedRotation = null;
        this.lockWorld = null;
        this.rotationSynced = false;
        this.rotationApplied = false;
        this.itemUsed = false;
        this.disableAfterRelease = false;
    }

    private int findHotbarSlot(Predicate filter) {
        for (int slot = 0; slot < 9; slot++) {
            if (filter.test(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private static Vec3d computeLaunchVelocity(Vec3d direction, double speed, Vec3d shooterVelocity, boolean shooterOnGround) {
        return direction.multiply(speed).add(shooterVelocity.x, shooterOnGround ? 0.0 : shooterVelocity.y, shooterVelocity.z);
    }

    private List simulatePlayerMotion(Vec3d startPos, Vec3d startVelocity, boolean onGround, int ticks) {
        ArrayList states = new ArrayList(ticks + 1);
        Vec3d pos = startPos;
        Vec3d velocity = startVelocity;
        states.add(new MotionState(startPos, startVelocity, onGround));

        for (int tick = 0; tick < ticks; tick++) {
            pos = pos.add(velocity);
            velocity = new Vec3d(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
            states.add(new MotionState(pos, velocity, false));
        }

        return states;
    }

    private List simulatePearlPath(MotionState shooter, float yaw, float pitch) {
        ArrayList path = new ArrayList(31);
        Vec3d velocity = computeLaunchVelocity(directionFromRotation(yaw, pitch), 1.5, shooter.velocity, shooter.onGround);
        Vec3d pos = shooter.pos;
        path.add(pos);

        for (int tick = 0; tick < 30; tick++) {
            pos = pos.add(velocity);
            velocity = velocity.multiply(0.99).subtract(0.0, 0.03, 0.0);
            path.add(pos);
            if (pos.y < shooter.pos.y - 256.0) {
                break;
            }
        }

        return path;
    }

    private List simulateWindChargePath(MotionState shooter, float yaw, float pitch, int ticks) {
        ArrayList path = new ArrayList(ticks + 1);
        Vec3d velocity = computeLaunchVelocity(directionFromRotation(yaw, pitch), 1.5, shooter.velocity, shooter.onGround);
        Vec3d pos = shooter.pos;
        path.add(pos);

        for (int tick = 0; tick < ticks; tick++) {
            pos = pos.add(velocity);
            velocity = velocity.multiply(0.99);
            path.add(pos);
        }

        return path;
    }

    private PearlThrowSolution findBestIntercept(List playerStates, List pearlPositions) {
        PearlThrowSolution best = null;
        double bestMiss = Double.MAX_VALUE;
        int pearlTickCount = pearlPositions.size();
        int stateCount = playerStates.size();

        for (int throwTick = 2; throwTick <= 15 && throwTick < pearlTickCount - 1 && throwTick < stateCount; throwTick++) {
            MotionState throwState = (MotionState)playerStates.get(throwTick);

            for (int pearlTick = throwTick + 1; pearlTick < pearlTickCount; pearlTick++) {
                Vec3d pearlPos = (Vec3d)pearlPositions.get(pearlTick);
                int flightTicks = pearlTick - throwTick;
                Vec3d toPearl = pearlPos.subtract(throwState.pos);
                if (!(toPearl.lengthSquared() < 1.0E-6)) {
                    Vec3d aimVector = toPearl.subtract((throwState.onGround ? new Vec3d(throwState.velocity.x, 0.0, throwState.velocity.z) : throwState.velocity).multiply(flightTicks));
                    float baseYaw = (float)Math.toDegrees(Math.atan2(-aimVector.x, aimVector.z));
                    double horizontalDist = Math.sqrt(aimVector.x * aimVector.x + aimVector.z * aimVector.z);
                    float basePitch = (float)Math.toDegrees(-Math.atan2(aimVector.y, horizontalDist));

                    for (float pitch = basePitch - 5.0F; pitch <= basePitch + 5.0F; pitch += 0.5F) {
                        for (float yaw = baseYaw - 5.0F; yaw <= baseYaw + 5.0F; yaw += 0.5F) {
                            PearlThrowSolution candidate = this.evaluateIntercept(throwState, pearlPositions, throwTick, flightTicks, yaw, pitch);
                            if (candidate != null && candidate.missDistance < bestMiss) {
                                bestMiss = candidate.missDistance;
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            return null;
        } else {
            MotionState refineState = (MotionState)playerStates.get(best.throwTick);

            for (float refinePitch = best.pitch - 0.5F; refinePitch <= best.pitch + 0.5F; refinePitch += 0.05F) {
                for (float refineYaw = best.yaw - 1.0F; refineYaw <= best.yaw + 1.0F; refineYaw += 0.1F) {
                    PearlThrowSolution refined = this.evaluateRefinedIntercept(refineState, pearlPositions, best.throwTick, refineYaw, refinePitch);
                    if (refined != null && refined.missDistance < best.missDistance) {
                        best = refined;
                    }
                }
            }

            return best.missDistance > 0.4 ? null : best;
        }
    }

    private PearlThrowSolution evaluateIntercept(MotionState throwState, List pearlPositions, int throwTick, int flightTicks, float yaw, float pitch) {
        List windPath = this.simulateWindChargePath(throwState, yaw, pitch, flightTicks + 4);
        double bestDistanceSq = Double.MAX_VALUE;
        int closestTick = throwTick;

        for (int step = 1; step < windPath.size(); step++) {
            int pearlTick = throwTick + step;
            if (pearlTick >= pearlPositions.size()) {
                break;
            }

            double distanceSq = ((Vec3d)windPath.get(step)).squaredDistanceTo((Vec3d)pearlPositions.get(pearlTick));
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                closestTick = pearlTick;
            }
        }

        return bestDistanceSq == Double.MAX_VALUE ? null : new PearlThrowSolution(throwTick, yaw, pitch, Math.sqrt(bestDistanceSq), closestTick);
    }

    private PearlThrowSolution evaluateRefinedIntercept(MotionState throwState, List pearlPositions, int throwTick, float yaw, float pitch) {
        int remainingTicks = pearlPositions.size() - throwTick;
        if (remainingTicks <= 1) {
            return null;
        } else {
            List windPath = this.simulateWindChargePath(throwState, yaw, pitch, remainingTicks);
            double bestDistanceSq = Double.MAX_VALUE;
            int closestTick = throwTick;

            for (int step = 1; step < windPath.size(); step++) {
                int pearlTick = throwTick + step;
                if (pearlTick >= pearlPositions.size()) {
                    break;
                }

                double distanceSq = ((Vec3d)windPath.get(step)).squaredDistanceTo((Vec3d)pearlPositions.get(pearlTick));
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    closestTick = pearlTick;
                }
            }

            return bestDistanceSq == Double.MAX_VALUE ? null : new PearlThrowSolution(throwTick, yaw, pitch, Math.sqrt(bestDistanceSq), closestTick);
        }
    }

    private static Vec3d directionFromRotation(float yaw, float pitch) {
        float yawRad = (float)Math.toRadians(yaw);
        float pitchRad = (float)Math.toRadians(pitch);
        double dirX = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double dirY = -MathHelper.sin(pitchRad);
        double dirZ = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        return length < 1.0E-9 ? new Vec3d(0.0, 0.0, 1.0) : new Vec3d(dirX / length, dirY / length, dirZ / length);
    }
}

package dev.zprestige.prestige.module.combat;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Gamemode;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.PearlFollowSimulatedPearl;
import dev.zprestige.prestige.data.PearlThrowOption;
import dev.zprestige.prestige.data.PearlFollowLanding;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.RotationProvider;
import dev.zprestige.prestige.render.StopWatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlFollow extends Module implements RotationProvider {
    public PearlFollow() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    public static boolean active;
    private FloatSetting throwerRange;
    private FloatSetting minLandingDistance;
    private FloatSetting landingRange;
    private FloatSetting maxError;
    private RangeSetting delay;
    private RangeSetting swapBackDelay;
    private BoolSetting onlyOnGround;
    private BoolSetting targetOnly;
    private Set seenPearlIds = new HashSet();
    private StopWatch throwTimer = new StopWatch();
    private StopWatch swapBackTimer = new StopWatch();
    private int previousSlot = -1;
    private Vec3d pendingLanding = null;

    @Override
    public Set getGamemodes() {
        return Set.of(Gamemode.Crystal, Gamemode.Sword, Gamemode.UHC, Gamemode.Mace);
    }

    @Override
    public void onEnable() {
        Prestige.silentRotationManager.addRotationProvider(this);
        this.seenPearlIds.clear();
        this.pendingLanding = null;
        this.previousSlot = -1;
        active = false;
    }

    @Override
    public void onDisable() {
        Prestige.silentRotationManager.removeRotationProvider(this);
        if (this.previousSlot != -1 && mc.player != null) {
            InventoryUtil.selectSlot(this.previousSlot);
        }

        this.seenPearlIds.clear();
        this.pendingLanding = null;
        this.previousSlot = -1;
        active = false;
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.previousSlot != -1 && this.swapBackTimer.hasElapsedRandom(this.swapBackDelay)) {
                InventoryUtil.selectSlot(this.previousSlot);
                this.previousSlot = -1;
            }

            active = this.previousSlot != -1 || this.pendingLanding != null;
            HashSet currentIds = new HashSet();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl) {
                    currentIds.add(pearl.getId());
                    if (!this.seenPearlIds.contains(pearl.getId())) {
                        this.seenPearlIds.add(pearl.getId());
                        if (pearl.getOwner() instanceof PlayerEntity thrower
                            && !thrower.equals(mc.player)
                            && !Prestige.friendManager.isFriend(thrower.getName().getString())) {
                            if (this.targetOnly.getValue()) {
                                PlayerEntity target = Prestige.targetTracker.getTarget();
                                if (target == null || !thrower.getUuid().equals(target.getUuid())) {
                                    continue;
                                }
                            } else if (mc.player.distanceTo(thrower) > this.throwerRange.getValue()) {
                                continue;
                            }

                            Vec3d landing = this.predictLanding(pearl);
                            if (landing != null) {
                                double landingDistance = mc.player.getEyePos().distanceTo(landing);
                                if (!(landingDistance < this.minLandingDistance.getValue().floatValue()) && !(landingDistance > this.landingRange.getValue().floatValue())) {
                                    this.pendingLanding = landing;
                                    this.throwTimer.reset();
                                    this.delay.reroll();
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            this.seenPearlIds.retainAll(currentIds);
        }
    }

    @Override
    public Rotation provideRotation() {
        if (this.pendingLanding == null) {
            return null;
        } else if (mc.player == null || mc.world == null) {
            return null;
        } else if (mc.currentScreen != null || !mc.isWindowFocused()) {
            return null;
        } else if (this.onlyOnGround.getValue() && !mc.player.isOnGround()) {
            return null;
        } else if (!this.throwTimer.hasElapsedRandom(this.delay)) {
            return null;
        } else if (InventoryUtil.findHotbarSlot(Items.ENDER_PEARL) == null) {
            this.pendingLanding = null;
            return null;
        } else {
            Rotation throwRotation = this.findThrowRotation(mc.player.getEyePos(), this.pendingLanding);
            if (throwRotation == null) {
                this.pendingLanding = null;
                return null;
            } else {
                float yaw = throwRotation.getYaw();
                float pitch = throwRotation.getPitch();
                Integer pearlSlot = InventoryUtil.findHotbarSlot(Items.ENDER_PEARL);
                if (pearlSlot == null) {
                    this.pendingLanding = null;
                    return null;
                } else {
                    int selectedSlot = InventoryUtil.getSelectedSlot();
                    boolean swapped = selectedSlot != pearlSlot;
                    if (swapped) {
                        InventoryUtil.selectSlot(pearlSlot);
                    }

                    float oldYaw = mc.player.getYaw();
                    float oldPitch = mc.player.getPitch();
                    mc.player.setYaw(yaw);
                    mc.player.setPitch(pitch);
                    PlayerInteractions.interactItem(Hand.MAIN_HAND);
                    mc.player.setYaw(oldYaw);
                    mc.player.setPitch(oldPitch);
                    if (swapped && this.previousSlot == -1) {
                        this.previousSlot = selectedSlot;
                    }

                    if (this.previousSlot != -1) {
                        this.swapBackTimer.reset();
                        this.swapBackDelay.reroll();
                    }

                    this.pendingLanding = null;
                    return new Rotation(yaw, pitch);
                }
            }
        }
    }

    private Vec3d predictLanding(EnderPearlEntity pearl) {
        PearlFollowSimulatedPearl simulated = new PearlFollowSimulatedPearl(mc.world, mc.player, Items.ENDER_PEARL.getDefaultStack());
        simulated.setPosition(pearl.getX(), pearl.getY(), pearl.getZ());
        simulated.setVelocity(pearl.getVelocity());
        int ticks = 0;

        while (!simulated.landed && ticks++ < 400) {
            simulated.tick();
        }

        return simulated.landed ? new Vec3d(simulated.getX(), simulated.getY(), simulated.getZ()) : null;
    }

    private Rotation findThrowRotation(Vec3d origin, Vec3d target) {
        float yaw = (float)Math.toDegrees(Math.atan2(-(target.x - origin.x), target.z - origin.z));
        ArrayList sweep = new ArrayList();
        ArrayList candidates = new ArrayList();

        for (float pitch = -85.0F; pitch <= 85.0F; pitch += 2.0F) {
            PearlThrowOption option = this.evaluateThrow(origin, target, yaw, pitch);
            sweep.add(option);
            if (option != null) {
                candidates.add(option);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        } else {
            double errorLimit = this.maxError.getValue().floatValue();
            List minima = this.selectBestMinima(sweep);
            PearlThrowOption fastest = this.pickFastestOption(candidates, errorLimit);

            for (PearlThrowOption minimum : (Iterable<PearlThrowOption>)(minima)) {
                this.refineAroundPitch(origin, target, yaw, minimum, candidates);
            }

            if (fastest != null && !this.containsPitch(minima, fastest)) {
                this.refineAroundPitch(origin, target, yaw, fastest, candidates);
            }

            PearlThrowOption best = this.pickBestOption(candidates, errorLimit);
            return best == null ? null : new Rotation(yaw, best.pitch());
        }
    }

    private List selectBestMinima(List sweep) {
        ArrayList minima = new ArrayList();

        for (int index = 0; index < sweep.size(); index++) {
            PearlThrowOption option = (PearlThrowOption)sweep.get(index);
            if (option != null) {
                double prevDistance = index > 0 && sweep.get(index - 1) != null ? ((PearlThrowOption)sweep.get(index - 1)).distanceToTarget() : Double.POSITIVE_INFINITY;
                double nextDistance = index + 1 < sweep.size() && sweep.get(index + 1) != null ? ((PearlThrowOption)sweep.get(index + 1)).distanceToTarget() : Double.POSITIVE_INFINITY;
                if (option.distanceToTarget() <= prevDistance && option.distanceToTarget() <= nextDistance) {
                    minima.add(option);
                }
            }
        }

        ArrayList selected = new ArrayList();

        while (selected.size() < 3) {
            PearlThrowOption best = null;

            for (PearlThrowOption candidate : (Iterable<PearlThrowOption>)(minima)) {
                if (!this.hasNearbyPitch(selected, candidate) && (best == null || candidate.distanceToTarget() < best.distanceToTarget())) {
                    best = candidate;
                }
            }

            if (best == null) {
                break;
            }

            selected.add(best);
        }

        return selected;
    }

    private void refineAroundPitch(Vec3d origin, Vec3d target, float yaw, PearlThrowOption seed, List candidates) {
        int steps = (int)Math.ceil(11.0);

        for (int step = -steps; step <= steps; step++) {
            float pitch = seed.pitch() + step * 0.2F;
            if (!(pitch < -85.0F) && !(pitch > 85.0F)) {
                PearlThrowOption option = this.evaluateThrow(origin, target, yaw, pitch);
                if (option != null) {
                    candidates.add(option);
                }
            }
        }
    }

    private PearlThrowOption evaluateThrow(Vec3d origin, Vec3d target, float yaw, float pitch) {
        PearlFollowLanding landing = this.simulateThrow(origin, yaw, pitch);
        return landing == null ? null : new PearlThrowOption(pitch, landing.landingPos().distanceTo(target), landing.flightTicks(), landing.peakY());
    }

    private PearlThrowOption pickBestOption(List candidates, double errorLimit) {
        double minDistance = Double.POSITIVE_INFINITY;

        for (PearlThrowOption option : (Iterable<PearlThrowOption>)(candidates)) {
            if (option.distanceToTarget() <= errorLimit) {
                minDistance = Math.min(minDistance, option.distanceToTarget());
            }
        }

        if (!Double.isFinite(minDistance)) {
            return null;
        } else {
            double threshold = Math.min(errorLimit, minDistance + 0.25);
            PearlThrowOption best = null;

            for (PearlThrowOption candidate : (Iterable<PearlThrowOption>)(candidates)) {
                if (candidate.distanceToTarget() <= threshold) {
                    best = this.choosePreferred(best, candidate);
                }
            }

            return best;
        }
    }

    private PearlThrowOption pickFastestOption(List candidates, double errorLimit) {
        PearlThrowOption best = null;

        for (PearlThrowOption option : (Iterable<PearlThrowOption>)(candidates)) {
            if (option.distanceToTarget() <= errorLimit) {
                best = this.choosePreferred(best, option);
            }
        }

        return best;
    }

    private PearlThrowOption choosePreferred(PearlThrowOption current, PearlThrowOption challenger) {
        if (current == null) {
            return challenger;
        } else {
            int tickCompare = Integer.compare(challenger.flightTicks(), current.flightTicks());
            if (tickCompare != 0) {
                return tickCompare < 0 ? challenger : current;
            } else {
                int peakCompare = Double.compare(challenger.peakY(), current.peakY());
                if (peakCompare != 0) {
                    return peakCompare < 0 ? challenger : current;
                } else {
                    int distanceCompare = Double.compare(challenger.distanceToTarget(), current.distanceToTarget());
                    if (distanceCompare != 0) {
                        return distanceCompare < 0 ? challenger : current;
                    } else {
                        return challenger.pitch() > current.pitch() ? challenger : current;
                    }
                }
            }
        }
    }

    private boolean hasNearbyPitch(List selected, PearlThrowOption option) {
        for (PearlThrowOption existing : (Iterable<PearlThrowOption>)(selected)) {
            if (this.arePitchesClose(existing, option)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsPitch(List options, PearlThrowOption option) {
        for (PearlThrowOption existing : (Iterable<PearlThrowOption>)(options)) {
            if (Math.abs(existing.pitch() - option.pitch()) < 0.1F) {
                return true;
            }
        }

        return false;
    }

    private boolean arePitchesClose(PearlThrowOption first, PearlThrowOption second) {
        return first != null && second != null && Math.abs(first.pitch() - second.pitch()) < 6.0F;
    }

    private PearlFollowLanding simulateThrow(Vec3d origin, float yaw, float pitch) {
        float yawRad = yaw * (float) (Math.PI / 180.0);
        float pitchRad = pitch * (float) (Math.PI / 180.0);
        double dirX = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double dirY = -MathHelper.sin(pitchRad);
        double dirZ = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d velocity = new Vec3d(dirX, dirY, dirZ).normalize().multiply(1.5);
        PearlFollowSimulatedPearl simulated = new PearlFollowSimulatedPearl(mc.world, mc.player, Items.ENDER_PEARL.getDefaultStack());
        simulated.setPosition(origin.x, origin.y, origin.z);
        simulated.setVelocity(velocity);
        int ticks = 0;

        double peakY;
        for (peakY = origin.y; !simulated.landed && ticks < 400; peakY = Math.max(peakY, simulated.getY())) {
            simulated.tick();
            ticks++;
        }

        return simulated.landed ? new PearlFollowLanding(new Vec3d(simulated.getX(), simulated.getY(), simulated.getZ()), ticks, peakY) : null;
    }
}

package dev.zprestige.prestige.math;

import net.minecraft.util.math.Vec3d;

public class PearlLandingMarker {
    public Vec3d landingPos;
    public float flightSeconds;
    public float alpha;

    public PearlLandingMarker(Vec3d landing, float seconds, float opacity) {
        this.landingPos = landing;
        this.flightSeconds = seconds;
        this.alpha = opacity;
    }
}

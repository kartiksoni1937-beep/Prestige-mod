package dev.zprestige.prestige.render;

public final class PearlThrowSolution {
    public int throwTick;
    public float yaw;
    public float pitch;
    public double missDistance;
    public int interceptTick;

    public PearlThrowSolution(int startTick, float throwYaw, float throwPitch, double distance, int closestTick) {
        this.throwTick = startTick;
        this.yaw = throwYaw;
        this.pitch = throwPitch;
        this.missDistance = distance;
        this.interceptTick = closestTick;
    }
}

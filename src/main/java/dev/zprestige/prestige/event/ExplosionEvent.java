package dev.zprestige.prestige.event;

public class ExplosionEvent extends Event {
    public float power;
    public double x;
    public double y;
    public double z;

    public ExplosionEvent(float explosionPower, double posX, double posY, double posZ) {
        this.power = explosionPower;
        this.x = posX;
        this.y = posY;
        this.z = posZ;
    }
}

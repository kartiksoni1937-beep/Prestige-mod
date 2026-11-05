package dev.zprestige.prestige.render;

public final class DamageSample {
    public long timestamp;
    public float damage;

    public DamageSample(long time, float amount) {
        this.timestamp = time;
        this.damage = amount;
    }
}

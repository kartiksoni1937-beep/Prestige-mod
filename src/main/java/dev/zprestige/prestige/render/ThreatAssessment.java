package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.ThreatType;

public final class ThreatAssessment {
    public final ThreatType source;
    public final float rawDamage;
    public final float weightedDamage;

    public ThreatAssessment(ThreatType threatType, float damage, float weighted) {
        this.source = threatType;
        this.rawDamage = damage;
        this.weightedDamage = weighted;
    }

    public boolean hasThreat() {
        return this.source != null;
    }

    public boolean isLethal(float health) {
        return health - this.weightedDamage <= 0.0F;
    }

    public float getHealthAfter(float health) {
        return Math.max(0.0F, health - this.weightedDamage);
    }
}

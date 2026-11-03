package dev.zprestige.prestige.type;

public enum ThreatType {
    CRYSTAL(1.0F),
    ANCHOR(1.0F),
    OBSIDIAN(0.5F),
    MACE(1.0F);

    public final float confidence;

    private ThreatType(float weight) {
        this.confidence = weight;
    }
}

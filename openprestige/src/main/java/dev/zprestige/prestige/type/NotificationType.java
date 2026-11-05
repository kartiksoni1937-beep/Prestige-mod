package dev.zprestige.prestige.type;

import java.awt.Color;

public enum NotificationType {
    ERROR(new Color(244, 67, 54)),
    INFO(new Color(33, 150, 243)),
    WARNING(new Color(255, 152, 0)),
    SUCCESS(new Color(76, 175, 80)),
    NEUTRAL(new Color(128, 128, 128));

    public final Color color;

    private NotificationType(Color displayColor) {
        this.color = displayColor;
    }
}

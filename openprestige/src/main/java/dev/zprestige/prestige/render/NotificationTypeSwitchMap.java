package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.NotificationType;

// $VF: synthetic class
public class NotificationTypeSwitchMap {
    public static final int[] SWITCH_MAP = new int[NotificationType.values().length];

    static {
        try {
            SWITCH_MAP[NotificationType.SUCCESS.ordinal()] = 1;
        } catch (NoSuchFieldError missingSuccess) {
        }

        try {
            SWITCH_MAP[NotificationType.ERROR.ordinal()] = 2;
        } catch (NoSuchFieldError missingError) {
        }

        try {
            SWITCH_MAP[NotificationType.WARNING.ordinal()] = 3;
        } catch (NoSuchFieldError missingWarning) {
        }

        try {
            SWITCH_MAP[NotificationType.INFO.ordinal()] = 4;
        } catch (NoSuchFieldError missingInfo) {
        }
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.NotificationType;

public final class ModuleToast {
    public final String title;
    public final String message;
    public final NotificationType type;
    public final long createdAt;
    public float animation;
    public boolean dismissed;

    public ModuleToast(String moduleName, String text, NotificationType notificationType) {
        this.title = moduleName;
        this.message = text;
        this.type = notificationType;
        this.createdAt = System.currentTimeMillis();
    }
}

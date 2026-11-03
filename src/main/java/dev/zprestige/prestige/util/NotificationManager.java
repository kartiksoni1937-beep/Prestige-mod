package dev.zprestige.prestige.util;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.Notification;
import dev.zprestige.prestige.type.NotificationType;
import java.util.ArrayList;
import java.util.Iterator;
import org.joml.Matrix4f;

public class NotificationManager implements MinecraftAccess {
    private final ArrayList notifications;
    public static float animationDelta = 0.0F;

    public NotificationManager() {
        super();
        this.notifications = new ArrayList();
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        animationDelta = (float)(System.currentTimeMillis() - 0L) * 0.005F;
        System.currentTimeMillis();
        if (!this.notifications.isEmpty()) {
            float scaleFactor = mc.getWindow().getScaleFactor();
            float scale = 2.0F / scaleFactor;
            Matrix4f matrix = new Matrix4f().scale(scale, scale, 1.0F);
            float screenWidth = mc.getWindow().getFramebufferWidth() / 2.0F;
            float screenHeight = mc.getWindow().getFramebufferHeight() / 2.0F;
            float width = 150.0F;
            float height = 30.0F;
            float x = screenWidth - width - 10.0F;
            float y = screenHeight - 50.0F;
            Iterator iterator = this.notifications.iterator();

            while (iterator.hasNext()) {
                Notification notification = (Notification)iterator.next();
                if (notification.finished) {
                    iterator.remove();
                } else {
                    boolean visible = y > screenHeight / 4.0F;
                    y -= notification.render(matrix, event.renderer, event.context, x, y, width, height, visible);
                }
            }
        }
    }

    public void addNotification(String title, String message, long duration, NotificationType type) {
        for (Notification existing : (Iterable<Notification>)(new ArrayList(this.notifications))) {
            if (existing.title.equals(title) && existing.message.equals(message)) {
                existing.dismissing = true;
                existing.appearing = false;
            }
        }

        this.notifications.add(new Notification(title, message, duration, type));
    }

    public boolean hasNotifications() {
        return !this.notifications.isEmpty();
    }
}

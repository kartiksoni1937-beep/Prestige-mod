package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.type.NotificationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

public class MiddleClickFriend extends Module {
    public MiddleClickFriend() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private boolean middleClickHandled = false;

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.currentScreen == null) {
            if (!mc.mouse.wasMiddleButtonClicked()) {
                this.middleClickHandled = false;
            }

            if (!this.middleClickHandled) {
                if (mc.mouse.wasMiddleButtonClicked() && mc.crosshairTarget instanceof EntityHitResult entityHit) {
                    if (entityHit.getEntity() instanceof PlayerEntity) {
                        String playerName = ((PlayerEntity)entityHit.getEntity()).getName().getString();
                        if (Prestige.friendManager.isFriend(playerName)) {
                            Prestige.friendManager.removeFriend(playerName);
                            ModuleNotificationsElement.postNotification(this, "Removed friend: " + playerName, NotificationType.INFO);
                        } else {
                            Prestige.friendManager.addFriend(playerName);
                            ModuleNotificationsElement.postNotification(this, "Added friend: " + playerName, NotificationType.INFO);
                        }
                    }

                    this.middleClickHandled = true;
                }
            }
        }
    }
}

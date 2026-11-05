package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;

public class Teams extends Module {
    public Teams() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private static volatile Set teammates = new HashSet();

    public static boolean isTeammate(String playerName) {
        for (PlayerEntity teammate : (Iterable<PlayerEntity>)(teammates)) {
            if (teammate.getName().getString().equals(playerName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onEnable() {
        teammates = new HashSet();
    }

    @Override
    public void onDisable() {
        teammates = new HashSet();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world == null || mc.player == null) {
            teammates = new HashSet();
            return;
        }

        HashSet currentTeammates = new HashSet();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && this.sharesTeamColor(player)) {
                currentTeammates.add(player);
            }
        }

        teammates = currentTeammates;
    }

    private boolean sharesTeamColor(PlayerEntity player) {
        boolean[] sameColor = new boolean[]{false};
        player.getDisplayName().asOrderedText().accept((index, style, codePoint) -> {
            mc.player.getDisplayName().asOrderedText().accept((selfIndex, selfStyle, selfCodePoint) -> {
                if (selfStyle != null && selfStyle.getColor() != null && style.getColor() != null && style.getColor().equals(selfStyle.getColor())) {
                    sameColor[0] = true;
                }

                return false;
            });
            return false;
        });
        return sameColor[0];
    }
}

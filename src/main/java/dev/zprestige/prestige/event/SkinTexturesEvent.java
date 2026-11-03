package dev.zprestige.prestige.event;

import net.minecraft.entity.player.SkinTextures;

public class SkinTexturesEvent extends Event {
    private SkinTextures skinTextures;
    private final String playerName;

    public SkinTexturesEvent(SkinTextures textures, String name) {
        this.skinTextures = textures;
        this.playerName = name;
    }

    public SkinTextures getSkinTextures() {
        return this.skinTextures;
    }

    public void setSkinTextures(SkinTextures newTextures) {
        this.skinTextures = newTextures;
    }

    public String getPlayerName() {
        return this.playerName;
    }
}

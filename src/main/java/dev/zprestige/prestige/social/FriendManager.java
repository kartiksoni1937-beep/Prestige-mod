package dev.zprestige.prestige.social;

import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.module.misc.Teams;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class FriendManager {
    private final Set friends = new LinkedHashSet();
    private final Set enemies = new LinkedHashSet();

    @Chainable
    public void addFriend(String name) {
        this.friends.add(name);
    }

    @Chainable
    public void removeFriend(String name) {
        this.friends.remove(name);
    }

    public boolean isFriend(String name) {
        return Teams.isTeammate(name) ? true : this.friends.contains(name);
    }

    @Chainable
    public List getFriends() {
        return new ArrayList(this.friends);
    }

    public List getEnemies() {
        return new ArrayList(this.enemies);
    }

    @Chainable
    public List getNonFriendPlayerNames() {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) {
            return new ArrayList();
        } else {
            ArrayList names = new ArrayList();

            for (PlayerListEntry entry : Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).getPlayerList()) {
                if (entry.getProfile() != null
                    && PositionUtil.getProfileName(entry.getProfile()) != null
                    && MinecraftClient.getInstance().player != null
                    && MinecraftClient.getInstance().player.getUuid() != null
                    && !entry.getProfile().id().equals(MinecraftClient.getInstance().player.getUuid())) {
                    String playerName = PositionUtil.getProfileName(entry.getProfile());
                    if (!this.isFriend(playerName)) {
                        names.add(playerName);
                    }
                }
            }

            return names;
        }
    }
}

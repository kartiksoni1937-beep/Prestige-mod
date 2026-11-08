package dev.zprestige.prestige.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.module.misc.Friends;
import dev.zprestige.prestige.module.misc.Teams;
import dev.zprestige.prestige.ui.SocialsPage;
import dev.zprestige.prestige.world.PositionUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Central friends registry.
 *
 * <p>Friends are keyed by UUID so a name change keeps the friendship, and the last known username is stored alongside
 * it only for display. Names that could not be resolved to a UUID (a legacy entry, or someone added while their UUID
 * was unavailable) are kept separately and migrate to the UUID form as soon as that player is seen online.
 *
 * <p>Every friend-related decision in the client goes through this class: {@link #hasFriend(String)} is identity,
 * {@link #isFriend(String)} is identity plus the Friends module's "Friend Protect" switch, and the small static
 * accessors at the bottom expose the module's remaining switches so no other class has to reach into the module.
 *
 * <p>The class never prints: the client is silent by design.
 */
public class FriendManager {
    /** uuid -> last known username. */
    private final Map<UUID, String> friends = new LinkedHashMap<>();
    /** Friends only known by name; promoted to {@link #friends} once their UUID is seen. */
    private final Set<String> unresolved = new LinkedHashSet<>();
    private final Set enemies = new LinkedHashSet<>();
    /** Online players (everyone, friends included), uuid -> name, refreshed once per client tick. */
    private final Map<UUID, String> online = new LinkedHashMap<>();

    private boolean loaded;
    private boolean bound;
    private String uiSignature = "";

    // ------------------------------------------------------------------ identity

    @Chainable
    public boolean hasFriend(UUID uuid) {
        this.load();
        return uuid != null && this.friends.containsKey(uuid);
    }

    /**
     * Pure identity check: friends by UUID or stored name, plus anyone on the player's own team. This is what the
     * name tags and the add/remove UI must use, because it does not depend on "Friend Protect".
     */
    public boolean hasFriend(String name) {
        if (name == null) {
            return false;
        }
        this.load();
        if (Teams.isTeammate(name)) {
            return true;
        }
        for (String stored : this.friends.values()) {
            if (stored.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return this.unresolved.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Identity check used by every targeting/attack path: friends are only ignored while protection is active. */
    public boolean isFriend(UUID uuid) {
        return protectActive() && this.hasFriend(uuid);
    }

    public boolean isFriend(String name) {
        return protectActive() && this.hasFriend(name);
    }

    // ------------------------------------------------------------------ mutation

    public void addFriend(UUID uuid, String username) {
        this.load();
        if (uuid == null) {
            this.addFriendByName(username);
            return;
        }
        String name = username == null ? "" : username;
        this.friends.put(uuid, name);
        this.unresolved.remove(name.toLowerCase(Locale.ROOT));
        this.save();
    }

    /** Adds by name, using the UUID of that player if they are online right now. */
    @Chainable
    public void addFriend(String name) {
        this.load();
        if (name == null || name.isBlank()) {
            return;
        }
        UUID uuid = this.uuidForName(name);
        if (uuid != null) {
            this.friends.put(uuid, name);
            this.unresolved.remove(name.toLowerCase(Locale.ROOT));
        } else {
            this.unresolved.add(name.toLowerCase(Locale.ROOT));
        }
        this.save();
    }

    private void addFriendByName(String name) {
        if (name != null && !name.isBlank()) {
            this.unresolved.add(name.toLowerCase(Locale.ROOT));
            this.save();
        }
    }

    public void removeFriend(UUID uuid) {
        this.load();
        if (uuid != null && this.friends.remove(uuid) != null) {
            this.save();
        }
    }

    @Chainable
    public void removeFriend(String name) {
        this.load();
        if (name == null) {
            return;
        }
        UUID uuid = this.uuidForName(name);
        boolean removed = uuid != null && this.friends.remove(uuid) != null;
        removed |= this.unresolved.remove(name.toLowerCase(Locale.ROOT));
        for (Map.Entry<UUID, String> entry : new ArrayList<>(this.friends.entrySet())) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                this.friends.remove(entry.getKey());
                removed = true;
            }
        }
        if (removed) {
            this.save();
        }
    }

    public void toggle(String name) {
        if (this.hasFriend(name)) {
            this.removeFriend(name);
        } else {
            this.addFriend(name);
        }
    }

    private UUID uuidForName(String name) {
        for (Map.Entry<UUID, String> entry : this.online.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        for (Map.Entry<UUID, String> entry : this.friends.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ lists

    @Chainable
    public List<String> getFriends() {
        this.load();
        return new ArrayList<>(this.friends.values());
    }

    public List<UUID> getFriendIds() {
        this.load();
        return new ArrayList<>(this.friends.keySet());
    }

    public int getFriendCount() {
        this.load();
        return this.friends.size() + this.unresolved.size();
    }

    /** Every online player except the local player, friends included. */
    public List<String> getOnlinePlayerNames() {
        this.load();
        List<String> names = new ArrayList<>(this.online.values());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Online players who are not friends (what the Socials "All Players" tab lists). */
    @Chainable
    public List<String> getOnlinePlayers() {
        List<String> names = new ArrayList<>();
        for (String name : this.getOnlinePlayerNames()) {
            if (!this.hasFriend(name)) {
                names.add(name);
            }
        }
        return names;
    }

    public int getOnlineCount() {
        return this.online.size();
    }

    @Chainable
    public List getEnemies() {
        return new ArrayList(this.enemies);
    }

    @Chainable
    public List getNonFriendPlayerNames() {
        return new ArrayList<>(this.getOnlinePlayers());
    }

    // ------------------------------------------------------------------ per-tick refresh

    /**
     * Rebuilds the online snapshot, folds in any name-only friend whose UUID is now known, keeps stored usernames
     * current after a name change, and pushes the result to the Socials page. Runs once per client tick.
     */
    public void tick() {
        this.load();
        this.bindSocials();

        MinecraftClient mc = MinecraftClient.getInstance();
        Map<UUID, String> now = new LinkedHashMap<>();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() == null || entry.getProfile().id() == null) {
                    continue;
                }
                UUID id = entry.getProfile().id();
                if (mc.player != null && id.equals(mc.player.getUuid())) {
                    continue;
                }
                String name = PositionUtil.getProfileName(entry.getProfile());
                if (name == null || name.isEmpty()) {
                    continue;
                }
                now.put(id, name);
            }
        }
        this.online.clear();
        this.online.putAll(now);

        // Promote name-only friends now that their UUID is visible, and refresh changed usernames.
        boolean dirty = false;
        if (!this.unresolved.isEmpty()) {
            for (Map.Entry<UUID, String> entry : this.online.entrySet()) {
                if (this.unresolved.remove(entry.getValue().toLowerCase(Locale.ROOT))) {
                    this.friends.put(entry.getKey(), entry.getValue());
                    dirty = true;
                }
            }
        }
        for (Map.Entry<UUID, String> entry : this.friends.entrySet()) {
            String current = this.online.get(entry.getKey());
            if (current != null && !current.equals(entry.getValue())) {
                entry.setValue(current);
                dirty = true;
            }
        }
        if (dirty) {
            this.save();
        }

        // Called every tick: the signature guard inside pushes to the page only when something really differs, which
        // also covers a friend added from the middle-click module while the page is open.
        this.pushToSocials();
    }

    private void bindSocials() {
        if (this.bound) {
            return;
        }
        this.bound = true;
        SocialsPage.onAddFriend = this::addFriend;
        SocialsPage.onRemoveFriend = this::removeFriend;
    }

    /** Mirrors the registry into the existing Socials page lists; only touches them when something changed. */
    private void pushToSocials() {
        List<String> onlineFriends = new ArrayList<>();
        List<String> offlineFriends = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : this.friends.entrySet()) {
            if (this.online.containsKey(entry.getKey())) {
                onlineFriends.add(entry.getValue());
            } else {
                offlineFriends.add(entry.getValue());
            }
        }
        for (String name : this.unresolved) {
            if (this.online.containsValue(name)) {
                onlineFriends.add(name);
            } else {
                offlineFriends.add(name);
            }
        }
        Collections.sort(onlineFriends, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(offlineFriends, String.CASE_INSENSITIVE_ORDER);

        List<String> others = this.getOnlinePlayers();
        SocialsPage.onlineCount = this.online.size();
        SocialsPage.friendCount = onlineFriends.size() + offlineFriends.size();
        String signature = onlineFriends + "|" + offlineFriends + "|" + others;
        if (signature.equals(this.uiSignature)) {
            return;
        }
        this.uiSignature = signature;

        List<String> friendView = new ArrayList<>(onlineFriends);
        friendView.addAll(offlineFriends);
        SocialsPage.friends.clear();
        SocialsPage.friends.addAll(friendView);
        SocialsPage.players.clear();
        SocialsPage.players.addAll(others);
        // The page caches its search-filtered view, so tell it the names changed.
        SocialsPage.dirty = true;
    }

    // ------------------------------------------------------------------ module switches

    /** Cached once: modules are created at startup and never replaced, and these switches are read per entity. */
    private static volatile Friends moduleCache;

    /** The running Friends module, or null before the module manager exists. */
    private static Friends friendsModule() {
        Friends cached = moduleCache;
        if (cached != null) {
            return cached;
        }
        if (Prestige.moduleManager == null) {
            return null;
        }
        for (Object module : Prestige.moduleManager.getModules()) {
            if (module instanceof Friends friends) {
                moduleCache = friends;
                return friends;
            }
        }
        return null;
    }

    /**
     * True while friends must be excluded from targeting. Stays on when the module is missing or switched off, which
     * is exactly how the client behaved before the module had settings, so nothing changes for existing users.
     */
    public static boolean protectActive() {
        Friends module = friendsModule();
        return module == null || !module.isEnabled() || module.isFriendsProtected();
    }

    public static boolean greenTags() {
        Friends module = friendsModule();
        return module != null && module.isEnabled() && module.isGreenTags();
    }

    public static boolean noHitActive() {
        Friends module = friendsModule();
        return module != null && module.isEnabled() && module.isNoHit();
    }

    public static boolean hitboxProtectionActive() {
        Friends module = friendsModule();
        return module != null && module.isEnabled() && module.isHitboxProtection();
    }

    // ------------------------------------------------------------------ persistence

    /** Test seam: the offline verifier points the store at a temporary file. Never set at runtime. */
    private static Path storeOverride;

    public static void setStoreFile(Path path) {
        storeOverride = path;
    }

    private static Path file() {
        if (storeOverride != null) {
            return storeOverride;
        }
        return FabricLoader.getInstance().getGameDir().resolve("prestige").resolve("friends.json");
    }

    private void load() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("friends") && object.get("friends").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("friends")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("id")) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(entry.get("id").getAsString());
                        String name = entry.has("name") ? entry.get("name").getAsString() : "";
                        this.friends.put(id, name);
                    } catch (IllegalArgumentException ignored) {
                        // malformed row: skip it rather than lose the rest of the file
                    }
                }
            }
            if (object.has("names") && object.get("names").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("names")) {
                    if (element.isJsonPrimitive()) {
                        this.unresolved.add(element.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {
            // A broken friends file must never stop the client from starting.
        }
    }

    public void save() {
        try {
            Path path = file();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            for (Map.Entry<UUID, String> entry : this.friends.entrySet()) {
                JsonObject row = new JsonObject();
                row.addProperty("id", entry.getKey().toString());
                row.addProperty("name", entry.getValue());
                list.add(row);
            }
            root.add("friends", list);
            JsonArray names = new JsonArray();
            for (String name : this.unresolved) {
                names.add(name);
            }
            root.add("names", names);
            Path tmp = path.resolveSibling("friends.json.tmp");
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
            // Saving is best effort; a read-only game directory must not break play.
        }
    }
}

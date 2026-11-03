package dev.zprestige.prestige.social;

import com.google.common.collect.HashMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class MojangApi {
    public static UUID fetchUuid(String username) {
        try {
            HttpURLConnection connection = (HttpURLConnection)new URL("https://api.mojang.com/users/profiles/minecraft/" + username).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) {
                return null;
            } else {
                UUID uuid;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    uuid = UUID.fromString(JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString().replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                }

                return uuid;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static GameProfile fetchProfile(UUID uuid) {
        try {
            HttpURLConnection connection = (HttpURLConnection)new URL(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString() + "?unsigned=false"
                )
                .openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) {
                return null;
            } else {
                GameProfile profile;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    HashMultimap properties = HashMultimap.create();
                    json.getAsJsonArray("properties").forEach(element -> {
                        JsonObject property = element.getAsJsonObject();
                        String propertyName = property.get("name").getAsString();
                        String propertyValue = property.get("value").getAsString();
                        String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                        properties.put(propertyName, new Property(propertyName, propertyValue, signature));
                    });
                    profile = new GameProfile(uuid, json.get("name").getAsString(), new PropertyMap(properties));
                }

                return profile;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }
}

package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.TextSetting;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.RandomUtil;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class StreamerMode extends Module {
    public StreamerMode() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting excludeFriends;
    private TextSetting prefix;
    private EnumSetting mode;
    private BoolSetting includeSelf;
    private Map aliases = new LinkedHashMap();
    private AtomicInteger aliasCounter = new AtomicInteger(0);

    @Override
    public void onEnable() {
        this.aliases.clear();
        this.aliasCounter.set(0);
    }

    @Override
    public void onDisable() {
        this.aliases.clear();
        this.aliasCounter.set(0);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                String playerName = player.getName().getString();
                if (!this.isExcluded(playerName) && playerName.length() >= 3) {
                    this.getOrCreateAlias(playerName);
                }
            }

            if (mc.getNetworkHandler() != null) {
                for (PlayerListEntry listEntry : mc.getNetworkHandler().getPlayerList()) {
                    if (listEntry != null && listEntry.getProfile() != null) {
                        String profileName = PositionUtil.getProfileName(listEntry.getProfile());
                        if (profileName != null && !this.isExcluded(profileName)) {
                            this.getOrCreateAlias(profileName);
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onLabelText(LabelTextEvent event) {
        if (event.getEntity() instanceof PlayerEntity player) {
            String playerName = player.getName().getString();
            if (!this.isExcluded(playerName)) {
                event.setText(Text.literal(this.getOrCreateAlias(playerName)));
            }
        }
    }

    @Subscribe
    public void onTextReplace(TextReplaceEvent event) {
        String originalText = event.getText();
        if (originalText != null && !originalText.isEmpty() && !this.aliases.isEmpty()) {
            String replacedText = originalText;

            for (Entry aliasEntry : (Iterable<Entry>)(this.aliases.entrySet())) {
                String realName = (String)aliasEntry.getKey();
                if (!realName.isEmpty() && replacedText.contains(realName)) {
                    replacedText = replacedText.replace(realName, (CharSequence)aliasEntry.getValue());
                }
            }

            if (!replacedText.equals(originalText)) {
                event.setTarget(originalText);
                event.setText(replacedText);
                event.cancel();
            }
        }
    }

    private boolean isExcluded(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return true;
        } else {
            return !this.includeSelf.getValue() && mc.player != null && playerName.equals(mc.player.getName().getString())
                ? true
                : this.excludeFriends.getValue() && Prestige.friendManager.isFriend(playerName);
        }
    }

    private String getOrCreateAlias(String playerName) {
        String existingAlias = (String)this.aliases.get(playerName);
        if (existingAlias != null) {
            return existingAlias;
        } else {
            String newAlias = this.generateAlias();
            this.aliases.put(playerName, newAlias);
            return newAlias;
        }
    }

    private String generateAlias() {
        String aliasPrefix = this.prefix.getValue().toString();
        if (!this.mode.getValue().equals("Random")) {
            return aliasPrefix + this.aliasCounter.incrementAndGet();
        } else {
            StringBuilder suffix = new StringBuilder();
            String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";

            for (int index = 0; index < 4; index++) {
                suffix.append(alphabet.charAt(RandomUtil.RANDOM.nextInt(alphabet.length())));
            }

            return aliasPrefix + suffix;
        }
    }
}

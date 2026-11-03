package dev.zprestige.prestige.module.misc;

import com.mojang.authlib.GameProfile;
import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.TextSetting;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.event.SkinTexturesEvent;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.social.MojangApi;
import dev.zprestige.prestige.world.PositionUtil;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

public class EnemyFaker extends Module {
    public EnemyFaker() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private TextSetting fakeName;
    private TextSetting gamemode;
    private EnumSetting tier;
    private BoolSetting update;
    private SkinTextures fakeSkin = null;
    private PlayerEntity target;

    @Subscribe
    public void onSkinTextures(SkinTexturesEvent event) {
        if (this.target != null
            && this.fakeSkin != null
            && event.getPlayerName() != null
            && event.getSkinTextures() != null
            && !this.fakeName.getValue().isEmpty()
            && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getPlayerListEntry(this.target.getUuid()) != null
            && mc.getNetworkHandler().getPlayerListEntry(this.target.getUuid()).getProfile() != null) {
            if (event.getPlayerName()
                .equals(PositionUtil.getProfileName(mc.getNetworkHandler().getPlayerListEntry(this.target.getUuid()).getProfile()))) {
                event.setSkinTextures(this.fakeSkin);
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onLabelText(LabelTextEvent event) {
        if (this.target != null) {
            if (event.getEntity().equals(this.target)) {
                MutableText iconText = Text.literal(String.valueOf(this.getGamemodeIcon().getLeft()));
                MutableText tierText = Text.literal(this.tier.getValue().toString())
                    .styled(style -> style.withColor(getTierColor(this.tier.getValue().toString())));
                MutableText separatorText = Text.literal(" | ").styled(style -> style.withColor(Formatting.GRAY));
                MutableText nameText = Text.literal(this.fakeName.getValue().toString()).styled(style -> style.withFormatting(Formatting.WHITE));
                if (this.fakeName.getValue().isEmpty()) {
                    nameText = Text.literal(event.getEntity().getName().getString()).styled(style -> style.withFormatting(Formatting.WHITE));
                }

                event.setText((this.tier.getValue().equals("None") ? Text.empty() : iconText.append(tierText).append(separatorText)).append(nameText));
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onTextReplace(TextReplaceEvent event) {
        if (this.target != null && !this.fakeName.getValue().isEmpty()) {
            if (event.getText().contains(this.target.getName().getString())) {
                event.setTarget(this.target.getName().getString());
                event.setText(this.fakeName.getValue().toString());
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world == null || mc.player == null) {
            this.target = null;
            return;
        }

        for (PlayerEntity player : new ArrayList<>(mc.world.getPlayers())) {
            if (!player.equals(mc.player) && (player.isDead() || !player.isAlive())) {
                mc.world.removeEntity(player.getId(), RemovalReason.DISCARDED);
            }
        }

        this.target = findClosestEnemy();
        if (this.update.getValue()) {
            this.update.setValue(false);
            if (!this.fakeName.getValue().isEmpty()) {
                String requestedName = this.fakeName.getValue().toString();
                // Mojang lookups block for several seconds on a bad connection, never do them on the game thread
                Thread lookup = new Thread(() -> {
                    UUID uuid = MojangApi.fetchUuid(requestedName);
                    GameProfile profile = uuid == null ? null : MojangApi.fetchProfile(uuid);
                    if (profile != null) {
                        mc.execute(() -> this.applyFakeSkin(profile, requestedName));
                    }
                }, "Prestige-SkinLookup");
                lookup.setDaemon(true);
                lookup.start();
            }
        }
    }

    private void applyFakeSkin(GameProfile profile, String requestedName) {
        if (!this.isEnabled()) {
            return;
        }

        CompletableFuture skinFuture = mc.getSkinProvider().fetchSkinTextures(profile);
        skinFuture.thenAccept(skinResult -> {
            if (((java.util.Optional)skinResult).isPresent()) {
                this.fakeSkin = (SkinTextures)((java.util.Optional)skinResult).get();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.of("Fake skin updated to: " + requestedName), false);
                }
            } else if (mc.player != null) {
                mc.player.sendMessage(Text.of("Failed to fetch skin for: " + requestedName), false);
            }
        });
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.fakeSkin = null;
    }

    public static PlayerEntity findClosestEnemy() {
        PlayerEntity closest = null;
        if (mc.world == null || mc.player == null) {
            return null;
        }

        for (PlayerEntity player : new ArrayList<>(mc.world.getPlayers())) {
            if (!player.equals(mc.player)
                && !Prestige.friendManager.isFriend(player.getName().getString())
                && player.getName().getString().length() >= 3
                && Prestige.botFilter.isValidTarget(player)
                && (closest == null || mc.player.squaredDistanceTo(player) < mc.player.squaredDistanceTo(closest))) {
                closest = player;
            }
        }

        return closest;
    }

    private static int getTierColor(String tierName) {
        return switch (tierName) {
            case "HT1" -> 15252026;
            case "LT1" -> 14005077;
            case "HT2" -> 12899303;
            case "LT2" -> 10528690;
            case "HT3" -> 16293722;
            case "LT3" -> 13007682;
            case "HT4" -> 8483994;
            case "LT4" -> 6642553;
            case "HT5" -> 9405096;
            case "LT5" -> 6642553;
            default -> 13882323;
        };
    }

    private Pair getGamemodeIcon() {
        return switch (this.gamemode.getValue().toLowerCase()) {
            case "axe" -> new Pair('\ue701', TextColor.fromFormatting(Formatting.GREEN));
            case "mace" -> new Pair('\ue702', TextColor.fromFormatting(Formatting.GRAY));
            case "nethop", "neth_pot" -> new Pair('\ue703', TextColor.fromRgb(8211008));
            case "pot" -> new Pair('\ue704', TextColor.fromRgb(16711680));
            case "smp" -> new Pair('\ue705', TextColor.fromRgb(15518533));
            case "sword" -> new Pair('\ue706', TextColor.fromRgb(10812912));
            case "uhc" -> new Pair('\ue707', TextColor.fromFormatting(Formatting.RED));
            case "vanilla" -> new Pair('\ue708', TextColor.fromFormatting(Formatting.LIGHT_PURPLE));
            case "bed" -> new Pair('\ue801', TextColor.fromRgb(16711680));
            case "bow" -> new Pair('\ue802', TextColor.fromRgb(6700304));
            case "creeper" -> new Pair('\ue803', TextColor.fromFormatting(Formatting.GREEN));
            case "debuff" -> new Pair('\ue804', TextColor.fromFormatting(Formatting.DARK_GRAY));
            case "dia_crystal" -> new Pair('\ue805', TextColor.fromFormatting(Formatting.AQUA));
            case "dia_smp" -> new Pair('\ue806', TextColor.fromRgb(9201291));
            case "elytra" -> new Pair('\ue807', TextColor.fromRgb(9276849));
            case "manhunt" -> new Pair('\ue808', TextColor.fromFormatting(Formatting.RED));
            case "minecart" -> new Pair('\ue809', TextColor.fromFormatting(Formatting.GRAY));
            case "og_vanilla" -> new Pair('\ue810', TextColor.fromFormatting(Formatting.GOLD));
            case "speed" -> new Pair('\ue811', TextColor.fromRgb(4434385));
            case "trident" -> new Pair('\ue812', TextColor.fromRgb(5741452));
            default -> new Pair('•', TextColor.fromFormatting(Formatting.WHITE));
        };
    }
}

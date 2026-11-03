package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Category;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;

public class AntiTrap extends Module {
    private MultiSetting entities;
    private FloatSetting range;
    private BoolSetting cancelSpawnPackets;
    private BoolSetting discardExisting;
    private BoolSetting antiSign;

    public AntiTrap() {
        super("AntiTrap", "Hides entities commonly used to bait trap-TPAs (armor stands, minecarts, etc).", Category.Misc.ordinal());
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent event) {
        if (this.antiSign.getValue() && event.getPacket() instanceof SignEditorOpenS2CPacket) {
            event.cancel();
        } else if (this.cancelSpawnPackets.getValue()) {
            if (event.getPacket() instanceof EntitySpawnS2CPacket spawnPacket) {
                if (mc.player != null) {
                    if (this.isTrapEntityType(spawnPacket.getEntityType())) {
                        double dx = spawnPacket.getX() - mc.player.getX();
                        double dy = spawnPacket.getY() - mc.player.getY();
                        double dz = spawnPacket.getZ() - mc.player.getZ();
                        float maxDistance = this.range.getValue();
                        if (!(dx * dx + dy * dy + dz * dz > maxDistance * maxDistance)) {
                            event.cancel();
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (this.antiSign.getValue() && mc.currentScreen instanceof AbstractSignEditScreen) {
            mc.setScreen(null);
        }

        if (this.discardExisting.getValue()) {
            if (mc.world != null && mc.player != null) {
                float maxDistance = this.range.getValue();
                double maxDistanceSq = maxDistance * maxDistance;
                double playerX = mc.player.getX();
                double playerY = mc.player.getY();
                double playerZ = mc.player.getZ();

                for (Entity entity : mc.world.getEntities()) {
                    if (this.isTrapEntity(entity)) {
                        double dx = entity.getX() - playerX;
                        double dy = entity.getY() - playerY;
                        double dz = entity.getZ() - playerZ;
                        if (!(dx * dx + dy * dy + dz * dz > maxDistanceSq)) {
                            entity.discard();
                        }
                    }
                }
            }
        }
    }

    private boolean isTrapEntity(Entity entity) {
        if (this.entities.isSelected("Armor Stands") && entity instanceof ArmorStandEntity) {
            return true;
        } else if (this.entities.isSelected("Minecarts") && entity instanceof AbstractMinecartEntity) {
            return true;
        } else if (this.entities.isSelected("Item Frames") && entity instanceof ItemFrameEntity) {
            return true;
        } else {
            return this.entities.isSelected("Boats") && entity instanceof AbstractBoatEntity
                ? true
                : this.entities.isSelected("End Crystals") && entity instanceof EndCrystalEntity;
        }
    }

    private boolean isTrapEntityType(EntityType entityType) {
        if (this.entities.isSelected("Armor Stands") && entityType == EntityType.ARMOR_STAND) {
            return true;
        } else if (this.entities.isSelected("Minecarts") && this.isMinecartType(entityType)) {
            return true;
        } else if (!this.entities.isSelected("Item Frames") || entityType != EntityType.ITEM_FRAME && entityType != EntityType.GLOW_ITEM_FRAME) {
            return this.entities.isSelected("Boats") && this.isBoatType(entityType)
                ? true
                : this.entities.isSelected("End Crystals") && entityType == EntityType.END_CRYSTAL;
        } else {
            return true;
        }
    }

    private boolean isMinecartType(EntityType entityType) {
        return entityType == EntityType.MINECART
            || entityType == EntityType.CHEST_MINECART
            || entityType == EntityType.COMMAND_BLOCK_MINECART
            || entityType == EntityType.FURNACE_MINECART
            || entityType == EntityType.HOPPER_MINECART
            || entityType == EntityType.SPAWNER_MINECART
            || entityType == EntityType.TNT_MINECART;
    }

    private boolean isBoatType(EntityType entityType) {
        return entityType == EntityType.OAK_BOAT
            || entityType == EntityType.SPRUCE_BOAT
            || entityType == EntityType.BIRCH_BOAT
            || entityType == EntityType.JUNGLE_BOAT
            || entityType == EntityType.ACACIA_BOAT
            || entityType == EntityType.DARK_OAK_BOAT
            || entityType == EntityType.MANGROVE_BOAT
            || entityType == EntityType.CHERRY_BOAT
            || entityType == EntityType.PALE_OAK_BOAT
            || entityType == EntityType.PALE_OAK_CHEST_BOAT
            || entityType == EntityType.BAMBOO_RAFT
            || entityType == EntityType.OAK_CHEST_BOAT
            || entityType == EntityType.SPRUCE_CHEST_BOAT
            || entityType == EntityType.BIRCH_CHEST_BOAT
            || entityType == EntityType.JUNGLE_CHEST_BOAT
            || entityType == EntityType.ACACIA_CHEST_BOAT
            || entityType == EntityType.DARK_OAK_CHEST_BOAT
            || entityType == EntityType.MANGROVE_CHEST_BOAT
            || entityType == EntityType.CHERRY_CHEST_BOAT
            || entityType == EntityType.BAMBOO_CHEST_RAFT;
    }
}

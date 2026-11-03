package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.EnumSetting;
import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.IntSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.EntityTypeSet;
import dev.zprestige.prestige.render.EspShaderManager;
import dev.zprestige.prestige.render.PostShader;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.RenderCallback;
import dev.zprestige.prestige.type.ShaderMode;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.entity.EntityType;

public class ESP extends Module {
    public ESP() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }
    private EnumSetting mode;
    private MultiSetting targets;
    private BoolSetting menuColor;
    private ColorSetting color;
    private IntSetting quality;
    private IntSetting radius;
    private BoolSetting fill;
    private FloatSetting opacity;
    private FloatSetting rimWidth;
    private FloatSetting lineWidth;
    private FloatSetting haloOpacity;
    private FloatSetting coreBoost;
    private BoolSetting invisible;
    private boolean[] lastSelectedTargets;
    private String lastMode;
    private boolean callbackRegistered = false;
    private Consumer uniformOverride;
    public static final ThreadLocal CURRENT_RENDER_LAYER = new ThreadLocal();

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        boolean[] selected = this.targets.getSelected();
        if (this.lastSelectedTargets == null || !Arrays.equals(selected, this.lastSelectedTargets)) {
            this.updateTargetTypes();
            this.lastSelectedTargets = Arrays.copyOf(selected, selected.length);
        }

        String currentMode = this.mode.getValue();
        if (!currentMode.equals(this.lastMode)) {
            EspShaderManager.setShaderMode(toShaderMode(currentMode));
            this.lastMode = currentMode;
        }

        EspShaderManager.renderInvisible = this.invisible.getValue();
    }

    @Override
    public void onEnable() {
        EspShaderManager.setShaderMode(toShaderMode(this.mode.getValue()));
        this.lastMode = this.mode.getValue();
        if (!this.callbackRegistered) {
            RenderEvents.AFTER_HUD_RENDER.register((RenderCallback)(buffers, context) -> {
                if (this.isEnabled()) {
                    PostShader activeShader = EspShaderManager.getActiveShader();
                    if (activeShader != null) {
                        activeShader.setUniformOverride(this.uniformOverride);
                    }
                }
            });
            this.callbackRegistered = true;
        }

        EspShaderManager.enabled = true;
        EspShaderManager.renderInvisible = this.invisible.getValue();
    }

    @Override
    public void onDisable() {
        EspShaderManager.enabled = false;
        EspShaderManager.renderInvisible = false;
        PostShader shader = EspShaderManager.getActiveShader();
        if (shader != null) {
            shader.setUniformOverride(null);
        }
    }

    private static ShaderMode toShaderMode(String modeName) {
        return switch (modeName) {
            case "Chams" -> ShaderMode.CHAMS;
            case "Aura" -> ShaderMode.AURA;
            default -> ShaderMode.NORMAL;
        };
    }

    private void updateTargetTypes() {
        EntityTypeSet types = new EntityTypeSet(new EntityType[0]);
        if (this.targets.isSelected("Players")) {
            types.add(EntityType.PLAYER);
        }

        if (this.targets.isSelected("Crystals")) {
            types.add(EntityType.END_CRYSTAL);
        }

        if (this.targets.isSelected("Items")) {
            types.add(EntityType.ITEM);
        }

        if (this.targets.isSelected("Monsters")) {
            types.add(EntityType.ZOMBIE);
            types.add(EntityType.SKELETON);
            types.add(EntityType.CREEPER);
            types.add(EntityType.SPIDER);
            types.add(EntityType.CAVE_SPIDER);
            types.add(EntityType.ZOMBIE_VILLAGER);
            types.add(EntityType.HUSK);
            types.add(EntityType.DROWNED);
            types.add(EntityType.STRAY);
            types.add(EntityType.WITCH);
            types.add(EntityType.SLIME);
            types.add(EntityType.SILVERFISH);
            types.add(EntityType.PHANTOM);
            types.add(EntityType.VEX);
            types.add(EntityType.VINDICATOR);
            types.add(EntityType.EVOKER);
            types.add(EntityType.PILLAGER);
            types.add(EntityType.RAVAGER);
        }

        if (this.targets.isSelected("Ambient")) {
            types.add(EntityType.BAT);
        }

        if (this.targets.isSelected("Friendly")) {
            types.add(EntityType.COW);
            types.add(EntityType.SHEEP);
            types.add(EntityType.PIG);
            types.add(EntityType.CHICKEN);
            types.add(EntityType.RABBIT);
            types.add(EntityType.OCELOT);
            types.add(EntityType.VILLAGER);
            types.add(EntityType.IRON_GOLEM);
            types.add(EntityType.SNOW_GOLEM);
            types.add(EntityType.MOOSHROOM);
            types.add(EntityType.GOAT);
            types.add(EntityType.PANDA);
            types.add(EntityType.ALLAY);
            types.add(EntityType.SNIFFER);
            types.add(EntityType.ARMADILLO);
        }

        if (this.targets.isSelected("Aquatic")) {
            types.add(EntityType.GUARDIAN);
            types.add(EntityType.ELDER_GUARDIAN);
            types.add(EntityType.DOLPHIN);
            types.add(EntityType.COD);
            types.add(EntityType.SALMON);
            types.add(EntityType.PUFFERFISH);
            types.add(EntityType.TROPICAL_FISH);
            types.add(EntityType.SQUID);
            types.add(EntityType.GLOW_SQUID);
            types.add(EntityType.AXOLOTL);
            types.add(EntityType.TURTLE);
            types.add(EntityType.TADPOLE);
        }

        if (this.targets.isSelected("Nether")) {
            types.add(EntityType.BLAZE);
            types.add(EntityType.GHAST);
            types.add(EntityType.MAGMA_CUBE);
            types.add(EntityType.PIGLIN);
            types.add(EntityType.PIGLIN_BRUTE);
            types.add(EntityType.HOGLIN);
            types.add(EntityType.ZOGLIN);
            types.add(EntityType.STRIDER);
            types.add(EntityType.WITHER_SKELETON);
        }

        if (this.targets.isSelected("End")) {
            types.add(EntityType.ENDERMAN);
            types.add(EntityType.ENDERMITE);
            types.add(EntityType.SHULKER);
        }

        if (this.targets.isSelected("Bosses")) {
            types.add(EntityType.WITHER);
            types.add(EntityType.ENDER_DRAGON);
            types.add(EntityType.WARDEN);
        }

        if (this.targets.isSelected("Tameable")) {
            types.add(EntityType.WOLF);
            types.add(EntityType.CAT);
            types.add(EntityType.PARROT);
            types.add(EntityType.HORSE);
            types.add(EntityType.DONKEY);
            types.add(EntityType.MULE);
            types.add(EntityType.LLAMA);
            types.add(EntityType.FOX);
            types.add(EntityType.BEE);
            types.add(EntityType.POLAR_BEAR);
            types.add(EntityType.FROG);
            types.add(EntityType.CAMEL);
        }

        if (this.targets.isSelected("Projectiles")) {
            types.add(EntityType.ARROW);
            types.add(EntityType.SPECTRAL_ARROW);
            types.add(EntityType.TRIDENT);
            types.add(EntityType.FIREBALL);
            types.add(EntityType.SMALL_FIREBALL);
            types.add(EntityType.DRAGON_FIREBALL);
            types.add(EntityType.WITHER_SKULL);
            types.add(EntityType.SNOWBALL);
            types.add(EntityType.EGG);
            types.add(EntityType.ENDER_PEARL);
            types.add(EntityType.EXPERIENCE_BOTTLE);
        }

        if (this.targets.isSelected("Vehicles")) {
            types.add(EntityType.MINECART);
            types.add(EntityType.CHEST_MINECART);
            types.add(EntityType.FURNACE_MINECART);
            types.add(EntityType.TNT_MINECART);
            types.add(EntityType.HOPPER_MINECART);
            types.add(EntityType.SPAWNER_MINECART);
        }

        if (this.targets.isSelected("Decorations")) {
            types.add(EntityType.ITEM_FRAME);
            types.add(EntityType.GLOW_ITEM_FRAME);
            types.add(EntityType.ARMOR_STAND);
            types.add(EntityType.PAINTING);
        }

        EspShaderManager.modelMatchCache.clear();
        EspShaderManager.targetTypes = types;
    }
}

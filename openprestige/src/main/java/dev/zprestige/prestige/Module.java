package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.annotation.NativeId;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.hud.ModuleNotificationsElement;
import dev.zprestige.prestige.render.MinecraftAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Module extends SettingHolder implements MinecraftAccess {
    private final List coreSettings = new ArrayList();
    private final BoolSetting enabledSetting = new BoolSetting("Enabled", false);
    private String name;
    private final KeybindSetting keybind;
    private Category category;
    private String description;
    private boolean lastEnabled;
    private volatile List allSettingsCache;

    public Module() {
        this.keybind = new KeybindSetting("Bind", -1).withDescription("Keybind for " + this.name);
        this.coreSettings.add(this.enabledSetting);
        this.coreSettings.add(this.keybind);
    }

    public Module(String moduleName, String moduleDescription, int categoryOrdinal) {
        this();
        this.name = moduleName;
        this.description = moduleDescription;

        for (Category candidate : Category.values()) {
            if (candidate.ordinal() == categoryOrdinal) {
                this.category = candidate;
                break;
            }
        }
    }

    @NativeId("")
    public void injectFromNative(Object injectedSetting, String moduleName, String moduleDescription, int categoryOrdinal) {
        if (injectedSetting != null) {
            this.settings.add((Setting)injectedSetting);
            this.allSettingsCache = null;
        } else {
            this.name = moduleName;
            this.description = moduleDescription;
            this.category = Arrays.stream(Category.values()).filter(candidate -> candidate.ordinal() == categoryOrdinal).findFirst().orElse(this.category);
        }
    }

    @Chainable
    public boolean isEnabled() {
        return this.enabledSetting.getValue();
    }

    public int getKey() {
        return this.keybind.getValue();
    }

    public KeybindSetting getKeybind() {
        return this.keybind;
    }

    public void toggle() {
        this.enabledSetting.setValue(!this.isEnabled());
        this.setLastEnabled(this.isEnabled());
        if (this.isEnabled()) {
            Prestige.eventBus.register(this);
            if (mc.world != null && mc.player != null) {
                this.onEnable();
            } else {
                Prestige.moduleManager.queueEnable(this);
            }
        } else {
            Prestige.eventBus.unregister(this);
            if (mc.world != null && mc.player != null) {
                this.onDisable();
            } else {
                Prestige.moduleManager.queueDisable(this);
            }
        }

        ModuleNotificationsElement.notifyToggle(this);
    }

    public void applyState() {
        if (this.isEnabled()) {
            Prestige.eventBus.register(this);
            if (mc.world != null && mc.player != null) {
                this.onEnable();
            } else {
                Prestige.moduleManager.queueEnable(this);
            }
        } else {
            Prestige.eventBus.unregister(this);
            if (mc.world != null && mc.player != null) {
                this.onDisable();
            } else {
                Prestige.moduleManager.queueDisable(this);
            }
        }

        ModuleNotificationsElement.notifyToggle(this);
    }

    public void dispose() {
        this.name = null;
        this.description = null;
        ArrayList allSettings = new ArrayList();
        allSettings.addAll(this.settings);
        allSettings.addAll(this.coreSettings);

        for (Setting setting : (Iterable<Setting>)(allSettings)) {
            setting.setName(null);
            setting.setDescription(null);
            setting.setValue(null);
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public boolean isPersistent() {
        return true;
    }

    public String getInfo() {
        return "";
    }

    public List getCoreSettings() {
        return this.coreSettings;
    }

    @Chainable
    public String getName() {
        return this.name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    @Chainable
    public String getDescription() {
        return this.description;
    }

    public void setDescription(String newDescription) {
        this.description = newDescription;
    }

    @Chainable
    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category newCategory) {
        this.category = newCategory;
    }

    public boolean getLastEnabled() {
        return this.lastEnabled;
    }

    public void setLastEnabled(boolean wasEnabled) {
        this.lastEnabled = wasEnabled;
    }

    @Chainable
    public List getSettings() {
        List cachedSettings = this.allSettingsCache;
        if (cachedSettings == null) {
            ArrayList combinedSettings = new ArrayList(this.coreSettings.size() + this.settings.size());
            combinedSettings.addAll(this.coreSettings);
            combinedSettings.addAll(this.settings);
            cachedSettings = List.copyOf(combinedSettings);
            this.allSettingsCache = cachedSettings;
        }

        return cachedSettings;
    }

    public Set getGamemodes() {
        return Set.of(Gamemode.Any);
    }

    @Chainable
    public int getGamemodeMask() {
        Set gamemodes = this.getGamemodes();
        int gamemodeMask = 0;

        for (Gamemode gamemode : (Iterable<Gamemode>)(gamemodes)) {
            if (gamemode != Gamemode.Any) {
                gamemodeMask |= 1 << gamemode.ordinal();
            }
        }

        return gamemodeMask;
    }
}

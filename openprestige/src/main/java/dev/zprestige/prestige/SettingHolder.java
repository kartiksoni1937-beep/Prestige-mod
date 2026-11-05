package dev.zprestige.prestige;

import java.awt.Color;
import java.util.ArrayList;

public class SettingHolder {
    public final ArrayList settings = new ArrayList();

    public KeybindSetting addKeybind(String settingName, int defaultKey) {
        KeybindSetting createdSetting = new KeybindSetting(settingName, defaultKey);
        this.settings.add(createdSetting);
        return createdSetting;
    }

    public BoolSetting addBool(String settingName, boolean defaultValue) {
        BoolSetting createdSetting = new BoolSetting(settingName, defaultValue);
        this.settings.add(createdSetting);
        return createdSetting;
    }

    public EnumSetting addEnum(String settingName, String defaultOption, String[] optionValues) {
        EnumSetting createdSetting = new EnumSetting(settingName, defaultOption, optionValues);
        this.settings.add(createdSetting);
        return createdSetting;
    }

    public ColorSetting addColor(String settingName, Color defaultColor) {
        ColorSetting createdSetting = new ColorSetting(settingName, defaultColor);
        this.settings.add(createdSetting);
        return createdSetting;
    }
}

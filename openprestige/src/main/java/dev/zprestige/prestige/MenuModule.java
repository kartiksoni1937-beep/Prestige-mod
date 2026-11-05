package dev.zprestige.prestige;

import java.awt.Color;

public class MenuModule extends Module {
    private final KeybindSetting menuKey;
    private final EnumSetting descriptionMode;
    private final BoolSetting rounded;
    private final ColorSetting menuColor;

    public MenuModule() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.menuKey = this.addKeybind("Menu Key", 161);
        this.descriptionMode = this.addEnum("Descriptions", "Mouse", new String[]{"Mouse", "Search Bar"});
        this.rounded = this.addBool("Rounded", false);
        this.menuColor = this.addColor("Menu Color", new Color(131, 118, 252));
    }

    public KeybindSetting getMenuKey() {
        return this.menuKey;
    }

    public EnumSetting getDescriptionMode() {
        return this.descriptionMode;
    }

    public BoolSetting getRounded() {
        return this.rounded;
    }

    public ColorSetting getMenuColor() {
        return this.menuColor;
    }
}

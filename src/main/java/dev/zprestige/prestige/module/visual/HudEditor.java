package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.FloatSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.gui.ClickGuiScreen;

public class HudEditor extends Module {
    private static HudEditor instance;
    private FloatSetting hudScale;

    public HudEditor() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        instance = this;
    }

    public static float getHudScalePercent() {
        HudEditor editor = instance;
        if (editor == null) {
            return 115.0F;
        } else {
            Float scale = editor.hudScale.getValue();
            return scale == null ? 115.0F : scale;
        }
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen instanceof ClickGuiScreen) {
            Prestige.clickGuiScreen.close();
        }

        mc.setScreen(Prestige.hudEditorScreen);
        this.toggle();
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}

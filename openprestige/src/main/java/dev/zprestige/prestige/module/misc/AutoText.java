package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.TextSetting;
import java.util.Objects;

public class AutoText extends Module {
    public AutoText() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private TextSetting text;

    @Override
    public void onEnable() {
        String message = this.text.getValue().toString();
        if (message.length() > 256) {
            message = message.substring(0, 256);
        }

        if (message.startsWith("/")) {
            Objects.requireNonNull(mc.getNetworkHandler()).sendChatCommand(message.substring(1));
        } else {
            Objects.requireNonNull(mc.getNetworkHandler()).sendChatMessage(message);
        }

        this.toggle();
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}

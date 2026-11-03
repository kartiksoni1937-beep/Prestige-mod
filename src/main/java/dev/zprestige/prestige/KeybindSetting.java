package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public Integer getValue() {
        return (Integer)super.getValue();
    }

    private boolean holdMode;

    public KeybindSetting(String settingName, int defaultKey) {
        super(settingName, defaultKey);
    }

    @Chainable
    public boolean isHoldMode() {
        return this.holdMode;
    }

    @Chainable
    public void setHoldMode(boolean hold) {
        this.holdMode = hold;
    }

    public KeybindSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public KeybindSetting withDescription(String newDescription) {
        super.setDescription(newDescription);
        return this;
    }

    public boolean isPressed() {
        return this.getValue() != -1 && this.getValue() > 9994
            ? GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), 9999 - this.getValue()) == 1
            : GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), this.getValue()) == 1;
    }

    @Override
    public Setting setVisibility(Predicate visibilityPredicate) {
        return this.visibleWhen(visibilityPredicate);
    }

    @Chainable
    @Override
    public Setting setDescription(String newDescription) {
        return this.withDescription(newDescription);
    }
}

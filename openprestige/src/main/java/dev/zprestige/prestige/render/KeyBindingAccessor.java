package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.FieldAccessor;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Key;

public class KeyBindingAccessor {
    private final KeyBinding keyBinding;
    private static final String BOUND_KEY_FIELD = "boundKey";

    public KeyBindingAccessor(KeyBinding binding) {
        this.keyBinding = binding;
    }

    public Key getBoundKey() {
        return (Key)FieldAccessor.bindMapped(this.keyBinding, BOUND_KEY_FIELD, KeyBinding.class, Key.class).get();
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.reflect.MethodAccessor;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;

public class MouseAccessor {
    private final Mouse mouse;
    private static final String MOUSE_BUTTON_METHOD = "onMouseButton";

    public MouseAccessor(Mouse mouseHandler) {
        this.mouse = mouseHandler;
    }

    public void invokeMouseButton(long windowHandle, int button, int action, int modifiers) {
        MethodAccessor.bindMapped(
            this.mouse, MOUSE_BUTTON_METHOD, Mouse.class, void.class, new Class[]{long.class, MouseInput.class, int.class}
        ).invoke(new Object[]{windowHandle, new MouseInput(button, modifiers), action});
    }
}

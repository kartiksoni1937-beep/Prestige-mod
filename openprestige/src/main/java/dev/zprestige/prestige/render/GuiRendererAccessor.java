package dev.zprestige.prestige.render;

public class GuiRendererAccessor {
    private final Object guiRenderer;

    public GuiRendererAccessor(Object renderer) {
        this.guiRenderer = renderer;
    }

    /** 1.21 has no GuiRenderer; the value is resolved reflectively when present. */
    public Object getProjectionMatrix() {
        try {
            java.lang.reflect.Field field = this.guiRenderer.getClass().getDeclaredField("guiProjectionMatrix");
            field.setAccessible(true);
            return field.get(this.guiRenderer);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

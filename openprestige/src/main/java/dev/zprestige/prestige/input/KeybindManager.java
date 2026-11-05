package dev.zprestige.prestige.input;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Native;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.KeyEvent;
import dev.zprestige.prestige.event.MouseButtonEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.VirtualKeyMapper;
import java.util.Map;

public class KeybindManager implements MinecraftAccess {
    private static Double pendingScaleFactor = null;
    private static final Map MOUSE_KEYCODE_TO_BUTTON = Map.of(9999, 0, 9998, 1, 9997, 2, 9996, 3, 9995, 4);
    private long lastGuiPollTime;

    public KeybindManager() {
        super();
        this.lastGuiPollTime = 0L;
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onKey(KeyEvent event) {
        if (event.getAction() == 0) {
            if (event.getKey() == VirtualKeyMapper.toGlfwKey(Prestige.moduleManager.getMenu().getMenuKey().getValue())) {
                mc.setScreen(Prestige.clickGuiScreen);
                Prestige.clickGuiScreen.resetClosedState();
            }
        }

        for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            try {
                int moduleKey = VirtualKeyMapper.toGlfwKey(module.getKey());
                if (module.getKeybind().isHoldMode()) {
                    if (module.isEnabled() && mc.currentScreen != null) {
                        module.toggle();
                    } else if (event.getAction() == 0) {
                        if (module.isEnabled() && moduleKey == event.getKey()) {
                            module.toggle();
                        }
                    } else if (event.getAction() == 1 && mc.currentScreen == null && !module.isEnabled() && moduleKey == event.getKey()) {
                        module.toggle();
                    }
                } else if (mc.currentScreen == null
                    && module.getKey() != -1
                    && event.getAction() == 1
                    && moduleKey == event.getKey()) {
                    module.toggle();
                }
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.currentScreen == null) {
            long now = System.currentTimeMillis();
            if (now - this.lastGuiPollTime >= 30L) {
                this.lastGuiPollTime = now;
                if (Native.isMenuOpen()) {
                    mc.setScreen(Prestige.clickGuiScreen);
                    Prestige.clickGuiScreen.resetClosedState();
                }
            }

            if (pendingScaleFactor != null) {
                mc.getWindow().setScaleFactor((int)Math.round(pendingScaleFactor));
                pendingScaleFactor = null;
            }
        }
    }

    @Subscribe
    public void onMouseButton(MouseButtonEvent event) {
        if (Prestige.simulatedClickManager != null && !Prestige.simulatedClickManager.isSimulated(event.getButton())) {
            for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
                try {
                    if (module.getKey() != -1
                        && MOUSE_KEYCODE_TO_BUTTON.containsKey(module.getKey())
                        && (Integer)MOUSE_KEYCODE_TO_BUTTON.get(module.getKey()) == event.getButton()) {
                        if (module.getKeybind().isHoldMode()) {
                            if (event.getAction() == 1 && mc.currentScreen == null && !module.isEnabled()) {
                                module.toggle();
                            } else if (event.getAction() == 0 && module.isEnabled()) {
                                module.toggle();
                            }
                        } else if (mc.currentScreen == null && event.getAction() == 1) {
                            module.toggle();
                        }
                    }
                } catch (Throwable error) {
                    error.printStackTrace();
                }
            }
        }
    }
}

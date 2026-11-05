package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.inject.Bootstrap;
import dev.zprestige.prestige.inject.NativeBridge;
import dev.zprestige.prestige.ui.mc.Overlay;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes the client from the running game.
 *
 * Stage 1 (this tick): stop all client logic, close the menu, switch every other module off.
 * Stage 2 (next tick, after Module.toggle has returned): dispose the modules and empty the module list.
 * Stage 3 (injected clients only): put the original bytes back into every hooked game class and let the native loader unload.
 */
public class SelfDestruct extends Module {
    public SelfDestruct() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    @Override
    public void onEnable() {
        Prestige.selfDestructed = true;
        Overlay.shutdown();
        mc.setScreen(null);

        List<Module> modules = new ArrayList<>(Prestige.moduleManager.getModules());
        for (Module module : modules) {
            if (module == this) {
                continue;
            }
            try {
                if (module.isEnabled()) {
                    module.toggle();
                }
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }

        // Disposing this module now would null its own "Enabled" value while Module.toggle still has to read it.
        mc.execute(() -> dispose(modules));
    }

    private void dispose(List<Module> modules) {
        for (Module module : modules) {
            try {
                module.dispose();
                module.getKeybind().setValue(-1);
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
        Prestige.moduleManager.setModules(new ArrayList<>());

        if (Bootstrap.isInjected()) {
            // one more tick so no hook of the current tick is still being entered when the original classes come back
            mc.execute(() -> mc.execute(SelfDestruct::unhook));
        }
    }

    private static void unhook() {
        try {
            Bootstrap.uninstall();
            NativeBridge.release();
        } catch (UnsatisfiedLinkError ignored) {
            // loader already gone
        } catch (Throwable error) {
            // an Error escaping a main-thread task would take the whole game down
            error.printStackTrace();
        }
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}

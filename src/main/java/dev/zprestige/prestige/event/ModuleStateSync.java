package dev.zprestige.prestige.event;

import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Native;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.RenderCallback;

public class ModuleStateSync {
    private boolean hudDrawn;

    public ModuleStateSync() {
        super();
        this.hudDrawn = false;
        Prestige.eventBus.register(this);
        RenderEvents.HUD_RENDER.register((RenderCallback)(renderer, context) -> {
            try {
                this.hudDrawn = Prestige.hudEditorScreen.renderHud(renderer, context);
            } catch (Throwable error) {
                this.hudDrawn = false;
                Prestige.logRenderError(error);
            }
        });
    }

    @Subscribe
    public void onPreTick(PreTickEvent event) {
        Native.onClientTick();

        for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
            boolean enabled = module.isEnabled();
            try {
                if (enabled && !module.getLastEnabled()) {
                    module.applyState();
                }

                if (!enabled && module.getLastEnabled()) {
                    module.applyState();
                }
            } catch (Throwable error) {
                error.printStackTrace();
            }

            module.setLastEnabled(enabled);
        }
    }

    @Subscribe
    public void onRenderStatusEffects(RenderStatusEffectsEvent event) {
        if (this.hudDrawn) {
            event.cancel();
        }
    }
}

package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ModuleManager implements MinecraftAccess {
    private final MenuModule menu;
    private List modules;
    private final ConcurrentLinkedQueue enableQueue;
    private final ConcurrentLinkedQueue disableQueue;
    public static boolean headTickFallback = true;

    public ModuleManager() {
        super();
        this.menu = new MenuModule();
        this.modules = new ArrayList();
        this.enableQueue = new ConcurrentLinkedQueue();
        this.disableQueue = new ConcurrentLinkedQueue();
        Prestige.eventBus.register(this);
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            while (!this.enableQueue.isEmpty()) {
                Module pendingEnable = (Module)this.enableQueue.poll();
                if (pendingEnable != null && pendingEnable.isEnabled()) {
                    pendingEnable.onEnable();
                }
            }

            while (!this.disableQueue.isEmpty()) {
                Module pendingDisable = (Module)this.disableQueue.poll();
                if (pendingDisable != null && !pendingDisable.isEnabled()) {
                    pendingDisable.onDisable();
                }
            }
        }
    }

    public void queueEnable(Module module) {
        if (!this.enableQueue.contains(module)) {
            this.enableQueue.offer(module);
        }
    }

    public void queueDisable(Module module) {
        if (!this.disableQueue.contains(module)) {
            this.disableQueue.offer(module);
        }
    }

    @Chainable
    public List getModules() {
        return this.modules;
    }

    @Chainable
    public void setModules(List moduleList) {
        this.modules = moduleList;
    }

    public MenuModule getMenu() {
        return this.menu;
    }

    @Chainable
    public void setMenuColor(Color color) {
        this.menu.getMenuColor().setValue(color);
    }

    @Chainable
    public void setMenuKey(int key) {
        this.menu.getMenuKey().setValue(key);
    }
}

package dev.zprestige.prestige.world;

import dev.zprestige.prestige.module.combat.CobwebPlacer;

public class CobwebSwitchBackTask implements Runnable {
    public CobwebPlacer placer;

    public CobwebSwitchBackTask(CobwebPlacer cobwebPlacer) {
        this.placer = cobwebPlacer;
    }

    @Override
    public void run() {
        this.placer.switchBackScheduled = false;
        if (this.placer.switchBackPending) {
            this.placer.processSwitchBack();
            if (this.placer.switchBackPending) {
                this.placer.scheduleSwitchBack();
            }
        }
    }
}

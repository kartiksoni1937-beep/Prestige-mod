package dev.zprestige.prestige.ui;

import dev.zprestige.prestige.ui.bridge.Bridge;
import java.util.List;

/** Dev/test helpers for the Overlay command file (only reachable with -Dprestige.ui.cmd=...). */
public final class DevCmd {
    private DevCmd() {}

    /** Switches to the module's category page and returns the canvas centre of its toggle switch, or null. */
    public static float[] toggleCenter(String name) {
        for (ModulesPage.Mod m : ModulesPage.P.mods) {
            if (!m.name.equals(name)) continue;
            int cat = Input.catOf(m);
            if (cat < 0) return null;
            Ui.S.page = cat; ModulesPage.P.cat = cat;
            List<int[]> e = Input.entries();
            Input.Layout L = Input.layout(e);
            int idx = Input.modIdx(m);
            for (int i = 0; i < e.size(); i++) {
                if (e.get(i)[0] != cat || e.get(i)[1] != idx) continue;
                float[] r = L.r[i];
                return new float[]{(r[0] + r[2]) - 60f + 22f, r[1] + (ModulesPage.P.grid ? 14f : 30f) + 12f};
            }
        }
        return null;
    }

    /** One-line state: UI model vs the real client module (read back through reflection). */
    public static String state(String name) {
        if (name.equals("hudsmoke")) return hudSmoke();
        if (name.equals("cfgtest")) return LocalConfigs.selfTest();
        if (name.equals("engine")) return dev.zprestige.prestige.engine.EngineState.details();
        if (name.equals("listeners")) return listeners();
        for (ModulesPage.Mod m : ModulesPage.P.mods) {
            if (!m.name.equals(name)) continue;
            Bridge.LiveModule l = m.live;
            if (l == null) return name + " ui=" + m.enabled + " live=<none>";
            Bridge.LiveSetting es = l.enabledSettingForTest();
            return name + " ui=" + m.enabled + " live.a()=" + l.enabled() + " enabledSetting.value=" + (es == null ? null : es.value());
        }
        return name + " <unknown module>";
    }

    /** Event types that currently have listeners on the client EventBus (module lifecycle check): type -> listener owner classes. */
    public static String listeners() {
        try {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) fieldOfType(dev.zprestige.prestige.EventBus.class, java.util.Map.class).get(dev.zprestige.prestige.client.Prestige.eventBus);
            int total = 0;
            StringBuilder sb = new StringBuilder();
            java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                Object[] arr = (Object[]) e.getValue();
                total += arr.length;
                java.util.TreeSet<String> owners = new java.util.TreeSet<>();
                for (Object le : arr) owners.add(((dev.zprestige.prestige.ListenerEntry) le).getTarget().getClass().getSimpleName());
                sorted.put(((Class<?>) e.getKey()).getSimpleName(), owners.toString());
            }
            sb.append("LISTENERS types=").append(map.size()).append(" entries=").append(total);
            sorted.forEach((k, v) -> sb.append(System.lineSeparator()).append("  ").append(k).append(" <- ").append(v));
            return sb.toString();
        } catch (Throwable t) {
            Throwable r = t; while (r.getCause() != null) r = r.getCause();
            return "LISTENERS failed " + r;
        }
    }

    /** Dev smoke test (command "state hudsmoke"): draws every HUD element's editor outline and runs the two recovered Runnables of the client. */
    private static String hudSmoke() {
        StringBuilder sb = new StringBuilder("HUDSMOKE");
        try {
            dev.zprestige.prestige.hud.HudElement[] els = (dev.zprestige.prestige.hud.HudElement[])
                    fieldOfType(dev.zprestige.prestige.gui.HudEditorScreen.class, dev.zprestige.prestige.hud.HudElement[].class).get(dev.zprestige.prestige.client.Prestige.hudEditorScreen);
            sb.append(" elements=").append(els.length);
            org.joml.Matrix4f mat = new org.joml.Matrix4f();
            for (dev.zprestige.prestige.hud.HudElement el : els) {
                String n = el.getClass().getSimpleName();
                try {
                    el.x = 20f;
                    el.y = 20f;
                    el.width = 80f;
                    el.height = 24f;
                    el.renderEditorOutline(mat);
                    sb.append(" [").append(n).append(".renderEditorOutline ok]");
                } catch (Throwable t) {
                    Throwable r = t; while (r.getCause() != null) r = r.getCause();
                    sb.append(" [").append(n).append(".renderEditorOutline FAIL ").append(r).append("]");
                }
            }
            // QueuedSlotSelect: with the player present it selects a hotbar slot, otherwise returns
            new dev.zprestige.prestige.render.QueuedSlotSelect(dev.zprestige.prestige.client.Prestige.silentRotationManager, 0).run();
            sb.append(" [QueuedSlotSelect.run ok]");
            // CobwebSwitchBackTask on the live CobwebPlacer module
            Bridge.LiveModule cw = Bridge.byName("Cobweb Placer");
            if (cw != null) {
                new dev.zprestige.prestige.world.CobwebSwitchBackTask((dev.zprestige.prestige.module.combat.CobwebPlacer) cw.handle).run();
                sb.append(" [CobwebSwitchBackTask.run ok]");
            } else sb.append(" [CobwebSwitchBackTask: module not found]");
        } catch (Throwable t) {
            Throwable r = t; while (r.getCause() != null) r = r.getCause();
            sb.append(" ABORT ").append(r);
        }
        return sb.toString();
    }

    /** First instance field of the given type (looked up by type so renaming the field cannot break it). */
    private static java.lang.reflect.Field fieldOfType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (java.lang.reflect.Field f : owner.getDeclaredFields())
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) { f.setAccessible(true); return f; }
        throw new NoSuchFieldException(type.getSimpleName() + " in " + owner.getSimpleName());
    }
}

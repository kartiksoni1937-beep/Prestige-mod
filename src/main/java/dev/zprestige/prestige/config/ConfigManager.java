package dev.zprestige.prestige.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.KeybindSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Setting;
import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.PendingModuleActivation;
import dev.zprestige.prestige.hud.HudElement;
import dev.zprestige.prestige.math.SettingValueParser;
import dev.zprestige.prestige.render.ConfigReport;
import dev.zprestige.prestige.type.NotificationType;
import dev.zprestige.prestige.type.HudAlignment;
import dev.zprestige.prestige.type.HudAnchor;
import dev.zprestige.prestige.util.NotificationManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;

public class ConfigManager {
    private static final Gson GSON = new Gson();
    private String lastCompleteSnapshot;
    private String lastLoadReport;
    private static final long NOTIFICATION_DURATION_MS = 8000L;

    @Chainable
    public String saveToJson() {
        ConfigReport report = new ConfigReport("save");
        JsonObject root = new JsonObject();

        try {
            if (Prestige.moduleManager == null) {
                report.error("Config", "module manager is not ready", null);
            } else {
                HashSet sectionNames = new HashSet();
                sectionNames.add("Menu");
                JsonObject menuSection = new JsonObject();

                for (Setting menuSetting : (Iterable<Setting>)(Prestige.moduleManager.getMenu().settings)) {
                    this.saveSetting(menuSection, menuSetting, "Menu", report);
                }

                root.add("Menu", menuSection);

                for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
                    String moduleName = module.getName();
                    if (moduleName == null || moduleName.isBlank()) {
                        report.error("Unnamed module", "module has no config name", null);
                    } else if (!sectionNames.add(moduleName)) {
                        report.error(moduleName, "duplicate config section name", null);
                    } else {
                        JsonObject moduleSection = new JsonObject();

                        for (Setting coreSetting : (Iterable<Setting>)(module.getCoreSettings())) {
                            this.saveSetting(moduleSection, coreSetting, moduleName, report);
                        }

                        for (Setting setting : (Iterable<Setting>)(module.settings)) {
                            this.saveSetting(moduleSection, setting, moduleName, report);
                        }

                        root.add(moduleName, moduleSection);
                    }
                }

                if (Prestige.hudEditorScreen == null) {
                    report.error("HUD", "HUD editor is not ready", null);
                } else {
                    for (HudElement element : Prestige.hudEditorScreen.getElements()) {
                        String elementName = element.getName();
                        if (elementName == null || elementName.isBlank()) {
                            report.error("HUD", "component has no config name", null);
                        } else if (!sectionNames.add(elementName)) {
                            report.error("HUD > " + elementName, "duplicate config section name", null);
                        } else {
                            try {
                                JsonObject elementSection = new JsonObject();
                                elementSection.addProperty("Enabled", element.isEnabled());
                                elementSection.addProperty("X", element.getX());
                                elementSection.addProperty("Y", element.getY());
                                if (element.isPositionPending()) {
                                    report.addValues(3);
                                } else {
                                    elementSection.addProperty("RuleX", element.anchorX.name());
                                    elementSection.addProperty("RuleY", element.anchorY.name());
                                    elementSection.addProperty("OffX", element.offsetX);
                                    elementSection.addProperty("OffY", element.offsetY);
                                    report.addValues(7);
                                }

                                root.add(elementName, elementSection);
                            } catch (Throwable elementError) {
                                rethrowIfFatal(elementError);
                                report.error("HUD > " + elementName, describeError(elementError), elementError);
                            }
                        }
                    }
                }
            }
        } catch (Throwable saveError) {
            rethrowIfFatal(saveError);
            report.error("Config", "unexpected save failure: " + describeError(saveError), saveError);
        }

        String json;
        try {
            json = root.toString();
        } catch (Throwable serializeError) {
            rethrowIfFatal(serializeError);
            report.error("Config", "could not serialize snapshot: " + describeError(serializeError), serializeError);
            json = "{}";
        }

        if (report.hasErrors()) {
            if (this.lastCompleteSnapshot != null) {
                report.warn("Config", "save was aborted; the previous complete snapshot was kept", null);
                json = this.lastCompleteSnapshot;
            } else {
                report.error("Config", "save was aborted; no complete fallback snapshot exists", null);
                json = "{}";
            }
        } else {
            this.lastCompleteSnapshot = json;
        }

        notifyResult(report);
        return json;
    }

    @Chainable
    public void loadFromJson(String json) {
        ConfigReport report = new ConfigReport("load");

        try {
            JsonObject root = this.parseConfigJson(json, report);
            if (root == null) {
                return;
            }

            if (Prestige.moduleManager != null) {
                HashSet sectionNames = new HashSet();
                sectionNames.add("Menu");
                ArrayList pendingStates = new ArrayList();
                Set predisabled = Collections.newSetFromMap(new IdentityHashMap());
                this.disableModulesBeforeLoad(root, predisabled, report);
                this.loadMenuSection(root, report);

                for (Module module : (Iterable<Module>)(Prestige.moduleManager.getModules())) {
                    String moduleName = module.getName();
                    if (moduleName != null && !moduleName.isBlank()) {
                        if (!sectionNames.add(moduleName)) {
                            report.error(moduleName, "duplicate module config name; section was skipped", null);
                        } else {
                            this.loadModuleSection(root, module, moduleName, predisabled, pendingStates, report);
                        }
                    }
                }

                if (Prestige.hudEditorScreen == null) {
                    report.warn("HUD", "HUD editor is not ready; HUD layout was skipped", null);
                } else {
                    for (HudElement element : Prestige.hudEditorScreen.getElements()) {
                        String elementName = element.getName();
                        if (elementName == null || elementName.isBlank()) {
                            report.error("HUD", "component has no config name", null);
                        } else if (!sectionNames.add(elementName)) {
                            report.error("HUD > " + elementName, "duplicate config section name; section was skipped", null);
                        } else {
                            this.loadHudSection(root, element, elementName, report);
                        }
                    }
                }

                this.reportUnknownKeys(root, sectionNames, "", "module or HUD section", report);

                for (PendingModuleActivation pending : (Iterable<PendingModuleActivation>)(pendingStates)) {
                    this.applyModuleEnabledState(pending.enabledValue(), pending.module(), pending.sectionName(), pending.alreadyApplied(), report);
                }

                if (report.sectionCount == 0) {
                    report.error("Config", "no recognized config sections were found", null);
                }

                return;
            }

            report.error("Config", "module manager is not ready", null);
        } catch (Throwable error) {
            rethrowIfFatal(error);
            report.error("Config", "unexpected load failure: " + describeError(error), error);
            return;
        } finally {
            this.lastLoadReport = report.getSummary();
            notifyResult(report);
        }
    }

    @Chainable
    public String consumeLoadReport() {
        String report = this.lastLoadReport;
        this.lastLoadReport = null;
        return report;
    }

    private void disableModulesBeforeLoad(JsonObject root, Set predisabled, ConfigReport report) {
        HashSet seenNames = new HashSet();
        seenNames.add("Menu");
        Iterator moduleIterator = Prestige.moduleManager.getModules().iterator();

        while (true) {
            Module module;
            String name;
            boolean currentlyEnabled;
            while (true) {
                boolean configEnabled;
                while (true) {
                    if (!moduleIterator.hasNext()) {
                        return;
                    }

                    module = (Module)moduleIterator.next();
                    name = module.getName();
                    if (name != null && !name.isBlank() && seenNames.add(name)) {
                        JsonElement section = root.get(name);
                        if (section != null && section.isJsonObject()) {
                            JsonObject sectionObject = section.getAsJsonObject();
                            if (sectionObject.has("Enabled")) {
                                try {
                                    configEnabled = SettingValueParser.parseBoolean(this.readScalarString(sectionObject.get("Enabled")));
                                    break;
                                } catch (Throwable parseError) {
                                    rethrowIfFatal(parseError);
                                }
                            }
                        }
                    }
                }

                if (!configEnabled) {
                    try {
                        currentlyEnabled = module.isEnabled();
                        break;
                    } catch (Throwable stateError) {
                        rethrowIfFatal(stateError);
                        report.error(name + " > Enabled", "could not inspect current module state: " + describeError(stateError), stateError);
                    }
                }
            }

            if (currentlyEnabled) {
                predisabled.add(module);
                String settingPath = name + " > Enabled";

                try {
                    module.toggle();
                    if (module.isEnabled()) {
                        module.toggle();
                    }

                    if (!module.isEnabled()) {
                        report.addValues(1);
                    } else {
                        report.warn(settingPath, "module changed its own state while being disabled", null);
                    }
                } catch (Throwable toggleError) {
                    rethrowIfFatal(toggleError);
                    report.error(settingPath, this.restoreModuleState(module, true, toggleError) ? "disable lifecycle failed; previous state was restored" : "disable lifecycle failed; state may be inconsistent", toggleError);
                }
            }
        }
    }

    private void loadMenuSection(JsonObject root, ConfigReport report) {
        if (root.has("Menu")) {
            report.sectionCount++;
            JsonObject section = this.requireSectionObject(root.get("Menu"), "Menu", report);
            if (section != null) {
                HashSet seenSettings = new HashSet();

                for (Setting setting : (Iterable<Setting>)(Prestige.moduleManager.getMenu().settings)) {
                    this.loadSetting(section, setting, "Menu", seenSettings, report);
                }

                this.reportUnknownKeys(section, seenSettings, "Menu", "setting", report);
            }
        }
    }

    private void loadModuleSection(JsonObject root, Module module, String name, Set predisabled, List pendingStates, ConfigReport report) {
        if (root.has(name)) {
            report.sectionCount++;
            JsonObject section = this.requireSectionObject(root.get(name), name, report);
            if (section != null) {
                HashSet seenSettings = new HashSet();
                boolean hasEnabledSetting = false;

                for (Setting coreSetting : (Iterable<Setting>)(module.getCoreSettings())) {
                    if ("Enabled".equals(coreSetting.getName())) {
                        hasEnabledSetting = true;
                        seenSettings.add("Enabled");
                    } else {
                        this.loadSetting(section, coreSetting, name, seenSettings, report);
                    }
                }

                for (Setting setting : (Iterable<Setting>)(module.settings)) {
                    this.loadSetting(section, setting, name, seenSettings, report);
                }

                this.reportUnknownKeys(section, seenSettings, name, "setting", report);
                if (hasEnabledSetting && section.has("Enabled")) {
                    pendingStates.add(new PendingModuleActivation(module, name, section.get("Enabled"), predisabled.contains(module)));
                }
            }
        }
    }

    private void applyModuleEnabledState(JsonElement enabledValue, Module module, String name, boolean alreadyDisabled, ConfigReport report) {
        String settingPath = name + " > Enabled";
        if (!alreadyDisabled) {
            try {
                boolean wantEnabled = SettingValueParser.parseBoolean(this.readScalarString(enabledValue));
                boolean wasEnabled = module.isEnabled();
                if (wantEnabled == wasEnabled) {
                    report.addValues(1);
                    return;
                }

                if (wantEnabled && !module.isPersistent()) {
                    report.warn(settingPath, "activation is blocked for downloaded configs; settings were still loaded", null);
                    return;
                }

                try {
                    module.toggle();
                    if (module.isEnabled() == wantEnabled) {
                        report.addValues(1);
                    } else {
                        report.warn(settingPath, "module changed its own state during activation", null);
                    }
                } catch (Throwable toggleError) {
                    rethrowIfFatal(toggleError);
                    report.error(settingPath, this.restoreModuleState(module, wasEnabled, toggleError) ? "module lifecycle failed; previous state was restored" : "module lifecycle failed; state may be inconsistent", toggleError);
                }
            } catch (Throwable error) {
                rethrowIfFatal(error);
                report.error(settingPath, describeError(error), error);
            }
        }
    }

    private boolean restoreModuleState(Module module, boolean expectedEnabled, Throwable failure) {
        try {
            if (module.isEnabled() != expectedEnabled) {
                module.toggle();
            }

            return module.isEnabled() == expectedEnabled;
        } catch (Throwable restoreError) {
            rethrowIfFatal(restoreError);
            failure.addSuppressed(restoreError);
            return false;
        }
    }

    private void loadHudSection(JsonObject root, HudElement element, String name, ConfigReport report) {
        if (root.has(name)) {
            report.sectionCount++;
            String sectionPath = "HUD > " + name;
            JsonObject section = this.requireSectionObject(root.get(name), sectionPath, report);
            if (section != null) {
                Set knownKeys = Set.of("Enabled", "X", "Y", "Anchor", "RuleX", "RuleY", "OffX", "OffY");
                this.reportUnknownKeys(section, knownKeys, sectionPath, "setting", report);
                boolean hasRules = section.has("RuleX") && section.has("RuleY") && section.has("OffX") && section.has("OffY");
                boolean hasX = section.has("X");
                boolean hasY = section.has("Y");
                if (hasRules) {
                    try {
                        element.setAnchors(HudAlignment.valueOf(this.readScalarString(section.get("RuleX"))), HudAlignment.valueOf(this.readScalarString(section.get("RuleY"))), SettingValueParser.parseFloat(this.readScalarString(section.get("OffX"))), SettingValueParser.parseFloat(this.readScalarString(section.get("OffY"))));
                        report.addValues(4);
                    } catch (IllegalArgumentException unknownRule) {
                        report.error(sectionPath + " > Position", "unknown positioning rule; position was skipped", null);
                    } catch (Throwable ruleError) {
                        rethrowIfFatal(ruleError);
                        report.error(sectionPath + " > Position", describeError(ruleError), ruleError);
                    }
                } else if (hasX != hasY) {
                    report.error(sectionPath + " > Position", "X and Y must both be present; position was skipped", null);
                } else if (hasX) {
                    try {
                        label128: {
                            float x = SettingValueParser.parseFloat(this.readScalarString(section.get("X")));
                            float y = SettingValueParser.parseFloat(this.readScalarString(section.get("Y")));

                            label129: {
                                {
                                    if (section.has("Anchor")) {
                                        try {
                                            element.setCornerAnchor(HudAnchor.valueOf(this.readScalarString(section.get("Anchor"))), x, y);
                                            report.addValues(3);
                                            break label128;
                                        } catch (IllegalArgumentException unknownAnchor) {
                                            break label129;
                                        }
                                    }
                                }

                                element.setPosition(x, y);
                                report.addValues(2);
                                break label128;
                            }

                            report.error(sectionPath + " > Anchor", "unknown anchor; position was skipped", null);
                        }
                    } catch (Throwable positionError) {
                        rethrowIfFatal(positionError);
                        report.error(sectionPath + " > Position", describeError(positionError), positionError);
                    }
                }

                if (section.has("Enabled")) {
                    try {
                        if (SettingValueParser.parseBoolean(this.readScalarString(section.get("Enabled"))) != element.isEnabled()) {
                            boolean wasEnabled = element.isEnabled();

                            try {
                                element.toggle();
                            } catch (Throwable toggleError) {
                                rethrowIfFatal(toggleError);
                                report.error(sectionPath + " > Enabled", this.restoreHudState(element, wasEnabled, toggleError) ? "HUD lifecycle failed; previous state was restored" : "HUD lifecycle failed; state may be inconsistent", toggleError);
                                return;
                            }
                        }

                        report.addValues(1);
                    } catch (Throwable enabledError) {
                        rethrowIfFatal(enabledError);
                        report.error(sectionPath + " > Enabled", describeError(enabledError), enabledError);
                    }
                }
            }
        }
    }

    private boolean restoreHudState(HudElement element, boolean expectedEnabled, Throwable failure) {
        try {
            if (element.isEnabled() != expectedEnabled) {
                element.toggle();
            }

            return element.isEnabled() == expectedEnabled;
        } catch (Throwable restoreError) {
            rethrowIfFatal(restoreError);
            failure.addSuppressed(restoreError);
            return false;
        }
    }

    private void loadSetting(JsonObject section, Setting setting, String sectionName, Set seenSettings, ConfigReport report) {
        if (setting != null && setting.getName() != null && !setting.getName().isBlank()) {
            String settingName = setting.getName();
            if (!seenSettings.add(settingName)) {
                report.error(sectionName + " > " + settingName, "duplicate registered setting name", null);
            } else if (section.has(settingName)) {
                String settingPath = sectionName + " > " + settingName;

                try {
                    SettingValueParser.applyValue(setting, this.readScalarString(section.get(settingName)));
                    report.addValues(1);
                } catch (Throwable error) {
                    rethrowIfFatal(error);
                    report.error(settingPath, describeError(error), error);
                }
            }
        } else {
            report.warn(sectionName, "an unnamed setting was skipped", null);
        }
    }

    private void saveSetting(JsonObject section, Setting setting, String sectionName, ConfigReport report) {
        if (setting != null && setting.getName() != null && !setting.getName().isBlank()) {
            String settingPath = sectionName + " > " + setting.getName();
            if (section.has(setting.getName())) {
                report.error(settingPath, "duplicate registered setting name", null);
            } else {
                try {
                    section.addProperty(setting.getName(), this.serializeSetting(setting));
                    report.addValues(1);
                } catch (Throwable error) {
                    rethrowIfFatal(error);
                    report.error(settingPath, describeError(error), error);
                }
            }
        } else {
            report.warn(sectionName, "an unnamed setting was skipped", null);
        }
    }

    private JsonObject parseConfigJson(String json, ConfigReport report) {
        if (json != null && !json.isBlank()) {
            if (json.length() > 100000) {
                report.error("Config", "downloaded config is too large", null);
                return null;
            } else if (this.isNestedTooDeeply(json)) {
                report.error("Config", "downloaded config is nested too deeply", null);
                return null;
            } else {
                JsonObject root;
                try {
                    root = GSON.fromJson(json, JsonObject.class);
                } catch (Exception parseError) {
                    report.error("Config", "downloaded config is not valid JSON", parseError);
                    return null;
                }

                if (root == null) {
                    report.error("Config", "downloaded config contained JSON null", null);
                    return null;
                } else {
                    return root;
                }
            }
        } else {
            report.error("Config", "downloaded config was empty", null);
            return null;
        }
    }

    private boolean isNestedTooDeeply(String json) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                if (++depth > 64) {
                    return true;
                }
            } else if (ch == '}' || ch == ']') {
                depth--;
            }
        }

        return false;
    }

    private JsonObject requireSectionObject(JsonElement element, String path, ConfigReport report) {
        if (element == null || element.isJsonNull()) {
            report.error(path, "section is null", null);
            return null;
        } else if (!element.isJsonObject()) {
            report.error(path, "section must be a JSON object", null);
            return null;
        } else {
            return element.getAsJsonObject();
        }
    }

    private String readScalarString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("value is null");
        } else if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException("expected a single value");
        } else {
            return element.getAsString();
        }
    }

    private void reportUnknownKeys(JsonObject section, Set knownKeys, String pathPrefix, String kind, ConfigReport report) {
        for (Entry entry : section.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) {
                report.warn(pathPrefix.isEmpty() ? (String)entry.getKey() : pathPrefix + " > " + (String)entry.getKey(), kind + " is not available in this version; skipped", null);
            }
        }
    }

    private String serializeSetting(Setting setting) {
        if (setting instanceof ColorSetting colorSetting) {
            Color color = colorSetting.getValue();
            if (color == null) {
                throw new IllegalStateException("color is null");
            } else {
                return color.getRed() + "." + color.getGreen() + "." + color.getBlue() + "." + color.getAlpha();
            }
        } else if (setting instanceof KeybindSetting keybindSetting) {
            if (keybindSetting.getValue() == null) {
                throw new IllegalStateException("bind is null");
            } else {
                return keybindSetting.getValue() + "." + keybindSetting.isHoldMode();
            }
        } else if (setting instanceof MultiSetting multiSetting) {
            StringBuilder builder = new StringBuilder();

            for (String option : multiSetting.getOptions()) {
                if (!option.isEmpty()) {
                    builder.append(option).append(".").append(multiSetting.isSelected(option)).append("&");
                }
            }

            return builder.toString();
        } else if (setting instanceof RangeSetting rangeSetting) {
            return rangeSetting.getLow() + "|" + rangeSetting.getHigh();
        } else {
            Object value = setting.getValue();
            if (value == null) {
                throw new IllegalStateException("value is null");
            } else {
                return value.toString();
            }
        }
    }

    private static void notifyResult(ConfigReport report) {
        if (!"load".equals(report.operation) && (report.hasErrors() || report.warningCount != 0)) {
            Runnable showResult = () -> {
                if (Prestige.notificationManager != null) {
                    String verb = "load".equals(report.operation) ? "loaded" : "saved";
                    NotificationManager notifications = Prestige.notificationManager;
                    String title = "Config " + verb;
                    String summary = formatSummary(report.errorCount, report.warningCount, report.valueCount, verb);
                    long durationMs = NOTIFICATION_DURATION_MS;
                    notifications.addNotification(title, summary, durationMs, (NotificationType)(report.errorCount > 0 ? NotificationType.ERROR : (report.warningCount > 0 ? NotificationType.WARNING : NotificationType.SUCCESS)));
                }
            };

            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && !client.isOnThread()) {
                    client.execute(showResult);
                } else {
                    showResult.run();
                }
            } catch (Throwable error) {
                rethrowIfFatal(error);
            }
        }
    }

    public static String formatSummary(int errors, int skipped, int applied, String verb) {
        return errors + " errors, " + skipped + " skipped, " + applied + " " + verb;
    }

    private static String describeError(Throwable error) {
        String message = error.getMessage();
        return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
    }

    private static void rethrowIfFatal(Throwable error) {
        if (error instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        } else if (error instanceof VirtualMachineError vmError) {
            throw vmError;
        } else {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

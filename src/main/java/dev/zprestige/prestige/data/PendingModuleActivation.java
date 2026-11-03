package dev.zprestige.prestige.data;

import com.google.gson.JsonElement;
import dev.zprestige.prestige.Module;

public record PendingModuleActivation(Module module, String sectionName, JsonElement enabledValue, boolean alreadyApplied) {
}

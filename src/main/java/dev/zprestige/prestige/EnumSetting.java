package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;

public class EnumSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public String getValue() {
        return (String)super.getValue();
    }

    private final String[] options;

    public EnumSetting(String settingName, String defaultOption, String[] optionValues) {
        super(settingName, defaultOption);
        this.options = optionValues;
    }

    @Chainable
    public String[] getOptions() {
        return this.options;
    }

    public EnumSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public EnumSetting withDescription(String newDescription) {
        super.setDescription(newDescription);
        return this;
    }

    @Override
    public Setting setVisibility(Predicate visibilityPredicate) {
        return this.visibleWhen(visibilityPredicate);
    }

    @Chainable
    @Override
    public Setting setDescription(String newDescription) {
        return this.withDescription(newDescription);
    }
}

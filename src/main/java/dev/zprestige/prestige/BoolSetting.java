package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;

public class BoolSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public Boolean getValue() {
        return (Boolean)super.getValue();
    }

    public BoolSetting(String settingName, boolean defaultValue) {
        super(settingName, defaultValue);
    }

    public BoolSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public BoolSetting withDescription(String newDescription) {
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

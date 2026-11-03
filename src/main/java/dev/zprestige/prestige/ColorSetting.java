package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.awt.Color;
import java.util.function.Predicate;

public class ColorSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public java.awt.Color getValue() {
        return (java.awt.Color)super.getValue();
    }

    public ColorSetting(String settingName, Color defaultColor) {
        super(settingName, defaultColor);
    }

    public ColorSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public ColorSetting withDescription(String newDescription) {
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

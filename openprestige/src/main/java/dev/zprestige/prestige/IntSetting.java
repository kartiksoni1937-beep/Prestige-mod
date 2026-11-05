package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;

public class IntSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public Integer getValue() {
        return (Integer)super.getValue();
    }

    private final int min;
    private final int max;

    public IntSetting(String settingName, int defaultValue, int minValue, int maxValue) {
        super(settingName, defaultValue);
        this.min = minValue;
        this.max = maxValue;
    }

    @Chainable
    public int getMin() {
        return this.min;
    }

    @Chainable
    public int getMax() {
        return this.max;
    }

    public IntSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public IntSetting withDescription(String newDescription) {
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

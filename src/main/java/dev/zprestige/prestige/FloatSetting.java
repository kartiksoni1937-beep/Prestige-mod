package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;

public class FloatSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public Float getValue() {
        return (Float)super.getValue();
    }

    private final float min;
    private final float max;

    public FloatSetting(String settingName, float defaultValue, float minValue, float maxValue) {
        super(settingName, defaultValue);
        this.min = minValue;
        this.max = maxValue;
    }

    @Chainable
    public float getMin() {
        return this.min;
    }

    @Chainable
    public float getMax() {
        return this.max;
    }

    public FloatSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public FloatSetting withDescription(String newDescription) {
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

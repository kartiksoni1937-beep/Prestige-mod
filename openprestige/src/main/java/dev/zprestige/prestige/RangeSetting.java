package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import dev.zprestige.prestige.render.RandomUtil;
import java.util.function.Predicate;

public class RangeSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public Float getValue() {
        return (Float)super.getValue();
    }

    private final float min;
    private final float max;
    private float low;
    private float high;
    private float rolled;

    public RangeSetting(String settingName, float defaultLow, float defaultHigh, float minValue, float maxValue) {
        super(settingName, defaultLow);
        this.low = defaultLow;
        this.high = defaultHigh;
        this.min = minValue;
        this.max = maxValue;
        this.rolled = defaultLow;
    }

    @Chainable
    public float getLow() {
        return this.low;
    }

    @Chainable
    public void setLow(float newLow) {
        this.low = newLow;
    }

    @Chainable
    public float getHigh() {
        return this.high;
    }

    @Chainable
    public void setHigh(float newHigh) {
        this.high = newHigh;
    }

    @Chainable
    public float getMin() {
        return this.min;
    }

    @Chainable
    public float getMax() {
        return this.max;
    }

    public float getRolled() {
        return this.rolled;
    }

    public void setRolled(float newRolled) {
        this.rolled = newRolled;
    }

    public void reroll() {
        this.rolled = this.randomInRange();
    }

    public float randomInRange() {
        return RandomUtil.randomFloat(this.low, this.high);
    }

    public RangeSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public RangeSetting withDescription(String newDescription) {
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

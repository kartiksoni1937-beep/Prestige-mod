package dev.zprestige.prestige;

import dev.zprestige.prestige.annotation.Chainable;
import java.util.function.Predicate;

public class TextSetting extends Setting {
    // covariant getValue (added by fix_typed_values.py)
    @Override
    public String getValue() {
        return (String)super.getValue();
    }

    private final String placeholder;

    public TextSetting(String settingName, String defaultText, String placeholderText) {
        super(settingName, defaultText);
        this.placeholder = placeholderText;
    }

    public String getPlaceholder() {
        return this.placeholder;
    }

    public TextSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public TextSetting withDescription(String newDescription) {
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

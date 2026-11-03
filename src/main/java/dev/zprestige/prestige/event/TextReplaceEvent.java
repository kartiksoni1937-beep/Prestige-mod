package dev.zprestige.prestige.event;

public class TextReplaceEvent extends Event {
    private String target;
    private String text;

    public TextReplaceEvent(String initialTarget, String initialText) {
        this.target = initialTarget;
        this.text = initialText;
    }

    public String getTarget() {
        return this.target;
    }

    public void setTarget(String value) {
        this.target = value;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String value) {
        this.text = value;
    }
}

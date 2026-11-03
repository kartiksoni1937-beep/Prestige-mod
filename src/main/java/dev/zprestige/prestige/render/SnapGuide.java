package dev.zprestige.prestige.render;

import dev.zprestige.prestige.type.GuideAxis;

public class SnapGuide {
    private final GuideAxis axis;
    private final int position;

    public SnapGuide(GuideAxis guideAxis, int coordinate) {
        this.axis = guideAxis;
        this.position = coordinate;
    }

    public GuideAxis getAxis() {
        return this.axis;
    }

    public int getPosition() {
        return this.position;
    }
}

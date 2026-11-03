package dev.zprestige.prestige.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class NameTagEntry {
    public NameTagEntry() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    public String name;
    public Color nameColor;
    public int pops;
    public String popText;
    public float healthRatio;
    public float displayedHealthRatio;
    public Color healthColor;
    public String healthText;
    public float nameWidth;
    public float popTextWidth;
    public float healthTextWidth;
    public List items = new ArrayList();
    public List enchantLabels = new ArrayList();
}

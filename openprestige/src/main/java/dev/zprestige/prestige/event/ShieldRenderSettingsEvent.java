package dev.zprestige.prestige.event;

import java.awt.Color;
import net.minecraft.util.math.Vec3d;

public class ShieldRenderSettingsEvent extends Event {
    public boolean enabled;
    public boolean overlay;
    public boolean renderSelf;
    public Vec3d offset;
    public Vec3d scale;
    public String renderMode;
    public Color defaultColor;
    public Color brokenColor;
}

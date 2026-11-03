package dev.zprestige.prestige.data;

import java.util.ArrayList;
import net.minecraft.util.math.Vec3d;

public record TrajectoryPath(ArrayList<Vec3d> points, boolean landed) {
}

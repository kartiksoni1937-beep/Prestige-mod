package dev.zprestige.prestige.data;

import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public record CameraMatrices(Matrix4f projection, Matrix4f modelView, Camera camera) {
}

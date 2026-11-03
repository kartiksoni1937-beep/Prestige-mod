package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;

public interface RenderCallback {
    void onRender(BufferSource buffers, CameraMatrices context);
}

package dev.zprestige.prestige.mixin;

import net.lenni0451.classtransform.annotations.CTransformer;
import net.minecraft.client.render.BackgroundRenderer;

/**
 * The 1.21.9 target net.minecraft.client.render.fog.FogRenderer does not exist in Yarn 1.21+build.9, so this
 * transformer is retargeted to BackgroundRenderer (the same rendering class under its 1.21 name). It stays empty,
 * so no hooks are installed; it exists to keep the engine's transformer list and target count intact.
 */
@CTransformer({BackgroundRenderer.class})
public abstract class FogRendererMixin {
}

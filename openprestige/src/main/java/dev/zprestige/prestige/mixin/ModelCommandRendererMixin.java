package dev.zprestige.prestige.mixin;

import net.lenni0451.classtransform.annotations.CTransformer;

/**
 * Target class absent from Yarn 1.21+build.9. Registered by name so the engine's target count stays intact; the
 * transformer is empty, so nothing is injected even if a matching anchor ever applies.
 */
@CTransformer(value = {}, name = {"net.minecraft.client.render.command.ModelCommandRenderer"})
public abstract class ModelCommandRendererMixin {
}

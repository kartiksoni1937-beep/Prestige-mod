package dev.zprestige.prestige.engine.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Engine anchor for net.minecraft.client.render.fog.FogRenderer: class absent in 1.21, anchor is a no-op. */
@Mixin(targets = "net.minecraft.client.render.fog.FogRenderer", remap = false, priority = 2000)
public abstract class N27 {}

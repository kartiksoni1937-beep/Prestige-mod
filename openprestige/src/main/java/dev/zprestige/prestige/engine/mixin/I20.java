package dev.zprestige.prestige.engine.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Engine anchor for net.minecraft.class_9975 (SkyRendering intermediary): class absent in 1.21, anchor is a no-op. */
@Mixin(targets = "net.minecraft.class_9975", remap = false, priority = 2000)
public abstract class I20 {}

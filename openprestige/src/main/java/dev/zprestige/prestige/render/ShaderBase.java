package dev.zprestige.prestige.render;

import net.minecraft.client.MinecraftClient;

public abstract class ShaderBase {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final long START_TIME_MS = System.currentTimeMillis();
    public final Shader shader;

    public ShaderBase(String vertexPath, String fragmentPath, boolean spirv, long unusedKey) {
        super();
        unusedKey = 0L;
        unusedKey = 50459426823510L ^ unusedKey;
        this.shader = ShaderCompiler.loadProgram(vertexPath, fragmentPath, spirv);
    }

    public ShaderBase(String vertexPath, String fragmentPath) {
        this(vertexPath, fragmentPath, false, (12485776094193L));
    }

    public Shader getShader() {
        return this.shader;
    }
}

package dev.zprestige.prestige.render;

public class CoreShaders {
    public static final Shader POS_COLOR;
    public static final Shader POS_COLOR_TEX;
    public static final Shader ROUNDED_RECT;
    public static final Shader GAUSSIAN_BLUR;
    public static final Shader MSDF_TEXT;

    static {
        POS_COLOR = ShaderCompiler.loadProgram("shaders/pos_col.vert", "shaders/pos_col.frag", false);
        POS_COLOR_TEX = ShaderCompiler.loadProgram("shaders/pos_col_uv.vert", "shaders/pos_col_uv.frag", false);
        ROUNDED_RECT = ShaderCompiler.loadProgram("shaders/rounded_pos_color.vert", "shaders/rounded_pos_color.frag", false);
        GAUSSIAN_BLUR = ShaderCompiler.loadProgram("shaders/post.vert", "shaders/gaussian.frag", false);
        MSDF_TEXT = ShaderCompiler.loadProgram("shaders/pos_col_uv.vert", "shaders/msdf_text.frag", false);
    }
}

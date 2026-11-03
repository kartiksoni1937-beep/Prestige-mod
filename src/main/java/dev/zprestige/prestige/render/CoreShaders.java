package dev.zprestige.prestige.render;

/**
 * Core 2D shader programs.
 *
 * <p>Loading is guarded: on a GL implementation that cannot compile one of the programs (for example a reduced GLES
 * translation layer) the failure is reported once and the 2D layer is marked unavailable instead of taking down the
 * game with an {@link ExceptionInInitializerError}. {@link #available()} lets the HUD/ClickGUI skip drawing while the
 * rest of the client keeps working.
 */
public class CoreShaders {
    public static final Shader POS_COLOR;
    public static final Shader POS_COLOR_TEX;
    public static final Shader ROUNDED_RECT;
    public static final Shader GAUSSIAN_BLUR;
    public static final Shader MSDF_TEXT;
    private static final boolean AVAILABLE;

    static {
        POS_COLOR = load("shaders/pos_col.vert", "shaders/pos_col.frag");
        POS_COLOR_TEX = load("shaders/pos_col_uv.vert", "shaders/pos_col_uv.frag");
        ROUNDED_RECT = load("shaders/rounded_pos_color.vert", "shaders/rounded_pos_color.frag");
        GAUSSIAN_BLUR = load("shaders/post.vert", "shaders/gaussian.frag");
        MSDF_TEXT = load("shaders/pos_col_uv.vert", "shaders/msdf_text.frag");
        AVAILABLE = POS_COLOR != null && POS_COLOR_TEX != null && ROUNDED_RECT != null
                && GAUSSIAN_BLUR != null && MSDF_TEXT != null;
        if (!AVAILABLE) {
            System.err.println("[Prestige] one or more 2D shaders could not be compiled on this GL implementation; "
                    + "HUD and ClickGUI rendering are disabled for this session (gameplay is unaffected)");
        }
    }

    private static Shader load(String vertex, String fragment) {
        try {
            return ShaderCompiler.loadProgram(vertex, fragment, false);
        } catch (Throwable error) {
            System.err.println("[Prestige] shader program unavailable (" + vertex + " + " + fragment + "): " + error);
            return null;
        }
    }

    /** True when every core 2D shader compiled. */
    public static boolean available() {
        return AVAILABLE;
    }
}

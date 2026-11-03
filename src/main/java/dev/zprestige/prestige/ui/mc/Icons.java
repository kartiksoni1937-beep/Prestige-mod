package dev.zprestige.prestige.ui.mc;

import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.TextureLoader;
import dev.zprestige.prestige.ui.Lib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Loads the ClickGUI PNG icons into GL textures through the client's own {@link TextureLoader} (stb_image, which is
 * part of Minecraft's LWJGL bundle on every platform including Android arm64) instead of AWT ImageIO.
 *
 * <p>The returned ids are the opaque texture ids the UI pages pass to {@code Lib.image}.
 */
public final class Icons {
    private Icons() {}

    static final String[] FILES = {
        "logo.png", "categories/combat.png", "categories/mace.png", "categories/misc.png", "categories/movement.png",
        "categories/spear.png", "categories/visual.png", "other/menu.png", "other/themes.png", "other/configs.png",
        "other/social.png", "other/keyboard.png"};

    public static long[] load() throws Exception {
        long[] ids = new long[FILES.length];
        for (int i = 0; i < FILES.length; i++) {
            GlTextureHandle handle = TextureLoader.loadTexture("prestige_ui/icons/" + FILES[i], GL11.GL_LINEAR, GL12.GL_CLAMP_TO_EDGE, false);
            ids[i] = Lib.texture(handle);
        }
        return ids;
    }
}

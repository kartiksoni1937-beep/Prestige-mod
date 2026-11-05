package dev.zprestige.prestige.math;

import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.type.BlendMode;
import org.joml.Vector4f;

public class TexturedDrawModeKey {
    public final GlTextureHandle texture;
    public final Vector4f radius;
    public final BlendMode blendMode;
    public final boolean depthTest;

    public TexturedDrawModeKey(GlTextureHandle glTexture, Vector4f cornerRadius, BlendMode blend, boolean useDepthTest) {
        this.texture = glTexture;
        this.radius = new Vector4f(cornerRadius);
        this.blendMode = blend;
        this.depthTest = useDepthTest;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof TexturedDrawModeKey that) ? false : this.depthTest == that.depthTest && this.blendMode == that.blendMode && this.texture.equals(that.texture) && this.radius.equals(that.radius);
        }
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * this.texture.hashCode() + this.radius.hashCode()) + this.blendMode.hashCode()) + Boolean.hashCode(this.depthTest);
    }
}

package dev.zprestige.prestige.type;

import dev.zprestige.prestige.render.AuraOutlineShader;
import dev.zprestige.prestige.render.ChamsOutlineShader;
import dev.zprestige.prestige.render.OutlineShader;
import dev.zprestige.prestige.render.PostShader;
import dev.zprestige.prestige.render.RainbowOutlineShader;

public enum ShaderMode {
    NONE(null),
    NORMAL(new OutlineShader()),
    CHAMS(new ChamsOutlineShader()),
    AURA(new AuraOutlineShader()),
    RAINBOW(new RainbowOutlineShader());

    private final PostShader shader;

    private ShaderMode(PostShader postShader) {
        this.shader = postShader;
    }

    public PostShader getShader() {
        return this.shader;
    }
}

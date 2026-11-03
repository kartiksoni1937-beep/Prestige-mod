package dev.zprestige.prestige.data;

import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.CoreShaders;
import dev.zprestige.prestige.render.GlRenderPass;
import dev.zprestige.prestige.render.RenderStateElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20;

public record MsdfFontState(GlTextureHandle font, float unitRangeX, float unitRangeY) implements RenderStateElement {
    private static final int UNIT_RANGE_LOCATION;

    @Override
    public void apply(GlRenderPass renderPass) {
        GL13C.glActiveTexture(33984);
        GL11.glBindTexture(3553, this.font.glId);
        GL20.glUniform2f(UNIT_RANGE_LOCATION, this.unitRangeX, this.unitRangeY);
    }

    static {
        UNIT_RANGE_LOCATION = CoreShaders.MSDF_TEXT.getUniformLocation("unitRange");
        CoreShaders.MSDF_TEXT.setUniformInt("msdfTexture", 0);
    }
}

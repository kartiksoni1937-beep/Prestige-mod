package dev.zprestige.prestige.render;

public class CornerRadiiUniform implements RenderStateElement {
    public final CornerRadii radii;

    public CornerRadiiUniform(CornerRadii cornerRadii) {
        this.radii = cornerRadii;
    }

    @Override
    public void apply(GlRenderPass pass) {
        org.lwjgl.opengl.GL20.glUniform4f(DrawPipelines.ROUNDNESS_LOCATION, this.radii.topRight, this.radii.bottomRight, this.radii.topLeft, this.radii.bottomLeft);
    }
}

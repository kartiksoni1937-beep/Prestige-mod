package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.ProgramUniforms;
import java.awt.Color;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class OutlineShader extends PostShader {
    private final Color color;

    public OutlineShader() {
        super("shaders/post.vert", "shaders/outline.frag");
        this.color = Color.WHITE;
    }

    @Override
    public MappedMeshBuilder getPipelineBuffer(BufferSource buffers, int textureId) {
        return buffers.getBuffer(
            (DrawPipeline)(this.pipelineCache
                    .computeIfAbsent(
                        textureId,
                        cacheKey -> {
                            VertexLayout format = VertexLayouts.POSITION_COLOR_UV;
                            RenderStateElement[] states = new RenderStateElement[]{
                                RenderStateElement.target(this.maskFramebuffer.glId), RenderStateElement.shader(CoreShaders.POS_COLOR_TEX), null, null, null, null
                            };
                            states[2] = RenderStateElement.capability(2884, false, true);
                            states[3] = RenderStateElement.blend(true, true);
                            states[4] = RenderStateElement.depthFunc(519, 515);
                            states[5] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> {
                                GL13.glActiveTexture(33984);
                                GL11.glBindTexture(3553, textureId);
                            });
                            return new DrawPipeline(format, 4, true, states);
                        }
                    ))
        );
    }

    @Override
    public void setDefaultUniforms(ProgramUniforms uniforms) {
        uniforms.setVec4("color", this.color.getRed() / 255.0F, this.color.getGreen() / 255.0F, this.color.getBlue() / 255.0F, this.color.getAlpha() / 255.0F);
    }

    @Override
    public FullscreenPassChain createCompositePasses() {
        return new FullscreenPassChain().addDynamicPass(this.getShader(), (Consumer<GlRenderPass>)pass -> {
            GL13.glActiveTexture(33984);
            GL11.glBindTexture(3553, this.maskTexture.glId);
            this.applyUniforms();
            ProgramUniforms.of(this.getShader().getProgramId()).setVec2("oneTexel", this.maskWidth > 0 ? 1.0F / this.maskWidth : 0.0F, this.maskHeight > 0 ? 1.0F / this.maskHeight : 0.0F);
        }, (java.util.function.IntSupplier)RenderCore::getMainFramebufferId);
    }

    static {
        CoreShaders.POS_COLOR_TEX.setUniformInt("texSampler", 0);
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.ProgramUniforms;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class RainbowOutlineShader extends PostShader {
    private final float rainbowSpeed;
    private final float saturation;
    private final float brightness;
    private final float glowSize;

    public RainbowOutlineShader() {
        super("shaders/post.vert", "shaders/rainbow_outline.frag");
        this.rainbowSpeed = 0.5F;
        this.saturation = 0.2F;
        this.brightness = 1.0F;
        this.glowSize = 1.0F;
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
        uniforms.setFloat("time", (float)(System.currentTimeMillis() - START_TIME_MS) / 1000.0F).setFloat("rainbowSpeed", 0.5F).setFloat("saturation", 0.2F).setFloat("brightness", 1.0F).setFloat("size", 1.0F);
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

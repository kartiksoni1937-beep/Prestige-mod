package dev.zprestige.prestige.render;

import it.unimi.dsi.fastutil.Pair;
import dev.zprestige.prestige.data.ProgramUniforms;
import dev.zprestige.prestige.type.GlPrimitive;
import java.util.Map;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;

public abstract class PostShader extends ShaderBase {
    public FullscreenPassChain compositePasses;
    public int maskWidth;
    public int maskHeight;
    public boolean initialized;
    public GlFramebuffer maskFramebuffer;
    public GlTextureHandle maskTexture;
    public final Map pipelineCache;
    private Consumer uniformOverride;
    private boolean maskDrawn;

    public PostShader(String vertexPath, String fragmentPath) {
        super(vertexPath, fragmentPath, false, (115399614053456L));
        this.maskWidth = -1;
        this.maskHeight = -1;
        this.initialized = false;
        this.maskFramebuffer = GlFramebuffer.create();
        this.pipelineCache = dev.zprestige.prestige.math.RenderCacheUtil.newLruCache(256);
        this.maskTexture = GlTextureHandle.create(3553);   // reconstructed: colour attachment texture (GL_TEXTURE_2D), sized on first use in g()
    }

    public void prepareMask(BufferSource buffers, dev.zprestige.prestige.data.CameraMatrices context) {
        this.ensureInitialized();
        this.resizeIfNeeded();
        buffers.streamActiveBuffers()
            .filter(candidate -> ((DrawPipeline)((Pair)candidate).key()).isEntityMask())
            .forEach(entry -> ((MappedMeshBuilder)((it.unimi.dsi.fastutil.Pair)entry).value()).generateIndices(GlPrimitive.QUADS));
    }

    public void renderComposite(BufferSource buffers, dev.zprestige.prestige.data.CameraMatrices context) {
        if (this.initialized && this.compositePasses != null) {
            if (this.maskDrawn) {
                this.maskDrawn = false;
                boolean cullWasEnabled = GL11.glGetBoolean(2884);
                boolean blendWasEnabled = GL11.glGetBoolean(3042);
                boolean depthWasEnabled = GL11.glGetBoolean(2929);
                GL11.glDisable(2884);
                GL11.glEnable(3042);
                GL11.glBlendFunc(770, 771);
                GL11.glDisable(2929);
                try {
                    this.compositePasses.render();
                } finally {
                    if (depthWasEnabled) {
                        GL11.glEnable(2929);
                    }

                    if (cullWasEnabled) {
                        GL11.glEnable(2884);
                    }

                    if (!blendWasEnabled) {
                        GL11.glDisable(3042);
                    }
                }

                this.maskFramebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            }
        }
    }

    public final MappedMeshBuilder getMaskBuffer(BufferSource buffers, int textureId) {
        this.maskDrawn = true;
        return this.getPipelineBuffer(buffers, textureId);
    }

    public abstract MappedMeshBuilder getPipelineBuffer(BufferSource buffers, int textureId);

    public abstract FullscreenPassChain createCompositePasses();

    public abstract void setDefaultUniforms(ProgramUniforms uniforms);

    public final void setUniformOverride(Consumer override) {
        this.uniformOverride = override;
    }

    public final void applyUniforms() {
        ProgramUniforms uniforms = ProgramUniforms.of(this.getShader().getProgramId());
        if (this.uniformOverride != null) {
            this.uniformOverride.accept(uniforms);
        } else {
            this.setDefaultUniforms(uniforms);
        }
    }

    public void resizeIfNeeded() {
        if (mc.getWindow() != null) {
            int fbWidth = mc.getWindow().getFramebufferWidth();
            int fbHeight = mc.getWindow().getFramebufferHeight();
            if (fbWidth > 0 && fbHeight > 0) {
                if (this.maskWidth != fbWidth || this.maskHeight != fbHeight) {
                    this.maskWidth = fbWidth;
                    this.maskHeight = fbHeight;
                    this.maskTexture = this.maskTexture.resize(this.maskWidth, this.maskHeight);
                    this.maskTexture.setFilter(9729);
                    this.maskTexture.setWrapMode(33071);
                    this.maskFramebuffer.attachTexture(36064, this.maskTexture);
                    this.maskFramebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
        }
    }

    public void ensureInitialized() {
        if (!this.initialized) {
            this.maskWidth = mc.getWindow().getFramebufferWidth();
            this.maskHeight = mc.getWindow().getFramebufferHeight();
            this.maskTexture.allocate(33323, this.maskWidth, this.maskHeight);
            this.maskTexture.setFilter(9729);
            this.maskTexture.setWrapMode(33071);
            this.maskFramebuffer.attachTexture(36064, this.maskTexture);
            this.maskFramebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.compositePasses = this.createCompositePasses();
            this.initialized = true;
        }
    }
}

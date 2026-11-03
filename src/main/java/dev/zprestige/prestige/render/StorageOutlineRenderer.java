package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.BlendToggleState;
import dev.zprestige.prestige.data.GlCapabilityState;
import dev.zprestige.prestige.data.ProgramUniforms;
import dev.zprestige.prestige.data.DepthFuncState;
import dev.zprestige.prestige.data.FramebufferBindState;
import dev.zprestige.prestige.data.ShaderBindState;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

public class StorageOutlineRenderer implements MinecraftAccess {
    private static final Shader OUTLINE_SHADER;
    private GlFramebuffer framebuffer;
    private GlTextureHandle colorTexture;
    private int width;
    private int height;
    private boolean initialized;
    private DrawPipeline maskDrawMode;
    private FullscreenPassChain outlinePass;
    private int quality;
    private float radius;
    private float fill;

    public StorageOutlineRenderer() {
        super();
        this.framebuffer = GlFramebuffer.create();
        this.colorTexture = GlTextureHandle.create(3553);
        this.initialized = false;
        this.quality = 5;
        this.radius = 15.0F;
        this.fill = 0.3F;
    }

    private void ensureInitialized() {
        if (mc.getWindow() != null) {
            int fbWidth = mc.getWindow().getFramebufferWidth();
            int fbHeight = mc.getWindow().getFramebufferHeight();
            if (fbWidth > 0 && fbHeight > 0) {
                if (!this.initialized) {
                    this.width = fbWidth;
                    this.height = fbHeight;
                    this.colorTexture.allocate(32856, this.width, this.height);
                    this.colorTexture.setFilter(9729);
                    this.colorTexture.setWrapMode(33071);
                    this.framebuffer.attachTexture(36064, this.colorTexture);
                    this.framebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                    this.maskDrawMode = new DrawPipeline(
                        VertexLayouts.POSITION_COLOR,
                        4,
                        false,
                        new RenderStateElement[]{
                            new FramebufferBindState(this.framebuffer.glId),
                            new ShaderBindState(CoreShaders.POS_COLOR),
                            new GlCapabilityState(2884, false),
                            new BlendToggleState(true, true),
                            new DepthFuncState(519, 515),
                            new GlCapabilityState(2929, false)
                        }
                    );
                    this.outlinePass = new FullscreenPassChain()
                        .addDynamicPass(
                            OUTLINE_SHADER,
                                (Consumer<GlRenderPass>)pass -> {
                                    GL13.glActiveTexture(33984);
                                    GL30.glBindTexture(3553, this.colorTexture.glId);
                                    GL30.glTexParameteri(3553, 33085, 4);
                                    GL30.glTexParameteri(3553, 10241, 9985);
                                    GL30.glGenerateMipmap(3553);
                                    GL30.glTexParameteri(3553, 10242, 33071);
                                    GL30.glTexParameteri(3553, 10243, 33071);
                                    ProgramUniforms.of(OUTLINE_SHADER.getProgramId())
                                        .setInt("texSampler", 0)
                                        .setInt("quality", this.quality)
                                        .setFloat("radius", this.radius)
                                        .setFloat("fill", this.fill)
                                        .setVec2("oneTexel", 1.0F / this.width, 1.0F / this.height);
                                },
                                (java.util.function.IntSupplier)RenderCore::getMainFramebufferId
                        );
                    this.initialized = true;
                } else if (this.width != fbWidth || this.height != fbHeight) {
                    this.width = fbWidth;
                    this.height = fbHeight;
                    this.colorTexture = this.colorTexture.resize(this.width, this.height);
                    this.colorTexture.setFilter(9729);
                    this.colorTexture.setWrapMode(33071);
                    this.framebuffer.attachTexture(36064, this.colorTexture);
                    this.framebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
        }
    }

    public DrawPipeline getMaskDrawMode() {
        this.ensureInitialized();
        return this.maskDrawMode;
    }

    public void renderOutline(int newQuality, float newRadius, float newFill) {
        this.ensureInitialized();
        if (this.outlinePass != null) {
            this.quality = newQuality;
            this.radius = newRadius;
            this.fill = newFill;
            boolean cullWasEnabled = GL30.glGetBoolean(2884);
            boolean blendWasEnabled = GL30.glGetBoolean(3042);
            boolean depthWasEnabled = GL30.glGetBoolean(2929);
            GL30.glDisable(2884);
            GL30.glEnable(3042);
            GL30.glBlendFunc(770, 771);
            GL30.glDisable(2929);
            try {
                this.outlinePass.render();
            } finally {
                if (depthWasEnabled) {
                    GL30.glEnable(2929);
                }

                if (cullWasEnabled) {
                    GL30.glEnable(2884);
                }

                if (!blendWasEnabled) {
                    GL30.glDisable(3042);
                }
            }

            this.framebuffer.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    static {
        OUTLINE_SHADER = ShaderCompiler.loadProgram("shaders/post.vert", "shaders/storage_outline.frag", false);
    }
}

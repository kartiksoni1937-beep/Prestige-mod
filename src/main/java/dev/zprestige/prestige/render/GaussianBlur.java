package dev.zprestige.prestige.render;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class GaussianBlur {
    public GlFramebuffer outputFramebuffer;
    public GlFramebuffer horizontalFramebuffer;
    public GlTextureHandle outputTexture;
    public GlTextureHandle horizontalTexture;
    private int width;
    private int height;
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int DIRECTION_LOCATION;
    private static final int KERNEL_WIDTH_LOCATION;
    private static final int SIGMA_LOCATION;
    private final float kernelWidth;
    private final float kernelSigma;
    private boolean texturesCreated;
    private final FullscreenPassChain passes;
    private long lastBlurFrame;
    private static final long NO_FRAME = Long.MIN_VALUE;

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public GaussianBlur(float radius, float sigma) {
        super();
        this.outputFramebuffer = GlFramebuffer.create();
        this.horizontalFramebuffer = GlFramebuffer.create();
        this.outputTexture = GlTextureHandle.create(3553);
        this.horizontalTexture = GlTextureHandle.create(3553);
        this.texturesCreated = false;
        this.lastBlurFrame = NO_FRAME;
        this.width = mc.getWindow().getFramebufferWidth();
        this.height = mc.getWindow().getFramebufferHeight();
        this.kernelWidth = radius;
        this.kernelSigma = sigma;
        this.createTargets();
        this.passes = new FullscreenPassChain().addPass(CoreShaders.GAUSSIAN_BLUR, horizontalPass -> {
            GL13.glActiveTexture(33984);
            GL30.glBindTexture(3553, RenderCore.getMainColorTextureId());
            GL20.glUniform2f(DIRECTION_LOCATION, 1.0F, 0.0F);
            GL20.glUniform1f(KERNEL_WIDTH_LOCATION, this.kernelWidth);
            GL20.glUniform1f(SIGMA_LOCATION, this.kernelSigma);
        }, this.horizontalFramebuffer.glId).addPass(CoreShaders.GAUSSIAN_BLUR, verticalPass -> {
            GL13.glActiveTexture(33984);
            GL30.glBindTexture(3553, this.horizontalTexture.glId);
            GL20.glUniform2f(DIRECTION_LOCATION, 0.0F, 1.0F);
            GL20.glUniform1f(KERNEL_WIDTH_LOCATION, this.kernelWidth);
            GL20.glUniform1f(SIGMA_LOCATION, this.kernelSigma);
        }, this.outputFramebuffer.glId);
    }

    private void createTargets() {
        if (this.texturesCreated) {
            this.outputTexture.close();
            this.horizontalTexture.close();
        }

        this.outputTexture = GlTextureHandle.create(3553);
        this.outputTexture.allocate(32849, this.width, this.height);
        this.outputTexture.setFilter(9729);
        this.outputTexture.setWrapMode(33071);
        this.horizontalTexture = GlTextureHandle.create(3553);
        this.horizontalTexture.allocate(32849, this.width, this.height);
        this.horizontalTexture.setFilter(9729);
        this.horizontalTexture.setWrapMode(33071);
        GL30.glBindFramebuffer(36160, this.outputFramebuffer.glId);
        GL30.glFramebufferTexture2D(36160, 36064, 3553, this.outputTexture.glId, 0);
        GL30.glBindFramebuffer(36160, this.horizontalFramebuffer.glId);
        GL30.glFramebufferTexture2D(36160, 36064, 3553, this.horizontalTexture.glId, 0);
        GL30.glBindFramebuffer(36160, 0);
        this.texturesCreated = true;
    }

    private boolean updateSize() {
        if (mc.getWindow() == null) {
            return false;
        } else {
            int newWidth = mc.getWindow().getFramebufferWidth();
            int newHeight = mc.getWindow().getFramebufferHeight();
            if (newWidth <= 0 || newHeight <= 0) {
                return false;
            } else if (this.width == newWidth && this.height == newHeight) {
                return false;
            } else {
                this.width = newWidth;
                this.height = newHeight;
                this.createTargets();
                return true;
            }
        }
    }

    public void blurIfNeeded(long frame) {
        if (this.updateSize() || this.lastBlurFrame != frame) {
            this.blur(frame);
        }
    }

    private void blur(long frame) {
        this.lastBlurFrame = frame;
        this.passes.render();
    }

    static {
        DIRECTION_LOCATION = CoreShaders.GAUSSIAN_BLUR.getUniformLocation("direction");
        KERNEL_WIDTH_LOCATION = CoreShaders.GAUSSIAN_BLUR.getUniformLocation("width");
        SIGMA_LOCATION = CoreShaders.GAUSSIAN_BLUR.getUniformLocation("sigma");
        CoreShaders.GAUSSIAN_BLUR.setUniformInt("texSampler", 0);
    }
}

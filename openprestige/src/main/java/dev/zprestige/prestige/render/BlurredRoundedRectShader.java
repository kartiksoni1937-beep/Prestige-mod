package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.RenderCacheUtil;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.util.Map;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class BlurredRoundedRectShader extends ShaderBase {
    private static final BlurredRoundedRectShader INSTANCE = new BlurredRoundedRectShader();
    private static final Map BLURS;
    private static final Map DRAW_MODES_BY_BLUR;
    private final int roundnessUniform;
    private final int invBlurTexSizeUniform;

    private BlurredRoundedRectShader() {
        super("shaders/rounded_texture.vert", "shaders/rounded_texture_blurred.frag");
        this.roundnessUniform = this.getShader().getUniformLocation("roundness");
        this.invBlurTexSizeUniform = this.getShader().getUniformLocation("invBlurTexSize");
        this.getShader().setUniformInt("texSampler", 0);
    }

    public static void drawBlurredRectCorners(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x, float y, float width, float height, int blurRadius, Vector4f radii) {
        GaussianBlur blur = (GaussianBlur)BLURS.computeIfAbsent(blurRadius, radiusKey -> new GaussianBlur(blurRadius, blurRadius * 2));
        INSTANCE.queueBlurredRect(buffers, context, matrix, blur, x, y, width, height, radii);
    }

    public static void drawBlurredRect(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x, float y, float width, float height, int blurRadius, float cornerRadius) {
        GaussianBlur blur = (GaussianBlur)BLURS.computeIfAbsent(blurRadius, radiusKey -> new GaussianBlur(blurRadius, blurRadius * 2));
        INSTANCE.queueBlurredRect(buffers, context, matrix, blur, x, y, width, height, new Vector4f(cornerRadius));
    }

    private void queueBlurredRect(BufferSource unusedBuffers, CameraMatrices unusedContext, Matrix4f matrix, GaussianBlur blur, float x, float y, float width, float height, Vector4f radii) {
        DrawPipeline drawMode = this.getDrawMode(blur, radii);
        ShapeRenderer.getDrawQueue()
            .enqueue(
                new CallbackDrawCommand(
                        drawMode,
                        (depth, builder) -> {
                            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, 0.0F})
                                .putFloats(new float[]{width, height}).putTransformedVertex(matrix, x + width, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, 0.0F})
                                .putFloats(new float[]{width, height}).putTransformedVertex(matrix, x + width, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, height})
                                .putFloats(new float[]{width, height}).putTransformedVertex(matrix, x, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, height})
                                .putFloats(new float[]{width, height})
                                .generateIndices(GlPrimitive.QUADS)
                                .resetBaseVertex();
                        }
                    )
            );
    }

    private DrawPipeline getDrawMode(GaussianBlur blur, Vector4f radii) {
        Vector4f roundedRadii = RenderCacheUtil.quantizeVector(radii);
        Map modesByRadii = (Map)DRAW_MODES_BY_BLUR.computeIfAbsent(blur, blurKey -> {
            return RenderCacheUtil.newLruCache(128);
        });
        DrawPipeline drawMode = (DrawPipeline)modesByRadii.get(roundedRadii);
        if (drawMode == null) {
            VertexLayout format = VertexLayouts.POSITION_UV_SIZE;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.onConfigure((Consumer<GlRenderPassBuilder>)passBuilder -> {
                blur.blurIfNeeded(RenderCore.getFrameCount());
            }), RenderStateElement.MAIN_TARGET, RenderStateElement.shader(this.getShader()), null, null, null, null};
            states[3] = RenderStateElement.capability(2884, true);
            states[4] = RenderStateElement.blend(true, true);
            states[5] = RenderStateElement.depthFunc(515, 515);
            states[6] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> {
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, blur.outputTexture.glId);
                GL20.glUniform2f(this.invBlurTexSizeUniform, 1.0F / blur.getWidth(), 1.0F / blur.getHeight());
                GL20.glUniform4f(this.roundnessUniform, roundedRadii.x, roundedRadii.y, roundedRadii.z, roundedRadii.w);
            });
            drawMode = new DrawPipeline(format, 4, false, states);
            modesByRadii.put(roundedRadii, drawMode);
        }

        return drawMode;
    }

    static {
        BLURS = RenderCacheUtil.newLruCache(4);
        DRAW_MODES_BY_BLUR = RenderCacheUtil.newLruCache(4);
    }
}

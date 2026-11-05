package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.BlendFuncState;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.RenderCacheUtil;
import dev.zprestige.prestige.math.TexturedDrawModeKey;
import dev.zprestige.prestige.type.BlendMode;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class RoundedTextureShader extends ShaderBase {
    private static final RoundedTextureShader INSTANCE = new RoundedTextureShader();
    private static final Map DRAW_MODE_CACHE = RenderCacheUtil.newLruCache(256);
    private final int tintModeUniform;
    private final int roundnessUniform;

    private RoundedTextureShader() {
        super("shaders/rounded_texture.vert", "shaders/rounded_texture.frag");
        this.tintModeUniform = this.getShader().getUniformLocation("tintMode");
        this.roundnessUniform = this.getShader().getUniformLocation("roundness");
        this.getShader().setUniformInt("texSampler", 0);
    }

    public static void drawTexture(BufferSource buffers, CameraMatrices context, Matrix4f matrix, GlTextureHandle texture, float x, float y, float width, float height, Color color) {
        INSTANCE.queueTexture(buffers, context, matrix, texture, x, y, width, height, new Vector4f(0.0F), color, BlendMode.MULTIPLY);
    }

    public static void drawTextureQuad(BufferSource buffers, CameraMatrices context, GlTextureHandle texture, float x, float y, float width, float height, Matrix4f matrix, Color color) {
        INSTANCE.queueTexture(buffers, context, matrix, texture, x, y, width, height, new Vector4f(0.0F), color, BlendMode.MULTIPLY);
    }

    public static void drawWorldTexture(BufferSource buffers, CameraMatrices context, Matrix4f matrix, GlTextureHandle texture, float x1, float y1, float z1, float x2, float y2, float z2, Color color, float radius) {
        INSTANCE.writeWorldTexture(buffers, context, matrix, texture, x1, y1, z1, x2, y2, z2, color.getRGB(), new Vector4f(radius), BlendMode.MULTIPLY);
    }

    public static void drawWorldTextureGradient(BufferSource buffers, CameraMatrices context, Matrix4f matrix, GlTextureHandle texture, float x1, float y1, float z1, float x2, float y2, float z2, Color bottomLeft, Color bottomRight, Color topRight, Color topLeft, float radius, boolean depthTest) {
        INSTANCE.writeWorldTextureGradient(buffers, context, matrix, texture, x1, y1, z1, x2, y2, z2, bottomLeft.getRGB(), bottomRight.getRGB(), topRight.getRGB(), topLeft.getRGB(), new Vector4f(radius), depthTest, BlendMode.MULTIPLY);
    }

    private void queueTexture(BufferSource unusedBuffers, CameraMatrices unusedContext, Matrix4f matrix, GlTextureHandle texture, float x, float y, float width, float height, Vector4f radii, Color color, BlendMode blendMode) {
        DrawPipeline drawMode = this.getDrawMode(texture, radii, blendMode, true);
        ((DrawCommandQueue)ShapeRenderer.drawQueue.get())
            .enqueue(
                new CallbackDrawCommand(
                        drawMode,
                        (depth, builder) -> {
                            int argb = color.getRGB();
                            int abgr = (argb >> 24 & 0xFF) << 24 | (argb & 0xFF) << 16 | (argb >> 8 & 0xFF) << 8 | (argb >> 16 & 0xFF);
                            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x + width, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x + width, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, height})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, height})
                                .putFloats(new float[]{width, height}).putColor(abgr).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }
                    )
            );
    }

    private void queueTextureRegion(BufferSource unusedBuffers, CameraMatrices unusedContext, Matrix4f matrix, GlTextureHandle texture, float x, float y, float width, float height, float u, float v, float regionWidth, float regionHeight, Vector4f radii, Color color, BlendMode blendMode) {
        DrawPipeline drawMode = this.getDrawMode(texture, radii, blendMode, true);
        ((DrawCommandQueue)ShapeRenderer.drawQueue.get())
            .enqueue(
                new CallbackDrawCommand(
                        drawMode,
                        (depth, builder) -> {
                            int argb = color.getRGB();
                            int abgr = (argb >> 24 & 0xFF) << 24 | (argb & 0xFF) << 16 | (argb >> 8 & 0xFF) << 8 | (argb >> 16 & 0xFF);
                            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{u, v})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x + width, y + height, ((java.lang.Float)depth))
                                .putFloats(new float[]{u + regionWidth, v})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x + width, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{u + regionWidth, v + regionHeight})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x, y, ((java.lang.Float)depth))
                                .putFloats(new float[]{u, v + regionHeight})
                                .putFloats(new float[]{width, height}).putColor(abgr).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }
                    )
            );
    }

    public static void drawTextureRegion(BufferSource buffers, CameraMatrices context, Matrix4f matrix, GlTextureHandle texture, float x, float y, float width, float height, float u, float v, float regionWidth, float regionHeight, Vector4f radii, Color color) {
        INSTANCE.queueTextureRegion(buffers, context, matrix, texture, x, y, width, height, u, v, regionWidth, regionHeight, radii, color, BlendMode.MULTIPLY);
    }

    private DrawPipeline getDrawMode(GlTextureHandle texture, Vector4f radii, BlendMode blendMode, boolean depthTest) {
        TexturedDrawModeKey cacheKey = new TexturedDrawModeKey(texture, radii, blendMode, depthTest);
        return (DrawPipeline)DRAW_MODE_CACHE.computeIfAbsent(cacheKey, mapKey -> {
            VertexLayout format = VertexLayouts.POSITION_UV_SIZE_COLOR;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.MAIN_TARGET, RenderStateElement.shader(this.getShader()), null, null, null, null};
            states[2] = RenderStateElement.capability(2929, depthTest);
            states[3] = RenderStateElement.depthMask(true);
            states[4] = new BlendFuncState(true, true, 770, 771, 1, 771);
            states[5] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> {
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, texture.glId);
                GL20.glUniform1i(this.tintModeUniform, blendMode.ordinal());
                GL20.glUniform4f(this.roundnessUniform, radii.x, radii.y, radii.z, radii.w);
            });
            return new DrawPipeline(format, 4, false, states);
        });
    }

    private void writeWorldTexture(BufferSource buffers, CameraMatrices unusedContext, Matrix4f matrix, GlTextureHandle texture, float x1, float y1, float z1, float x2, float y2, float z2, int argb, Vector4f radii, BlendMode blendMode) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        int abgr = (argb >> 24 & 0xFF) << 24 | (argb & 0xFF) << 16 | (argb >> 8 & 0xFF) << 8 | (argb >> 16 & 0xFF);
        ((MappedMeshBuilder)((MappedMeshBuilder)this.getWorldBuilder(buffers, texture, radii, blendMode, true).beginShape()).appendTransformedPosition(matrix, x1, y2, z2)
            .appendFloats(new float[]{0.0F, 0.0F})
            .appendFloats(new float[]{width, height}).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2)
            .appendFloats(new float[]{width, 0.0F})
            .appendFloats(new float[]{width, height}).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1)
            .appendFloats(new float[]{width, height})
            .appendFloats(new float[]{width, height}).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z1)
            .appendFloats(new float[]{0.0F, height})
            .appendFloats(new float[]{width, height}).appendInt(abgr).generateIndices(GlPrimitive.QUADS)).clearBaseVertex();
    }

    private void writeWorldTextureGradient(BufferSource buffers, CameraMatrices unusedContext, Matrix4f matrix, GlTextureHandle texture, float x1, float y1, float z1, float x2, float y2, float z2, int bottomLeftArgb, int bottomRightArgb, int topRightArgb, int topLeftArgb, Vector4f radii, boolean depthTest, BlendMode blendMode) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        int bottomLeftAbgr = ShapeRenderer.argbToAbgr(bottomLeftArgb);
        int bottomRightAbgr = ShapeRenderer.argbToAbgr(bottomRightArgb);
        int topRightAbgr = ShapeRenderer.argbToAbgr(topRightArgb);
        int topLeftAbgr = ShapeRenderer.argbToAbgr(topLeftArgb);
        ((MappedMeshBuilder)((MappedMeshBuilder)this.getWorldBuilder(buffers, texture, radii, blendMode, depthTest).beginShape()).appendTransformedPosition(matrix, x1, y2, z2)
            .appendFloats(new float[]{0.0F, 0.0F})
            .appendFloats(new float[]{width, height}).appendInt(bottomLeftAbgr).appendTransformedPosition(matrix, x2, y2, z2)
            .appendFloats(new float[]{width, 0.0F})
            .appendFloats(new float[]{width, height}).appendInt(bottomRightAbgr).appendTransformedPosition(matrix, x2, y1, z1)
            .appendFloats(new float[]{width, height})
            .appendFloats(new float[]{width, height}).appendInt(topRightAbgr).appendTransformedPosition(matrix, x1, y1, z1)
            .appendFloats(new float[]{0.0F, height})
            .appendFloats(new float[]{width, height}).appendInt(topLeftAbgr).generateIndices(GlPrimitive.QUADS)).clearBaseVertex();
    }

    private MappedMeshBuilder getWorldBuilder(BufferSource buffers, GlTextureHandle texture, Vector4f radii, BlendMode blendMode, boolean depthTest) {
        TexturedDrawModeKey cacheKey = new TexturedDrawModeKey(texture, radii, blendMode, depthTest);
        return buffers.getBuffer((DrawPipeline)DRAW_MODE_CACHE.computeIfAbsent(cacheKey, mapKey -> {
            VertexLayout format = VertexLayouts.POSITION_UV_SIZE_COLOR;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.MAIN_TARGET, RenderStateElement.shader(this.getShader()), null, null, null, null};
            states[2] = RenderStateElement.capability(2929, depthTest);
            states[3] = RenderStateElement.depthMask(false, true);
            states[4] = new BlendFuncState(true, true, 770, 771, 1, 771);
            states[5] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> {
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, texture.glId);
                GL20.glUniform1i(this.tintModeUniform, blendMode.ordinal());
                GL20.glUniform4f(this.roundnessUniform, radii.x, radii.y, radii.z, radii.w);
            });
            return new DrawPipeline(format, 4, false, states);
        }));
    }
}

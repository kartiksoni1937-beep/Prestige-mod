package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.math.RenderCacheUtil;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.util.Map;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

public class RoundedRectShader extends ShaderBase {
    private static final RoundedRectShader INSTANCE = new RoundedRectShader();
    private static final Map DRAW_MODE_CACHE;
    private final int roundnessUniform;

    private RoundedRectShader() {
        super("shaders/rounded_pos_color.vert", "shaders/rounded_pos_color.frag");
        this.roundnessUniform = this.getShader().getUniformLocation("roundness");
    }

    public static void drawRoundedRect(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x, float y, float width, float height, Color color, float radius) {
        INSTANCE.queueRoundedRect(buffers, context, matrix, x, y, width, height, color.getRGB(), new Vector4f(radius));
    }

    private void queueRoundedRect(BufferSource unusedBuffers, CameraMatrices unusedContext, Matrix4f matrix, float x, float y, float width, float height, int argb, Vector4f radii) {
        DrawPipeline drawMode = this.getDrawMode(radii);
        ShapeRenderer.getDrawQueue()
            .enqueue(
                new CallbackDrawCommand(
                        drawMode,
                        (depth, builder) -> {
                            int abgr = ShapeRenderer.argbToAbgr(argb);
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

    public DrawPipeline getDrawMode(Vector4f radii) {
        Vector4f roundedRadii = RenderCacheUtil.quantizeVector(radii);
        DrawPipeline drawMode = (DrawPipeline)DRAW_MODE_CACHE.get(roundedRadii);
        if (drawMode == null) {
            VertexLayout format = VertexLayouts.POSITION_UV_SIZE_COLOR;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.MAIN_TARGET, RenderStateElement.shader(this.getShader()), null, null, null, null};
            states[2] = RenderStateElement.capability(2884, true);
            states[3] = RenderStateElement.blend(true, true);
            states[4] = RenderStateElement.depthFunc(515, 515);
            states[5] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> GL20.glUniform4f(this.roundnessUniform, roundedRadii.x, roundedRadii.y, roundedRadii.z, roundedRadii.w));
            drawMode = new DrawPipeline(format, 4, false, states);
            DRAW_MODE_CACHE.put(roundedRadii, drawMode);
        }

        return drawMode;
    }

    static {
        DRAW_MODE_CACHE = RenderCacheUtil.newLruCache(128);
    }
}

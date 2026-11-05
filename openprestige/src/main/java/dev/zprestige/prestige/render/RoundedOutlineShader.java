package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.RoundedOutlineKey;
import dev.zprestige.prestige.math.RenderCacheUtil;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.util.Map;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

public class RoundedOutlineShader extends ShaderBase {
    private static final RoundedOutlineShader INSTANCE = new RoundedOutlineShader();
    private static final Map DRAW_MODE_CACHE;
    private static final Matrix4f TEMP_MATRIX = new Matrix4f();
    private static final ThreadLocal RADII_SCRATCH = ThreadLocal.withInitial(Vector4f::new);
    private final int roundnessUniform;
    private final int thicknessUniform;

    private RoundedOutlineShader() {
        super("shaders/rounded_pos_color.vert", "shaders/rounded_outline_pos_color.frag");
        this.roundnessUniform = this.getShader().getUniformLocation("roundness");
        this.thicknessUniform = this.getShader().getUniformLocation("outlineThickness");
    }

    public static void drawRoundedOutline(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x, float y, float width, float height, Color color, float radius, float thickness) {
        INSTANCE.queueOutline(buffers, context, matrix, x, y, width, height, color.getRGB(), ((Vector4f)RADII_SCRATCH.get()).set(radius), thickness);
    }

    private void queueOutline(BufferSource unusedBuffers, CameraMatrices unusedContext, Matrix4f matrix, float x, float y, float width, float height, int argb, Vector4f radii, float thickness) {
        DrawPipeline drawMode = this.getDrawMode(radii, thickness);
        float radiusX = radii.x;
        float radiusY = radii.y;
        float radiusZ = radii.z;
        float radiusW = radii.w;
        ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(drawMode, (depth, builder) -> {
            int abgrColor = ShapeRenderer.argbToAbgr(argb);
            float edgeSize = 2.0F * thickness + Math.max(Math.max(radiusX, radiusY), Math.max(radiusZ, radiusW)) + 2.0F;
            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape();
            if (edgeSize * 2.0F >= Math.min(width, height)) {
                addSubQuad(((dev.zprestige.prestige.world.MeshBuilder)builder), matrix, ((java.lang.Float)depth), abgrColor, width, height, x, y, width, height, 0.0F, 0.0F, width, height);
            } else {
                addSubQuad(((dev.zprestige.prestige.world.MeshBuilder)builder), matrix, ((java.lang.Float)depth), abgrColor, width, height, x, y, width, edgeSize, 0.0F, height - edgeSize, width, height);
                addSubQuad(((dev.zprestige.prestige.world.MeshBuilder)builder), matrix, ((java.lang.Float)depth), abgrColor, width, height, x, y + height - edgeSize, width, edgeSize, 0.0F, 0.0F, width, edgeSize);
                addSubQuad(((dev.zprestige.prestige.world.MeshBuilder)builder), matrix, ((java.lang.Float)depth), abgrColor, width, height, x, y + edgeSize, edgeSize, height - 2.0F * edgeSize, 0.0F, edgeSize, edgeSize, height - edgeSize);
                addSubQuad(((dev.zprestige.prestige.world.MeshBuilder)builder), matrix, ((java.lang.Float)depth), abgrColor, width, height, x + width - edgeSize, y + edgeSize, edgeSize, height - 2.0F * edgeSize, width - edgeSize, edgeSize, width, height - edgeSize);
            }

            ((dev.zprestige.prestige.world.MeshBuilder)builder).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
        }));
    }

    private static void addSubQuad(MeshBuilder builder, Matrix4f matrix, float depth, int abgr, float fullWidth, float fullHeight, float x, float y, float width, float height, float u0, float v0, float u1, float v1) {
        builder.putTransformedVertex(matrix, x, y + height, depth)
            .putFloats(new float[]{u0, v0})
            .putFloats(new float[]{fullWidth, fullHeight}).putColor(abgr).putTransformedVertex(matrix, x + width, y + height, depth)
            .putFloats(new float[]{u1, v0})
            .putFloats(new float[]{fullWidth, fullHeight}).putColor(abgr).putTransformedVertex(matrix, x + width, y, depth)
            .putFloats(new float[]{u1, v1})
            .putFloats(new float[]{fullWidth, fullHeight}).putColor(abgr).putTransformedVertex(matrix, x, y, depth)
            .putFloats(new float[]{u0, v1})
            .putFloats(new float[]{fullWidth, fullHeight}).putColor(abgr);
    }

    public DrawPipeline getDrawMode(Vector4f radii, float thickness) {
        RoundedOutlineKey key = new RoundedOutlineKey(RenderCacheUtil.quantizeVector(radii), thickness);
        DrawPipeline drawMode = (DrawPipeline)DRAW_MODE_CACHE.get(key);
        if (drawMode == null) {
            VertexLayout format = VertexLayouts.POSITION_UV_SIZE_COLOR;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.MAIN_TARGET, RenderStateElement.shader(this.getShader()), null, null, null, null};
            states[2] = RenderStateElement.capability(2884, true);
            states[3] = RenderStateElement.blend(true, true);
            states[4] = RenderStateElement.depthFunc(515, 515);
            states[5] = RenderStateElement.onApply((Consumer<GlRenderPass>)pass -> {
                GL20.glUniform4f(this.roundnessUniform, key.cornerRadii().x, key.cornerRadii().y, key.cornerRadii().z, key.cornerRadii().w);
                GL20.glUniform1f(this.thicknessUniform, key.outlineThickness());
            });
            drawMode = new DrawPipeline(format, 4, false, states);
            DRAW_MODE_CACHE.put(key, drawMode);
        }

        return drawMode;
    }

    static {
        DRAW_MODE_CACHE = RenderCacheUtil.newLruCache(128);
    }
}

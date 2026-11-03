package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class ShapeRenderer {
    public static AtomicReference drawQueue = new AtomicReference();
    private static final ThreadLocal SCRATCH_MATRIX = ThreadLocal.withInitial(Matrix4f::new);
    private static final Matrix4f TEMP_MATRIX = new Matrix4f();
    private static final ThreadLocal SCRATCH_VECTOR = ThreadLocal.withInitial(Vector3d::new);

    public static void initDrawQueue() {
        if (drawQueue.get() == null) {
            drawQueue.compareAndSet(null, new DrawCommandQueue());
        }
    }

    public static DrawCommandQueue getDrawQueue() {
        DrawCommandQueue queue = (DrawCommandQueue)drawQueue.get();
        if (queue == null) {
            initDrawQueue();
            queue = (DrawCommandQueue)drawQueue.get();
        }

        return queue;
    }

    public static void flushDrawQueue() {
        getDrawQueue().flush(Render2D.currentContext);
    }

    public static void drawPath(CameraMatrices context, BufferSource buffers, ArrayList points, Color color) {
        if (!points.isEmpty()) {
            Matrix4f matrix = ((Matrix4f)SCRATCH_MATRIX.get()).identity();
            MappedMeshBuilder builder = buffers.getBuffer(DrawPipelines.LINES_NO_DEPTH);
            int abgr = toAbgr(color);
            builder.beginShape();
            Vector3d relative = (Vector3d)SCRATCH_VECTOR.get();

            for (Vec3d point : (Iterable<Vec3d>)(points)) {
                RenderCore.toCameraRelative(context, point.x, point.y, point.z, relative);
                builder.appendTransformedPosition(matrix, (float)relative.x, (float)relative.y, (float)relative.z).appendInt(abgr);
            }

            ((MappedMeshBuilder)builder.generateIndices(GlPrimitive.DEBUG_LINE_STRIP)).clearBaseVertex();
        }
    }

    public static void drawDisc(CameraMatrices context, BufferSource buffers, float x, float y, float z, float radius, Color centerColor, Color edgeColor) {
        drawDisc(context, buffers, ((Matrix4f)SCRATCH_MATRIX.get()).identity(), x, y, z, radius, centerColor, edgeColor);
    }

    public static void drawDisc(CameraMatrices context, BufferSource buffers, Matrix4f matrix, float x, float y, float z, float radius, Color centerColor, Color edgeColor) {
        double coordX = x;
        double coordY = y;
        double coordZ = z;
        Vector3d center = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        MappedMeshBuilder builder = buffers.getBuffer(DrawPipelines.TRIANGLES_NO_DEPTH);
        float cx = (float)center.x;
        float cy = (float)center.y;
        float cz = (float)center.z;
        int centerAbgr = toAbgr(centerColor);
        int edgeAbgr = toAbgr(edgeColor);
        builder.beginShape();
        builder.appendTransformedPosition(matrix, cx, cy, cz).appendInt(centerAbgr);

        for (int i = 0; i <= 100; i++) {
            float angle = (float)((Math.PI * 2) * i / 100.0);
            builder.appendTransformedPosition(matrix, cx + radius * ((float)Math.cos(angle)), cy, cz + radius * ((float)Math.sin(angle))).appendInt(edgeAbgr);
        }

        ((MappedMeshBuilder)builder.generateIndices(GlPrimitive.TRIANGLES_FAN)).clearBaseVertex();
    }

    public static void drawGlowRing(CameraMatrices context, BufferSource buffers, float x, float y, float z, float radius, float thickness, float glowWidth, Color color, Color glowColor) {
        double coordX = x;
        double coordY = y;
        double coordZ = z;
        Vector3d center = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        Matrix4f matrix = ((Matrix4f)SCRATCH_MATRIX.get()).identity();
        MappedMeshBuilder builder = buffers.getBuffer(DrawPipelines.TRIANGLES_NO_DEPTH);
        float cx = (float)center.x;
        float cy = (float)center.y;
        float cz = (float)center.z;
        int abgr = toAbgr(color);
        int glowAbgr = toAbgr(glowColor) & 16777215;
        builder.beginShape();

        for (int i = 0; i <= 100; i++) {
            float angle = (float)((Math.PI * 2) * i / 100.0);
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            builder.appendTransformedPosition(matrix, cx + (radius + thickness / 2.0F + glowWidth) * cos, cy, cz + (radius + thickness / 2.0F + glowWidth) * sin).appendInt(glowAbgr);
            builder.appendTransformedPosition(matrix, cx + (radius + thickness / 2.0F) * cos, cy, cz + (radius + thickness / 2.0F) * sin).appendInt(abgr);
            builder.appendTransformedPosition(matrix, cx + (radius - thickness / 2.0F) * cos, cy, cz + (radius - thickness / 2.0F) * sin).appendInt(abgr);
            builder.appendTransformedPosition(matrix, cx + (radius - thickness / 2.0F - glowWidth) * cos, cy, cz + (radius - thickness / 2.0F - glowWidth) * sin).appendInt(glowAbgr);
        }

        builder.ensureIndexCapacity(7200);

        for (int segment = 0; segment < 100; segment++) {
            int base = segment * 4;
            int nextBase = (segment + 1) % 101 * 4;
            builder.appendIndices(new int[]{base, base + 1, nextBase + 1, nextBase + 1, nextBase, base});
            builder.appendIndices(new int[]{base + 1, base + 2, nextBase + 2, nextBase + 2, nextBase + 1, base + 1});
            builder.appendIndices(new int[]{base + 2, base + 3, nextBase + 3, nextBase + 3, nextBase + 2, base + 2});
        }

        builder.clearBaseVertex();
    }

    public static void drawBoxOutline(CameraMatrices context, BufferSource buffers, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Color color) {
        double coordX = minX;
        double coordY = minY;
        double coordZ = minZ;
        Vector3d min = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        coordX = maxX;
        coordY = maxY;
        coordZ = maxZ;
        Vector3d max = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        Matrix4f matrix = ((Matrix4f)SCRATCH_MATRIX.get()).identity();
        MappedMeshBuilder builder = buffers.getBuffer(DrawPipelines.LINES_NO_DEPTH);
        int abgr = toAbgr(color);
        float x1 = (float)min.x;
        float y1 = (float)min.y;
        float z1 = (float)min.z;
        float x2 = (float)max.x;
        float y2 = (float)max.y;
        float z2 = (float)max.z;
        ((MappedMeshBuilder)((MappedMeshBuilder)builder.beginShape()).appendTransformedPosition(matrix, x1, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z1).appendInt(abgr).generateIndices(GlPrimitive.DEBUG_LINE_STRIP)).clearBaseVertex();
    }

    public static void drawFilledBox(CameraMatrices context, BufferSource buffers, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Color color) {
        double coordX = minX;
        double coordY = minY;
        double coordZ = minZ;
        Vector3d min = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        coordX = maxX;
        coordY = maxY;
        coordZ = maxZ;
        Vector3d max = RenderCore.toCameraRelative(context, coordX, coordY, coordZ);
        Matrix4f matrix = ((Matrix4f)SCRATCH_MATRIX.get()).identity();
        MappedMeshBuilder builder = buffers.getBuffer(DrawPipelines.TRIANGLES_NO_DEPTH);
        int abgr = toAbgr(color);
        float x1 = (float)min.x;
        float y1 = (float)min.y;
        float z1 = (float)min.z;
        float x2 = (float)max.x;
        float y2 = (float)max.y;
        float z2 = (float)max.z;
        ((MappedMeshBuilder)((MappedMeshBuilder)builder.beginShape()).appendTransformedPosition(matrix, x1, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x1, y1, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x1, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z1).appendInt(abgr).appendTransformedPosition(matrix, x2, y2, z2).appendInt(abgr).appendTransformedPosition(matrix, x2, y1, z2).appendInt(abgr).generateIndices(GlPrimitive.QUADS)).clearBaseVertex();
    }

    public static void drawTriangle(Matrix4f matrix, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_DEPTH_TESTED, (depth, builder) -> {
            int abgr = argbToAbgr(argb);
            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x1, y1, ((java.lang.Float)depth)).putColor(abgr).putTransformedVertex(matrix, x2, y2, ((java.lang.Float)depth)).putColor(abgr).putTransformedVertex(matrix, x3, y3, ((java.lang.Float)depth)).putColor(abgr).generateIndices(GlPrimitive.TRIANGLES).resetBaseVertex();
        }));
    }

    public static void drawRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color) {
        drawRectArgb(matrix, x1, y1, x2, y2, color.getRGB());
    }

    public static void drawRectArgb(Matrix4f matrix, float x1, float y1, float x2, float y2, int argb) {
        getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_DEPTH_TESTED, (depth, builder) -> {
            int abgr = argbToAbgr(argb);
            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x1, y2, ((java.lang.Float)depth)).putColor(abgr).putTransformedVertex(matrix, x2, y2, ((java.lang.Float)depth)).putColor(abgr).putTransformedVertex(matrix, x2, y1, ((java.lang.Float)depth)).putColor(abgr).putTransformedVertex(matrix, x1, y1, ((java.lang.Float)depth)).putColor(abgr).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
        }));
    }

    public static void drawRoundedRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, Vector4f radii) {
        getDrawQueue()
            .enqueue(
                new CallbackDrawCommand(
                        DrawPipelines.getRoundedRect(radii),
                        (depth, builder) -> {
                            int abgr = argbToAbgr(color.getRGB());
                            float width = x2 - x1;
                            float height = y2 - y1;
                            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x1, y2, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x2, y2, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x2, y1, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, height})
                                .putFloats(new float[]{width, height}).putColor(abgr).putTransformedVertex(matrix, x1, y1, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, height})
                                .putFloats(new float[]{width, height}).putColor(abgr).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }
                    )
            );
    }

    public static void drawGradientRoundedRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color bottomLeft, Color bottomRight, Color topRight, Color topLeft, Vector4f radii) {
        getDrawQueue()
            .enqueue(
                new CallbackDrawCommand(
                        DrawPipelines.getRoundedRect(radii),
                        (depth, builder) -> {
                            int bottomLeftAbgr = toAbgr(bottomLeft);
                            int bottomRightAbgr = toAbgr(bottomRight);
                            int topRightAbgr = toAbgr(topRight);
                            int topLeftAbgr = toAbgr(topLeft);
                            float width = x2 - x1;
                            float height = y2 - y1;
                            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape().putTransformedVertex(matrix, x1, y2, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(bottomLeftAbgr).putTransformedVertex(matrix, x2, y2, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, 0.0F})
                                .putFloats(new float[]{width, height}).putColor(bottomRightAbgr).putTransformedVertex(matrix, x2, y1, ((java.lang.Float)depth))
                                .putFloats(new float[]{width, height})
                                .putFloats(new float[]{width, height}).putColor(topRightAbgr).putTransformedVertex(matrix, x1, y1, ((java.lang.Float)depth))
                                .putFloats(new float[]{0.0F, height})
                                .putFloats(new float[]{width, height}).putColor(topLeftAbgr).generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }
                    )
            );
    }

    public static void drawCircle(Matrix4f matrix, float centerX, float centerY, float radius, int argb) {
        getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_DEPTH_TESTED, (depth, builder) -> {
            int abgr = argbToAbgr(argb);
            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape();
            ((dev.zprestige.prestige.world.MeshBuilder)builder).putTransformedVertex(matrix, centerX, centerY, ((java.lang.Float)depth)).putColor(abgr);
            int segments = Math.max(12, (int)(radius * 2.0F));

            for (int i = 0; i < segments; i++) {
                float angle = (float)((Math.PI * 2) * i / segments);
                ((dev.zprestige.prestige.world.MeshBuilder)builder).putTransformedVertex(matrix, (float)(centerX + Math.sin(angle) * radius), (float)(centerY + Math.cos(angle) * radius), ((java.lang.Float)depth)).putColor(abgr);
            }

            ((dev.zprestige.prestige.world.MeshBuilder)builder).generateIndices(GlPrimitive.TRIANGLES_FAN).resetBaseVertex();
        }));
    }

    public static void drawRing(Matrix4f matrix, float centerX, float centerY, float radius, float thickness, int argb) {
        getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_DEPTH_TESTED, (depth, builder) -> {
            int abgr = argbToAbgr(argb);
            float halfThickness = thickness / 2.0F;
            float outerRadius = radius + halfThickness;
            float innerRadius = Math.max(0.0F, radius - halfThickness);
            ((dev.zprestige.prestige.world.MeshBuilder)builder).beginShape();

            for (int i = 0; i <= 100; i++) {
                float angle = (float)((Math.PI * 2) * i / 100);
                float cos = (float)Math.cos(angle);
                float sin = (float)Math.sin(angle);
                float outerX = centerX + outerRadius * cos;
                float outerY = centerY + outerRadius * sin;
                float innerX = centerX + innerRadius * cos;
                float innerY = centerY + innerRadius * sin;
                ((dev.zprestige.prestige.world.MeshBuilder)builder).putTransformedVertex(matrix, outerX, outerY, ((java.lang.Float)depth)).putColor(abgr);
                ((dev.zprestige.prestige.world.MeshBuilder)builder).putTransformedVertex(matrix, innerX, innerY, ((java.lang.Float)depth)).putColor(abgr);
            }

            ((dev.zprestige.prestige.world.MeshBuilder)builder).generateIndices(GlPrimitive.QUAD_STRIP).resetBaseVertex();
        }));
    }

    public static void drawRoundedRectOutline(BufferSource buffers, CameraMatrices context, Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, float radius, float thickness) {
        RoundedOutlineShader.drawRoundedOutline(buffers, context, matrix, x1, y1, x2 - x1, y2 - y1, color, radius, thickness);
    }

    public static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, alpha);
    }

    public static int toAbgr(Color color) {
        return argbToAbgr(color.getRGB());
    }

    public static int argbToAbgr(int argb) {
        return (argb >> 24 & 0xFF) << 24 | (argb & 0xFF) << 16 | (argb >> 8 & 0xFF) << 8 | (argb >> 16 & 0xFF);
    }
}

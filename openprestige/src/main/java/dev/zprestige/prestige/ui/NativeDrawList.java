package dev.zprestige.prestige.ui;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.render.CallbackDrawCommand;
import dev.zprestige.prestige.render.CoreShaders;
import dev.zprestige.prestige.render.DrawPipeline;
import dev.zprestige.prestige.render.DrawPipelines;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.RoundedOutlineShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.BlendMode;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Android-safe replacement for the ImGui draw list the original ClickGUI used.
 *
 * <p>The original UI (ui/Ui.java and ui/*Page.java) is immediate mode: every frame it emits primitives in screen pixels
 * into an {@code ImDrawList}. This class keeps that contract but records the primitives and replays them through the
 * client's own renderer ({@link ShapeRenderer}'s command queue, the MSDF font and the rounded-rect/outline/texture
 * shaders), so the ClickGUI no longer needs the desktop-only imgui-java natives, GLFW or a GL3 backend.
 *
 * <p>Coordinates are GUI-scale pixels with the origin in the top-left corner, exactly what {@code ImDrawList} received
 * after {@link Lib#pt(float, float)}. Clipping is carried per command as a scissor rectangle and applied by
 * {@code DrawCommandQueue} at flush time, which keeps scrolling lists and popups correct.
 *
 * <p>All methods are render-thread only and never throw into the game loop; if the shader package is unavailable
 * (unsupported GL) {@link #flush(Matrix4f)} is a no-op and the rest of the client keeps running.
 */
public final class NativeDrawList {
    private static final int RECT = 0, ROUND = 1, OUTLINE = 2, LINE = 3, CIRCLE = 4, RING = 5, TEXT = 6, IMAGE = 7, QUAD = 8;

    private static final class Command {
        int type;
        float x, y, w, h;
        float a, b, c, d;
        int color;
        long texture;
        String text;
        float[] radii;
        float[] quad;
        float[] clip;
    }

    private final List<Command> commands = new ArrayList<>();
    private final ArrayList<float[]> clipStack = new ArrayList<>();
    private float[] clip;
    /** GlTextureHandle registry: the pages pass opaque long texture ids around (the ImTextureID equivalent). */
    private static final Map<Long, GlTextureHandle> TEXTURES = new java.util.HashMap<>();
    private static final AtomicLong NEXT_TEXTURE = new AtomicLong(1);

    public static long registerTexture(GlTextureHandle handle) {
        if (handle == null) return 0L;
        long id = NEXT_TEXTURE.getAndIncrement();
        TEXTURES.put(id, handle);
        return id;
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    /** Number of primitives recorded for the current frame (used by the offline GUI verification). */
    public int size() {
        return commands.size();
    }

    public void clear() {
        commands.clear();
        clipStack.clear();
        clip = null;
    }

    private Command add(int type) {
        Command c = new Command();
        c.type = type;
        c.clip = clip;
        commands.add(c);
        return c;
    }

    private float[] currentClip() {
        return clip;
    }

    private int nextDepthSlot() {
        return commands.size();
    }

    public void pushClipRect(float x1, float y1, float x2, float y2, boolean intersect) {
        float[] rect = new float[]{x1, y1, Math.max(x1, x2), Math.max(y1, y2)};
        if (intersect && clip != null) {
            rect[0] = Math.max(rect[0], clip[0]);
            rect[1] = Math.max(rect[1], clip[1]);
            rect[2] = Math.min(rect[2], clip[2]);
            rect[3] = Math.min(rect[3], clip[3]);
            if (rect[2] < rect[0]) rect[2] = rect[0];
            if (rect[3] < rect[1]) rect[3] = rect[1];
        }
        clipStack.add(clip);
        clip = rect;
    }

    public void popClipRect() {
        if (!clipStack.isEmpty()) clip = clipStack.remove(clipStack.size() - 1);
    }

    /** Filled rectangle with uniform corner rounding. */
    public void addRectFilled(float x1, float y1, float x2, float y2, int color, float rounding, int flags) {
        addRectFilled(x1, y1, x2, y2, color, rounding, rounding, rounding, rounding, flags);
    }

    /** Filled rectangle with per-corner radii (top-right, bottom-right, top-left, bottom-left as the shader expects). */
    public void addRectFilled(float x1, float y1, float x2, float y2, int color,
                              float topRight, float bottomRight, float topLeft, float bottomLeft, int flags) {
        float w = x2 - x1;
        float h = y2 - y1;
        if (w <= 0f || h <= 0f || (color >>> 24) == 0) return;
        boolean any = topRight > 0.01f || bottomRight > 0.01f || topLeft > 0.01f || bottomLeft > 0.01f;
        if (!any) {
            Command c = add(RECT);
            c.x = x1; c.y = y1; c.w = w; c.h = h; c.color = color;
            return;
        }
        Command c = add(ROUND);
        c.x = x1; c.y = y1; c.w = w; c.h = h; c.color = color;
        c.radii = new float[]{Math.max(0f, topRight), Math.max(0f, bottomRight), Math.max(0f, topLeft), Math.max(0f, bottomLeft)};
    }

    public void addRect(float x1, float y1, float x2, float y2, int color, float rounding, int flags, float thickness) {
        if ((color >>> 24) == 0) return;
        Command c = add(OUTLINE);
        c.x = x1; c.y = y1; c.w = x2 - x1; c.h = y2 - y1;
        c.color = color;
        c.a = rounding;
        c.b = Math.max(1f, thickness);
    }

    public void addLine(float x1, float y1, float x2, float y2, int color, float thickness) {
        if ((color >>> 24) == 0) return;
        Command c = add(LINE);
        c.x = x1; c.y = y1; c.w = x2; c.h = y2;
        c.a = Math.max(0.5f, thickness);
        c.color = color;
    }

    public void addCircleFilled(float cx, float cy, float radius, int color, int segments) {
        if ((color >>> 24) == 0 || radius < 0.5f) return;
        Command c = add(CIRCLE);
        c.x = cx; c.y = cy; c.w = radius; c.color = color;
        c.a = segments <= 0 ? 24 : segments;
    }

    public void addCircle(float cx, float cy, float radius, int color, int segments, float thickness) {
        if ((color >>> 24) == 0 || radius < 0.5f) return;
        Command c = add(RING);
        c.x = cx; c.y = cy; c.w = radius; c.color = color;
        c.a = segments <= 0 ? 24 : segments;
        c.b = Math.max(0.5f, thickness);
    }

    public void addQuadFilled(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        if ((color >>> 24) == 0) return;
        Command c = add(QUAD);
        c.color = color;
        c.quad = new float[]{x1, y1, x2, y2, x3, y3, x4, y4};
    }

    /** Text in screen pixels; {@code shadowAlpha} > 0 draws the original 1px drop shadow first. */
    public void addText(String text, float x, float y, float size, int color, int shadowAlpha) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0) return;
        if (shadowAlpha > 0) {
            Command s = add(TEXT);
            s.text = text; s.x = x + 1f; s.y = y + 1f; s.a = size;
            s.color = (shadowAlpha << 24);
        }
        Command c = add(TEXT);
        c.text = text; c.x = x; c.y = y; c.a = size; c.color = color;
    }

    public void addImage(long texture, float x, float y, float w, float h, int color) {
        if (texture == 0L || (color >>> 24) == 0) return;
        Command c = add(IMAGE);
        c.texture = texture; c.x = x; c.y = y; c.w = w; c.h = h; c.color = color;
    }

    // ------------------------------------------------------------------ replay
    private static int argb(int imColor) {
        // ImGui IM_COL32 is r | g<<8 | b<<16 | a<<24; the client shaders take ARGB.
        int r = imColor & 0xFF;
        int g = (imColor >>> 8) & 0xFF;
        int b = (imColor >>> 16) & 0xFF;
        int a = (imColor >>> 24) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static Vector4f scissor(float[] clip) {
        if (clip == null) return null;
        float w = clip[2] - clip[0];
        float h = clip[3] - clip[1];
        if (w <= 0f || h <= 0f) return new Vector4f(clip[0], clip[1], 0f, 0f);
        return new Vector4f(clip[0], clip[1], w, h);
    }

    /** Replays every recorded primitive into the client's command queue, in order. */
    public void flush(Matrix4f matrix) {
        if (commands.isEmpty()) return;
        if (!CoreShaders.available()) {
            // No shader package on this GL: there is nothing to replay the command list with.
            clear();
            return;
        }
        try {
            for (Command c : commands) {
                try {
                    emit(c, matrix);
                } catch (Throwable firstFailure) {
                    if (firstFailure instanceof NoClassDefFoundError || firstFailure instanceof ExceptionInInitializerError) {
                        break;
                    }
                }
            }
        } finally {
            clear();
        }
    }

    private void emit(Command c, Matrix4f matrix) {
        Vector4f clipRect = scissor(c.clip);
        int color = argb(c.color);
        switch (c.type) {
            case RECT -> {
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_NO_DEPTH, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            mesh.beginShape()
                                    .putTransformedVertex(matrix, c.x, c.y + c.h, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x + c.w, c.y + c.h, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x + c.w, c.y, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x, c.y, z).putColor(abgr)
                                    .generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }));
            }
            case ROUND -> {
                Vector4f radii = new Vector4f(c.radii[0], c.radii[1], c.radii[2], c.radii[3]);
                DrawPipeline pipeline = DrawPipelines.getRoundedRect(radii);
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(pipeline, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            mesh.beginShape()
                                    .putTransformedVertex(matrix, c.x, c.y + c.h, z)
                                    .putFloats(new float[]{0f, 0f}).putFloats(new float[]{c.w, c.h}).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x + c.w, c.y + c.h, z)
                                    .putFloats(new float[]{c.w, 0f}).putFloats(new float[]{c.w, c.h}).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x + c.w, c.y, z)
                                    .putFloats(new float[]{c.w, c.h}).putFloats(new float[]{c.w, c.h}).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x, c.y, z)
                                    .putFloats(new float[]{0f, c.h}).putFloats(new float[]{c.w, c.h}).putColor(abgr)
                                    .generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }));
            }
            case OUTLINE -> RoundedOutlineShader.enqueueClipped(matrix, c.x, c.y, c.w, c.h,
                    new Color(color, true), c.a, c.b, clipRect);
            case LINE -> {
                float dx = c.w - c.x;
                float dy = c.h - c.y;
                float length = (float) Math.hypot(dx, dy);
                if (length < 1e-4f) return;
                float nx = -dy / length * c.a * 0.5f;
                float ny = dx / length * c.a * 0.5f;
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_NO_DEPTH, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            mesh.beginShape()
                                    .putTransformedVertex(matrix, c.x + nx, c.y + ny, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.w + nx, c.h + ny, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.w - nx, c.h - ny, z).putColor(abgr)
                                    .putTransformedVertex(matrix, c.x - nx, c.y - ny, z).putColor(abgr)
                                    .generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }));
            }
            case CIRCLE -> {
                int segments = Math.max(10, (int) c.a);
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_NO_DEPTH, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            mesh.beginShape();
                            mesh.putTransformedVertex(matrix, c.x, c.y, z).putColor(abgr);
                            for (int i = 0; i < segments; i++) {
                                float angle = (float) (Math.PI * 2 * i / segments);
                                mesh.putTransformedVertex(matrix,
                                        c.x + (float) Math.cos(angle) * c.w,
                                        c.y + (float) Math.sin(angle) * c.w, z).putColor(abgr);
                            }
                            mesh.generateIndices(GlPrimitive.TRIANGLES_FAN).resetBaseVertex();
                        }));
            }
            case RING -> {
                int segments = Math.max(12, (int) c.a);
                float outer = c.w + c.b * 0.5f;
                float inner = Math.max(0f, c.w - c.b * 0.5f);
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_NO_DEPTH, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            mesh.beginShape();
                            for (int i = 0; i <= segments; i++) {
                                float angle = (float) (Math.PI * 2 * i / segments);
                                float cos = (float) Math.cos(angle);
                                float sin = (float) Math.sin(angle);
                                mesh.putTransformedVertex(matrix, c.x + outer * cos, c.y + outer * sin, z).putColor(abgr);
                                mesh.putTransformedVertex(matrix, c.x + inner * cos, c.y + inner * sin, z).putColor(abgr);
                            }
                            mesh.generateIndices(GlPrimitive.QUAD_STRIP).resetBaseVertex();
                        }));
            }
            case TEXT -> {
                MsdfFont font = Prestige.fontManager == null ? null : Prestige.fontManager.getDefaultFont();
                if (font == null) return;
                font.drawString(matrix, c.text, c.x, c.y, c.a, color, clipRect);
            }
            case QUAD -> {
                ShapeRenderer.getDrawQueue().enqueue(new CallbackDrawCommand(DrawPipelines.TRIANGLES_NO_DEPTH, clipRect,
                        (depth, builder) -> {
                            float z = (Float) depth;
                            MeshBuilder mesh = (MeshBuilder) builder;
                            int abgr = ShapeRenderer.argbToAbgr(color);
                            float[] q = c.quad;
                            mesh.beginShape()
                                    .putTransformedVertex(matrix, q[0], q[1], z).putColor(abgr)
                                    .putTransformedVertex(matrix, q[2], q[3], z).putColor(abgr)
                                    .putTransformedVertex(matrix, q[4], q[5], z).putColor(abgr)
                                    .putTransformedVertex(matrix, q[6], q[7], z).putColor(abgr)
                                    .generateIndices(GlPrimitive.QUADS).resetBaseVertex();
                        }));
            }
            case IMAGE -> {
                GlTextureHandle handle = TEXTURES.get(c.texture);
                if (handle == null) return;
                RoundedTextureShader.enqueueClipped(matrix, handle, c.x, c.y, c.w, c.h,
                        new Color(color, true), BlendMode.MULTIPLY, clipRect);
            }
            default -> {
            }
        }
    }

    /** Depth slots are allocated by {@link dev.zprestige.prestige.render.DrawCommandQueue}; kept for API parity. */
    public int nextDepthClass() {
        return nextDepthSlot();
    }
}

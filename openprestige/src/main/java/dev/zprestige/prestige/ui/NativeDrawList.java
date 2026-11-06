package dev.zprestige.prestige.ui;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.render.CallbackDrawCommand;
import dev.zprestige.prestige.render.CoreShaders;
import dev.zprestige.prestige.render.DrawPipeline;
import dev.zprestige.prestige.render.DrawPipelines;
import dev.zprestige.prestige.render.GlTextureHandle;
import dev.zprestige.prestige.render.MsdfFont;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.RoundedOutlineShader;
import dev.zprestige.prestige.render.RoundedTextureShader;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.type.BlendMode;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.MeshBuilder;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_INT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11.GL_LIGHTING;
import static org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK;

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

    private int legacyVao = -1;
    private int legacyVbo = -1;
    private int legacyIbo = -1;
    private boolean legacyBound;

    private void ensureLegacyBuffers() {
        if (legacyBound && legacyVao >= 0 && legacyVbo >= 0 && legacyIbo >= 0) return;
        try {
            legacyVao = GL30.glGenVertexArrays();
            legacyVbo = GL15.glGenBuffers();
            legacyIbo = GL15.glGenBuffers();
            legacyBound = true;
        } catch (Throwable ignored) {
        }
    }

    private void flushLegacy() {
        if (commands.isEmpty()) return;
        ensureLegacyBuffers();
        if (legacyVao < 0 || legacyVbo < 0 || legacyIbo < 0) { clear(); return; }
        try {
            GL30.glBindVertexArray(legacyVao);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, legacyVbo);
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, legacyIbo);
            GL20.glEnableVertexAttribArray(0);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_LIGHTING);
            GL11.glEnable(GL_BLEND);
            GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            boolean scissorOn = GL11.glIsEnabled(GL_SCISSOR_TEST);
            for (Command c : commands) {
                try {
                    drawLegacy(c);
                } catch (Throwable ignored) {
                }
            }
            if (scissorOn) GL11.glEnable(GL_SCISSOR_TEST);
            GL11.glDepthMask(true);
            GL20.glDisableVertexAttribArray(0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        } catch (Throwable ignored) {
        } finally {
            clear();
        }
    }

    private void drawLegacy(Command c) {
        Vector4f sc = scissor(c.clip);
        int color = argb(c.color);
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        float scXScale = 1f, scYScale = 1f, fbHeight = 0f;
        try {
            fbHeight = RenderCore.getFramebufferHeight();
            scXScale = RenderCore.getScaleFactor();
            scYScale = RenderCore.getScaleFactor();
        } catch (Throwable ignored) {
        }
        if (sc != null) {
            GL11.glScissor((int) (sc.x * scXScale), (int) (fbHeight - sc.y * scYScale - sc.w * scYScale), (int) (sc.z * scXScale), (int) (sc.w * scXScale));
        }
        GL11.glColor4f(r / 255f, g / 255f, b / 255f, a / 255f);
        float z = 0f;
        switch (c.type) {
            case RECT -> {
                float x1 = c.x, y1 = c.y, x2 = c.x + c.w, y2 = c.y + c.h;
                float[] vs = new float[]{x1, y1, z, x2, y1, z, x2, y2, z, x1, y2, z};
                int[] is = new int[]{0, 1, 2, 0, 2, 3};
                uploadLegacy(vs, is);
                GL11.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
            }
            case QUAD -> {
                float[] q = c.quad;
                float[] vs = new float[]{q[0], q[1], z, q[2], q[3], z, q[4], q[5], z, q[6], q[7], z};
                int[] is = new int[]{0, 1, 2, 0, 2, 3};
                uploadLegacy(vs, is);
                GL11.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
            }
            case ROUND -> {
                float x1 = c.x, y1 = c.y, x2 = c.x + c.w, y2 = c.y + c.h;
                float[] rcopy = c.radii;
                float rtl = rcopy[2], tr = rcopy[0], br = rcopy[1], bl = rcopy[3];
                if (rtl <= 0.01f && tr <= 0.01f && br <= 0.01f && bl <= 0.01f) {
                    float[] vs = new float[]{x1, y1, z, x2, y1, z, x2, y2, z, x1, y2, z};
                    int[] is = new int[]{0, 1, 2, 0, 2, 3};
                    uploadLegacy(vs, is);
                    GL11.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
                } else {
                    fillRoundedLegacyPolygon(x1, y1, x2, y2, rtl, tr, br, bl, z);
                }
            }
            case OUTLINE -> {
                float x1 = c.x, y1 = c.y, x2 = c.x + c.w, y2 = c.y + c.h;
                float rad = Math.max(0.5f, c.a);
                float thick = Math.max(1f, c.b);
                drawRoundedOutlineLegacy(x1, y1, x2, y2, rad, thick, z);
            }
            case LINE -> {
                float x1 = c.x, y1 = c.y, x2 = c.w, y2 = c.h;
                float thick = Math.max(0.5f, c.a);
                drawLineLegacy(x1, y1, x2, y2, thick, z);
            }
            case CIRCLE -> {
                int segs = Math.max(10, (int) c.a);
                fillCircleLegacy(c.x, c.y, c.w, segs, z);
            }
            case RING -> {
                int segs = Math.max(12, (int) c.a);
                float outer = c.w + c.b * 0.5f;
                float inner = Math.max(0f, c.w - c.b * 0.5f);
                drawRingLegacy(c.x, c.y, outer, inner, segs, z);
            }
            case TEXT, IMAGE -> {
            }
            default -> {
            }
        }
        if (sc != null) GL11.glDisable(GL_SCISSOR_TEST);
    }

    private void uploadLegacy(float[] verts, int[] idx) {
        FloatBuffer vb = FloatBuffer.allocate(verts.length);
        vb.put(verts).flip();
        IntBuffer ib = IntBuffer.allocate(idx.length);
        ib.put(idx).flip();
        GL15.glBufferData(GL_ARRAY_BUFFER, vb, GL_STREAM_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0L);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STREAM_DRAW);
    }

    private void fillRoundedLegacyPolygon(float x1, float y1, float x2, float y2, float tl, float tr, float br, float bl, float z) {
        float w = x2 - x1, h = y2 - y1;
        float minD = Math.min(w, h);
        tl = Math.min(tl, minD * 0.5f); tr = Math.min(tr, minD * 0.5f); br = Math.min(br, minD * 0.5f); bl = Math.min(bl, minD * 0.5f);
        int segs = 12;
        int vertCount = (segs + 1) * 4;
        float[] vs = new float[vertCount * 3];
        int[] is = new int[segs * 6 * 4];
        int vp = 0, ip = 0;
        vs[vp++] = x1 + tl; vs[vp++] = y1;     vs[vp++] = z; vp++;
        int corner0 = vp / 3;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 0.5d * i / segs;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            vs[vp++] = (float) (x1 + tl + tr * (1d - ca));
            vs[vp++] = (float) (y1 + tl - tr * sa);
            vs[vp++] = z;
        }
        int topStart = vp / 3;
        vs[vp++] = x2 - tr; vs[vp++] = y1; vs[vp++] = z; vp++;
        int rightTop = vp / 3;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 0.5d * i / segs;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            vs[vp++] = (float) (x2 - tr + tr * sa);
            vs[vp++] = (float) (y1 + tr - tr * (1d - ca));
            vs[vp++] = z;
        }
        int rightMid = vp / 3;
        vs[vp++] = x2; vs[vp++] = y2 - br; vs[vp++] = z; vp++;
        int bottomRight = vp / 3;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 0.5d * i / segs;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            vs[vp++] = (float) (x2 - br + br * (1d - ca));
            vs[vp++] = (float) (y2 - br + br * sa);
            vs[vp++] = z;
        }
        int bottomMid = vp / 3;
        vs[vp++] = x1 + bl; vs[vp++] = y2; vs[vp++] = z; vp++;
        int leftBot = vp / 3;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 0.5d * i / segs;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            vs[vp++] = (float) (x1 + bl - bl * sa);
            vs[vp++] = (float) (y2 - bl + bl * (1d - ca));
            vs[vp++] = z;
        }
        int leftMid = vp / 3;
        int[] outline = new int[]{corner0, topStart, rightTop, rightMid, bottomRight, bottomMid, leftBot, leftMid};
        for (int ci = 0; ci < 4; ci++) {
            int base = outline[ci];
            int nextBase = outline[(ci + 1) % 4];
            for (int i = 0; i < segs; i++) {
                int a = base + i, b = base + i + 1, c1 = nextBase + i, d = nextBase + i + 1;
                if (i < segs - 1) {
                    is[ip++] = a; is[ip++] = b; is[ip++] = d;
                    is[ip++] = a; is[ip++] = d; is[ip++] = c1;
                }
            }
        }
        for (int ci = 0; ci < 4; ci++) {
            int base = outline[ci];
            int prevBase = outline[(ci + 3) % 4];
            for (int i = 0; i < segs; i++) {
                int innerPrev = prevBase + i, innerCur = prevBase + i + 1, outerCur = base + i + 1, outerPrev = base + i;
                if (i < segs - 1) {
                    is[ip++] = innerPrev; is[ip++] = outerCur; is[ip++] = outerPrev;
                    is[ip++] = innerPrev; is[ip++] = innerCur; is[ip++] = outerCur;
                }
            }
        }
        FloatBuffer vb = FloatBuffer.allocate(vs.length);
        vb.put(vs).flip();
        IntBuffer ib = IntBuffer.allocate(is.length);
        ib.put(is).flip();
        GL15.glBufferData(GL_ARRAY_BUFFER, vb, GL_STREAM_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0L);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STREAM_DRAW);
        GL11.glDrawElements(GL_TRIANGLES, is.length, GL_UNSIGNED_INT, 0L);
    }

    private void drawRoundedOutlineLegacy(float x1, float y1, float w, float h, float r, float t, float z) {
        drawRoundRectOutlineLegacy(x1, y1, w, h, r, t, z);
    }

    private void drawRoundRectOutlineLegacy(float x1, float y1, float w, float h, float r, float t, float z) {
        if (r <= 0.0f || w <= 2f * r || h <= 2f * r) {
            float[] vs = new float[]{x1, y1, z, x1 + w, y1, z, x1 + w, y1 + h, z, x1, y1 + h, z};
            int[] is = new int[]{0, 1, 2, 0, 2, 3};
            uploadLegacy(vs, is);
            GL11.glDrawElements(GL_LINE_LOOP, 4, GL_UNSIGNED_INT, 0L);
            return;
        }
        float rx = x1 + r, ry = y1 + r, rw = w - 2f * r, rh = h - 2f * r;
        int segs = 12;
        float[][] arcs = new float[][] {
            new float[]{rx, ry, (float) Math.PI, (float) (Math.PI * 1.5d)},
            new float[]{rx + rw, ry, (float) (Math.PI * 1.5d), (float) (Math.PI * 2d)},
            new float[]{rx + rw, ry + rh, 0f, (float) (Math.PI * 0.5d)},
            new float[]{rx, ry + rh, (float) (-Math.PI * 0.5d), 0f}
        };
        for (float[] arc : arcs) {
            float cx = arc[0], cy = arc[1], a0 = arc[2], a1 = arc[3];
            int vp = 0;
            float[] vs = new float[(segs + 2) * 3];
            vs[vp++] = cx; vs[vp++] = cy; vs[vp++] = z;
            for (int i = 0; i <= segs; i++) {
                double ang = a0 + (a1 - a0) * i / segs;
                vs[vp++] = (float) (cx + Math.cos(ang) * (r - t * 0.5f));
                vs[vp++] = (float) (cy + Math.sin(ang) * (r - t * 0.5f));
                vs[vp++] = z;
            }
            int[] is = new int[segs * 3];
            for (int i = 0; i < segs; i++) {
                is[i * 3] = 0; is[i * 3 + 1] = i + 1; is[i * 3 + 2] = i + 2;
            }
            uploadLegacy(vs, is);
            GL11.glDrawElements(GL_TRIANGLE_FAN, is.length, GL_UNSIGNED_INT, 0L);
        }
        float[][] segs2 = new float[][] {
            new float[]{x1, y1 + r, x1 + w - r, y1 + r, t},
            new float[]{x1 + w - r, y1, x1 + w - r, y1 + h - r, t},
            new float[]{x1 + w, y1 + h - r, x1 + r, y1 + h - r, t},
            new float[]{x1 + r, y1 + h, x1, y1 + h, t}
        };
        for (float[] seg : segs2) {
            drawLineLegacy(seg[0], seg[1], seg[2], seg[3], seg[4], z);
        }
    }

    private void drawLineLegacy(float x1, float y1, float x2, float y2, float t, float z) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * t * 0.5f, ny = dx / len * t * 0.5f;
        float[] vs = new float[]{x1 + nx, y1 + ny, z, x2 + nx, y2 + ny, z, x2 - nx, y2 - ny, z, x1 - nx, y1 - ny, z};
        int[] is = new int[]{0, 1, 2, 0, 2, 3};
        uploadLegacy(vs, is);
        GL11.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
    }

    private void fillCircleLegacy(float cx, float cy, float r, int segs, float z) {
        int vp = 0;
        float[] vs = new float[(segs + 2) * 3];
        vs[vp++] = cx; vs[vp++] = cy; vs[vp++] = z;
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 2d * i / segs;
            vs[vp++] = (float) (cx + Math.cos(ang) * r);
            vs[vp++] = (float) (cy + Math.sin(ang) * r);
            vs[vp++] = z;
        }
        int[] is = new int[segs * 3];
        for (int i = 0; i < segs; i++) {
            is[i * 3] = 0; is[i * 3 + 1] = i + 1; is[i * 3 + 2] = i + 2;
        }
        uploadLegacy(vs, is);
        GL11.glDrawElements(GL_TRIANGLES, is.length, GL_UNSIGNED_INT, 0L);
    }

    private void drawRingLegacy(float cx, float cy, float outer, float inner, int segs, float z) {
        int vp = 0;
        float[] vs = new float[(segs + 1) * 6];
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 2d * i / segs;
            double c = Math.cos(ang), s = Math.sin(ang);
            vs[vp++] = (float) (cx + outer * c); vs[vp++] = (float) (cy + outer * s); vs[vp++] = z;
            vs[vp++] = (float) (cx + inner * c); vs[vp++] = (float) (cy + inner * s); vs[vp++] = z;
        }
        int[] is = new int[segs * 6];
        for (int i = 0; i < segs; i++) {
            int a = i * 2, b = i * 2 + 1, c1 = (i + 1) * 2, d = (i + 1) * 2 + 1;
            is[i * 6] = a; is[i * 6 + 1] = b; is[i * 6 + 2] = c1;
            is[i * 6 + 3] = b; is[i * 6 + 4] = d; is[i * 6 + 5] = c1;
        }
        uploadLegacy(vs, is);
        GL11.glDrawElements(GL_TRIANGLES, is.length, GL_UNSIGNED_INT, 0L);
    }

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
        try {
            if (CoreShaders.available()) {
                for (Command c : commands) {
                    try {
                        emit(c, matrix);
                    } catch (Throwable firstFailure) {
                        if (firstFailure instanceof NoClassDefFoundError || firstFailure instanceof ExceptionInInitializerError) {
                            commands.clear();
                            return;
                        }
                    }
                }
            } else {
                flushLegacy();
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

package dev.zprestige.prestige.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zprestige.prestige.config.MsdfFontMetrics;
import dev.zprestige.prestige.config.MsdfGlyph;
import dev.zprestige.prestige.config.ClasspathResource;
import dev.zprestige.prestige.data.MsdfFontState;
import dev.zprestige.prestige.data.GlCapabilityState;
import dev.zprestige.prestige.data.DrawBatchKey;
import dev.zprestige.prestige.math.RenderCacheUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.texture.NativeImage;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class MsdfFont {
    private static final int[][] FORMATTING_COLORS = new int[32][3];
    private float distanceRange;
    private int atlasWidth;
    private int atlasHeight;
    private float fontSize;
    private float texelWidth;
    private float texelHeight;
    private final Int2ObjectOpenHashMap glyphs;
    private final Map widthCache;
    private MsdfFontMetrics metrics;
    private GlTextureHandle atlasTexture;
    private BiConsumer pipelineCustomizer;
    private Function drawQueueProvider;
    private DrawPipeline pipeline;
    private DrawBatchKey batchKey;

    public MsdfFont(String name) throws IOException {
        super();
        this.fontSize = 8.0F;
        this.glyphs = new Int2ObjectOpenHashMap();
        this.widthCache = RenderCacheUtil.newLruCache(256);
        this.drawQueueProvider = font -> {
            ShapeRenderer.initDrawQueue();
            return (DrawCommandQueue)ShapeRenderer.drawQueue.get();
        };
        ClasspathResource jsonResource = ClasspathResource.of("assets/prestige/font/" + name + ".json");
        ClasspathResource pngResource = ClasspathResource.of("assets/prestige/font/" + name + ".png");

        try (
            InputStream jsonStream = jsonResource.openStream();
            InputStream pngStream = pngResource.openStream();
        ) {
            this.loadAtlas(new InputStreamReader(jsonStream), pngStream);
        }
    }

    private void loadAtlas(Reader reader, InputStream imageStream) throws IOException {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

        {
            label49: {
                {
                    if ("msdf".equals(json.getAsJsonObject("atlas").get("type").getAsString())) {
                        break label49;
                    }
                }

                throw new RuntimeException("not msdf atlas");
            }
        }

        this.atlasWidth = json.getAsJsonObject("atlas").get("width").getAsInt();
        this.atlasHeight = json.getAsJsonObject("atlas").get("height").getAsInt();
        this.distanceRange = json.getAsJsonObject("atlas").get("distanceRange").getAsFloat();
        this.metrics = MsdfFontMetrics.fromJson(json.getAsJsonObject("metrics"));
        this.texelWidth = 1.0F / this.atlasWidth;
        this.texelHeight = 1.0F / this.atlasHeight;
        NativeImage image = NativeImage.read(imageStream);
        try {
            this.atlasTexture = GlTextureHandle.create(3553);
            this.atlasTexture.allocate(32856, image.getWidth(), image.getHeight());
            int width = image.getWidth();
            int height = image.getHeight();
            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            pixels.order(ByteOrder.LITTLE_ENDIAN);

            for (int row = 0; row < height; row++) {
                int column = 0;

                while (column < width) {
                    int argb = image.getColorArgb(column, row);

                    {
                        pixels.putInt(argb);
                        column++;
                    }
                }
            }

            pixels.flip();
            GL11.glPixelStorei(3317, 1);
            GL11.glPixelStorei(3314, 0);
            GL11.glPixelStorei(3316, 0);
            GL11.glPixelStorei(3315, 0);
            this.atlasTexture.uploadRegion(0, 0, width, height, 6408, 5121, pixels);
            GL11.glPixelStorei(3317, 4);
            this.atlasTexture.setFilter(9729);
            this.atlasTexture.setWrapMode(33071);
        } finally {
            image.close();
        }

        for (JsonElement glyphElement : json.getAsJsonArray("glyphs")) {
            MsdfGlyph glyph = MsdfGlyph.fromJson(glyphElement.getAsJsonObject());
            this.glyphs.put(glyph.getCodePoint(), glyph);
        }
    }

    public void setPipelineCustomizer(BiConsumer customizer) {
        this.pipelineCustomizer = customizer;
    }

    public void setDrawQueueProvider(Function provider) {
        this.drawQueueProvider = provider;
    }

    public void drawString(Matrix4f matrix, String text, float textX, float textY, float size, int color) {
        this.drawString(matrix, text, textX, textY, size, color, null);
    }

    /**
     * Draws a string with an optional scissor rectangle (GUI pixels). The ClickGUI needs clipping for its scrolling
     * lists and popups; a null rectangle behaves exactly like {@link #drawString(Matrix4f, String, float, float, float, int)}.
     */
    public void drawString(Matrix4f matrix, String text, float textX, float textY, float size, int color, org.joml.Vector4f scissor) {
        DrawCommandQueue queue = (DrawCommandQueue)this.drawQueueProvider.apply(this);
        DrawBatchKey key = scissor == null ? this.getBatchKey() : new DrawBatchKey(scissor, this.getPipeline());
        Matrix4f transform = new Matrix4f(matrix);
        float baseline = this.metrics.getAscender() * size;
        float[] cursorX = new float[]{textX};
        this.forEachGlyph(text, (GlyphVisitor)(codePoint, glyph) -> {
            cursorX[0] = cursorX[0] + this.drawGlyph(queue, key, transform, glyph, cursorX[0], textY, size, baseline, color);
            return true;
        });
    }

    public void drawShadowedString(Matrix4f matrix, String text, float textX, float textY, float size, Color color) {
        int argb = color.getRGB();
        this.drawString(matrix, text, textX + 0.5F, textY + 0.5F, size, ((argb >>> 24 & 0xFF) * 140 >> 8) << 24);
        this.drawString(matrix, text, textX, textY, size, argb);
    }

    public void drawText(Matrix4f matrix, String text, float textX, float textY, Color color) {
        this.drawShadowedString(matrix, text, textX, textY, this.fontSize, color);
    }

    public void drawTextScaled(Matrix4f matrix, String text, float textX, float textY, float scale, Color color) {
        this.drawShadowedString(matrix, text, textX, textY, this.fontSize * scale, color);
    }

    public float getTextWidth(String text) {
        return this.getTextWidth(text, this.fontSize);
    }

    public float getTextWidth(String text, float size) {
        if (text.isEmpty()) {
            return 0.0F;
        } else {
            Float cached = (Float)this.widthCache.get(text);
            float unitWidth;
            if (cached != null) {
                unitWidth = cached;
            } else {
                float[] total = new float[]{0.0F};
                this.forEachGlyph(text, (GlyphVisitor)(codePoint, glyph) -> {
                    total[0] += glyph.getAdvance();
                    return true;
                });
                unitWidth = total[0];
                this.widthCache.put(text, unitWidth);
            }

            return size * unitWidth;
        }
    }

    public float getLineHeight() {
        return this.getLineHeight(this.fontSize);
    }

    public float getLineHeight(float size) {
        return this.metrics.getLineHeight() * size;
    }

    private void forEachGlyph(String text, GlyphVisitor visitor) {
        int length = text.length();
        int index = 0;

        while (index < length) {
            int codePoint = text.codePointAt(index);
            if (codePoint == 167 && index + 1 < length) {
                index += 2;
            } else {
                index += Character.charCount(codePoint);
                MsdfGlyph glyph = (MsdfGlyph)this.glyphs.get(codePoint);
                if (glyph != null && !visitor.visit(codePoint, glyph)) {
                    return;
                }
            }
        }
    }

    public float getFontSize() {
        return this.fontSize;
    }

    public void setFontSize(float size) {
        this.fontSize = size;
    }

    private float drawGlyph(DrawCommandQueue queue, DrawBatchKey key, Matrix4f matrix, MsdfGlyph glyph, float cursorX, float textY, float size, float baseline, int color) {
        if (glyph.getPlaneRight() - glyph.getPlaneLeft() != 0.0F) {
            float left = cursorX + glyph.getPlaneLeft() * size;
            float right = cursorX + glyph.getPlaneRight() * size;
            float top = textY + baseline - glyph.getPlaneTop() * size;
            float bottom = textY + baseline - glyph.getPlaneBottom() * size;
            float minU = glyph.getAtlasLeft() * this.texelWidth;
            float maxU = glyph.getAtlasRight() * this.texelWidth;
            queue.enqueue(new GlyphQuadCommand(key, left, top, right, bottom, color, minU, 1.0F - glyph.getAtlasTop() * this.texelHeight, maxU, 1.0F - glyph.getAtlasBottom() * this.texelHeight, matrix));
        }

        return size * glyph.getAdvance();
    }

    public DrawBatchKey getBatchKey() {
        if (this.batchKey == null) {
            this.batchKey = new DrawBatchKey(null, this.getPipeline());
        }

        return this.batchKey;
    }

    public DrawPipeline getPipeline() {
        if (this.pipeline == null) {
            VertexLayout format = VertexLayouts.POSITION_COLOR_UV;
            RenderStateElement[] states = new RenderStateElement[]{RenderStateElement.MAIN_TARGET, RenderStateElement.shader(CoreShaders.MSDF_TEXT), null, null, null, null};
            states[2] = RenderStateElement.capability(2884, false, true);
            states[3] = RenderStateElement.blend(true, true);
            states[4] = new GlCapabilityState(2929, false);
            states[5] = new MsdfFontState(this.atlasTexture, this.distanceRange / this.atlasWidth, this.distanceRange / this.atlasHeight);
            this.pipeline = new DrawPipeline(format, 4, false, states);
            if (this.pipelineCustomizer != null) {
                this.pipelineCustomizer.accept(this, this.pipeline);
            }
        }

        return this.pipeline;
    }

    public void close() {
        if (this.atlasTexture != null) {
            this.atlasTexture.close();
        }
    }

    static {
        for (int index = 0; index < 32; index++) {
            int base = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + base;
            int green = (index >> 1 & 1) * 170 + base;
            int blue = (index & 1) * 170 + base;
            if (index == 6) {
                red += 85;
            }

            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }

            FORMATTING_COLORS[index][0] = red;
            FORMATTING_COLORS[index][1] = green;
            FORMATTING_COLORS[index][2] = blue;
        }
    }
}

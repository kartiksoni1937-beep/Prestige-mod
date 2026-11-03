package dev.zprestige.prestige.render;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.world.PositionUtil;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public class Render2D implements MinecraftAccess {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    public static net.minecraft.client.gui.DrawContext drawContext;
    public static net.minecraft.client.util.math.MatrixStack matrices;
    public static Matrix4f projectionMatrix = new Matrix4f();
    public static Matrix4f modelViewMatrix = new Matrix4f();
    public static Matrix4f cameraRotationMatrix = new Matrix4f();
    public static BufferSource currentBuffers;
    public static CameraMatrices currentContext;
    private static final int[] viewport = new int[4];
    private static boolean viewportCaptured = false;
    private static final Matrix4f projectionViewScratch = new Matrix4f();
    private static final Matrix4f modelViewScratch = new Matrix4f();
    private static final Vector3f screenPosScratch = new Vector3f();
    private static final Vector4f viewPosScratch = new Vector4f();
    private static long lastFrameIndex = Long.MIN_VALUE;
    private static long frameTimeMillis = 0L;
    private static final Matrix4f matrixScratch = new Matrix4f();
    private static final float[] hsbScratch = new float[3];
    private static int cachedHsbRgb = 1;
    private static final Vector4f radiiScratch = new Vector4f();
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color ITEM_BAR_BACKGROUND = new Color(0, 0, 0, 200);
    private static final Identifier ITEMS_ATLAS = SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE;

    public static void captureViewport() {
        GL11.glGetIntegerv(2978, viewport);
        viewportCaptured = true;
    }

    private static int[] getViewport() {
        if (!viewportCaptured) {
            GL11.glGetIntegerv(2978, viewport);
            viewportCaptured = true;
        }

        return viewport;
    }

    private static long getFrameTimeMillis() {
        long frameIndex = RenderCore.getFrameCount();
        if (frameIndex != lastFrameIndex) {
            lastFrameIndex = frameIndex;
            frameTimeMillis = System.currentTimeMillis();
        }

        return frameTimeMillis;
    }

    public static Vec3d worldToScreen(Vec3d worldPos) {
        Camera camera = client.getEntityRenderDispatcher().camera;
        int windowHeight = client.getWindow().getHeight();
        int[] viewportRect = getViewport();
        Vector3f screenPos = screenPosScratch;
        Vec3d cameraPos = camera.getCameraPos();
        Vector4f viewPos = viewPosScratch.set((float)(worldPos.x - cameraPos.x), (float)(worldPos.y - cameraPos.y), (float)(worldPos.z - cameraPos.z), 1.0F).mul(cameraRotationMatrix);
        projectionViewScratch.set(projectionMatrix).mul(modelViewScratch.set(modelViewMatrix)).project(viewPos.x(), viewPos.y(), viewPos.z(), viewportRect, screenPos);
        return new Vec3d(screenPos.x / client.getWindow().getScaleFactor(), (windowHeight - screenPos.y) / client.getWindow().getScaleFactor(), screenPos.z);
    }

    private static double interpolate(double current, double previous) {
        return previous + (current - previous) * getTickDelta();
    }

    public static Vec3d getInterpolatedPos(Entity entity) {
        Vec3d lastPos = PositionUtil.getLastPos(entity);
        return new Vec3d(interpolate(entity.getX(), lastPos.x), interpolate(entity.getY(), lastPos.y), interpolate(entity.getZ(), lastPos.z));
    }

    public static Color getWaveColor(Color baseColor, int index, int count) {
        int rgb = baseColor.getRGB();
        if (rgb != cachedHsbRgb) {
            Color.RGBtoHSB(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), hsbScratch);
            cachedHsbRgb = rgb;
        }

        float brightness = 0.5F + 0.5F * Math.abs(((float)(getFrameTimeMillis() % 2000L) / 1000.0F + (float)index / count * 2.0F) % 2.0F - 1.0F);
        return new Color(Color.HSBtoRGB(hsbScratch[0], hsbScratch[1], Math.min(brightness, 1.0F)));
    }

    public static Vec3d getCameraEntityPos() {
        return PositionUtil.getEntityPos(client.getCameraEntity());
    }

    public static void drawCircle(Matrix4f matrix, float centerX, float centerY, float radius, Color color) {
        ShapeRenderer.drawCircle(matrix, centerX, centerY, radius, color.getRGB());
    }

    public static void drawCircleOutline(Matrix4f matrix, float centerX, float centerY, float radius, Color color) {
        ShapeRenderer.drawRing(matrix, centerX, centerY, radius, 1.0F, color.getRGB());
    }

    public static void drawRoundedRectOutline(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, float radius) {
        ShapeRenderer.drawRoundedRectOutline(currentBuffers, currentContext, matrix, x1, y1, x2, y2, color, radius, 0.25F);
    }

    public static void drawRoundedRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color, float radius) {
        ShapeRenderer.drawRoundedRect(matrix, x1, y1, x2, y2, color, radiiScratch.set(radius));
    }

    public static void drawRoundedGradientRect(Matrix4f matrix, float x1, float y1, float x2, float y2, float radius, Color bottomLeftColor, Color bottomRightColor, Color topRightColor, Color topLeftColor) {
        ShapeRenderer.drawGradientRoundedRect(matrix, x1, y1, x2, y2, bottomLeftColor, bottomRightColor, topRightColor, topLeftColor, radiiScratch.set(radius));
    }

    public static void drawRect(Matrix4f matrix, float x1, float y1, float x2, float y2, Color color) {
        ShapeRenderer.drawRect(matrix, x1, y1, x2, y2, color);
    }

    public static void drawTexture(float drawX, float drawY, float width, float height, Matrix4f matrix, GlTextureHandle texture, Color color) {
        RoundedTextureShader.drawTextureQuad(currentBuffers, currentContext, texture, drawX, drawY, width, height, matrix, color);
    }

    public static void drawTextureRegion(float drawX, float drawY, float width, float height, float regionU, float regionV, float regionWidth, float regionHeight, float radius, Matrix4f matrix, GlTextureHandle texture, Color color) {
        RoundedTextureShader.drawTextureRegion(currentBuffers, currentContext, matrix, texture, drawX, drawY, width, height, regionU, regionV, regionWidth, regionHeight, new Vector4f(radius), color);
    }

    public static boolean drawItem(ItemStack stack, float itemX, float itemY, float scale, Matrix4f matrix) {
        Identifier modelId = stack.get(DataComponentTypes.ITEM_MODEL);
        if (modelId == null) {
            modelId = Registries.ITEM.getId(stack.getItem());
        }

        SpriteIdentifier spriteId = new SpriteIdentifier(ITEMS_ATLAS, Identifier.of(modelId.getNamespace(), "item/" + modelId.getPath()));
        Sprite sprite = client.getAtlasManager().getSprite(spriteId);
        if (sprite.getContents().getId().getPath().equals("missingno")) {
            return false;
        } else {
            Identifier atlasId = sprite.getAtlasId();
            float minU = sprite.getMinU();
            float maxU = sprite.getMaxU();
            float minV = sprite.getMinV();
            float maxV = sprite.getMaxV();
            float size = 16.0F * scale;
            drawTextureRegion(itemX, itemY, size, size, minU * size, size - maxV * size, (maxU - minU) * size, (maxV - minV) * size, 0.0F, matrix, GlTextureHandle.wrapMinecraftTexture(atlasId), Color.WHITE);
            if (stack.getCount() > 1) {
                String countText = String.valueOf(stack.getCount());
                Prestige.fontManager.getDefaultFont().drawTextScaled(matrix, countText, itemX + size - Prestige.fontManager.getDefaultFont().getTextWidth(countText) * scale, itemY + size - Prestige.fontManager.getDefaultFont().getLineHeight() * scale, scale, Color.WHITE);
            }

            if (stack.isItemBarVisible()) {
                int barX = (int)(itemX + 2.0F * scale);
                int barY = (int)(itemY + 16.0F * scale);
                float barLeft = barX;
                float barTop = barY;
                drawRoundedRect(matrix, barLeft, barTop, barX + 13.0F * scale, barY + scale, ITEM_BAR_BACKGROUND, 2.0F);
                barLeft = barX;
                barTop = barY;
                drawRoundedRect(matrix, barLeft, barTop, barX + stack.getItemBarStep() * scale, barY + scale, new Color(withFullAlpha(stack.getItemBarColor())), 2.0F);
            }

            return true;
        }
    }

    public static int withFullAlpha(int rgb) {
        return rgb | 0xFF000000;
    }

    public static float getTickDelta() {
        return client.getRenderTickCounter().getTickProgress(false);
    }
}

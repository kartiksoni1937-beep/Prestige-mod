package dev.zprestige.prestige.math;

import dev.zprestige.prestige.util.LruCache;
import java.util.Map;
import org.joml.Vector4f;

public final class RenderCacheUtil {
    private static final String NO_INSTANCE_MESSAGE = "This is a utility class and cannot be instantiated";

    private RenderCacheUtil() {
        throw new UnsupportedOperationException(NO_INSTANCE_MESSAGE);
    }

    public static Map newLruCache(int capacity) {
        return new LruCache(16, 0.75F, true, capacity);
    }

    public static float quantize(float value) {
        return Math.round(value * 4.0F) * 0.25F;
    }

    public static Vector4f quantizeVector(Vector4f vector) {
        return new Vector4f(quantize(vector.x), quantize(vector.y), quantize(vector.z), quantize(vector.w));
    }

    public static int getAlpha(int argb) {
        return argb >>> 24;
    }

    public static int getRed(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int getGreen(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int getBlue(int argb) {
        return argb & 0xFF;
    }

    public static float getAlphaFloat(int argb) {
        return channelToFloat(getAlpha(argb));
    }

    public static float getRedFloat(int argb) {
        return channelToFloat(getRed(argb));
    }

    public static float getGreenFloat(int argb) {
        return channelToFloat(getGreen(argb));
    }

    public static float getBlueFloat(int argb) {
        return channelToFloat(getBlue(argb));
    }

    private static float channelToFloat(int channel) {
        return channel / 255.0F;
    }
}

package dev.zprestige.prestige.render;

import dev.zprestige.prestige.math.RenderCacheUtil;
import org.joml.Vector4f;

public class CornerRadii {
    public final float topRight;
    public final float bottomRight;
    public final float topLeft;
    public final float bottomLeft;

    public CornerRadii(Vector4f radii) {
        super();
        this.topRight = RenderCacheUtil.quantize(radii.x);
        this.bottomRight = RenderCacheUtil.quantize(radii.y);
        this.topLeft = RenderCacheUtil.quantize(radii.z);
        this.bottomLeft = RenderCacheUtil.quantize(radii.w);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof CornerRadii that)
                ? false
                : Float.compare(that.topRight, this.topRight) == 0
                    && Float.compare(that.bottomRight, this.bottomRight) == 0
                    && Float.compare(that.topLeft, this.topLeft) == 0
                    && Float.compare(that.bottomLeft, this.bottomLeft) == 0;
        }
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * Float.floatToIntBits(this.topRight) + Float.floatToIntBits(this.bottomRight)) + Float.floatToIntBits(this.topLeft)) + Float.floatToIntBits(this.bottomLeft);
    }
}

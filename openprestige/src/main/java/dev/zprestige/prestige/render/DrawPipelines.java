package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.BlendToggleState;
import dev.zprestige.prestige.data.GlCapabilityState;
import dev.zprestige.prestige.data.DepthFuncState;
import dev.zprestige.prestige.data.ShaderBindState;
import dev.zprestige.prestige.math.RenderCacheUtil;
import java.util.Map;
import org.joml.Vector4f;

public class DrawPipelines {
    public static final DrawPipeline TRIANGLES = new DrawPipeline(
        VertexLayouts.POSITION_COLOR,
        4,
        false,
        new RenderStateElement[]{RenderStateElement.MAIN_TARGET, new ShaderBindState(CoreShaders.POS_COLOR), new GlCapabilityState(2884, false), new BlendToggleState(true, true), new DepthFuncState(515, 515)}
    );
    public static final DrawPipeline TRIANGLES_NO_DEPTH = new DrawPipeline(
        VertexLayouts.POSITION_COLOR,
        4,
        false,
        new RenderStateElement[]{
            RenderStateElement.MAIN_TARGET,
            new ShaderBindState(CoreShaders.POS_COLOR),
            new GlCapabilityState(2884, false),
            new BlendToggleState(true, true),
            new DepthFuncState(515, 515),
            new GlCapabilityState(2929, false)
        }
    );
    public static final DrawPipeline TRIANGLES_DEPTH_TESTED = new DrawPipeline(
        VertexLayouts.POSITION_COLOR,
        4,
        false,
        new RenderStateElement[]{
            RenderStateElement.MAIN_TARGET,
            new ShaderBindState(CoreShaders.POS_COLOR),
            new GlCapabilityState(2884, true),
            new BlendToggleState(true, true),
            new DepthFuncState(515, 515),
            new GlCapabilityState(2929, true)
        }
    );
    public static final DrawPipeline LINES = new DrawPipeline(
        VertexLayouts.POSITION_COLOR,
        1,
        false,
        new RenderStateElement[]{RenderStateElement.MAIN_TARGET, new ShaderBindState(CoreShaders.POS_COLOR), new GlCapabilityState(2884, true), new BlendToggleState(true, true), new DepthFuncState(515, 515)}
    );
    public static final DrawPipeline LINES_NO_DEPTH = new DrawPipeline(
        VertexLayouts.POSITION_COLOR,
        1,
        false,
        new RenderStateElement[]{
            RenderStateElement.MAIN_TARGET,
            new ShaderBindState(CoreShaders.POS_COLOR),
            new GlCapabilityState(2884, true),
            new BlendToggleState(true, true),
            new DepthFuncState(515, 515),
            new GlCapabilityState(2929, false)
        }
    );
    private static final Map ROUNDED_RECT_CACHE;
    static final int ROUNDNESS_LOCATION;

    public static DrawPipeline getRoundedRect(Vector4f radii) {
        CornerRadii key = new CornerRadii(radii);
        DrawPipeline drawMode = (DrawPipeline)ROUNDED_RECT_CACHE.get(key);
        if (drawMode == null) {
            drawMode = new DrawPipeline(
                VertexLayouts.POSITION_UV_SIZE_COLOR,
                4,
                false,
                new RenderStateElement[]{
                    RenderStateElement.MAIN_TARGET,
                    new ShaderBindState(CoreShaders.ROUNDED_RECT),
                    new GlCapabilityState(2884, true),
                    new BlendToggleState(true, true),
                    new DepthFuncState(515, 515),
                    new GlCapabilityState(2929, false),
                    new CornerRadiiUniform(key)
                }
            );
            ROUNDED_RECT_CACHE.put(key, drawMode);
        }

        return drawMode;
    }

    static {
        ROUNDED_RECT_CACHE = RenderCacheUtil.newLruCache(128);
        ROUNDNESS_LOCATION = CoreShaders.ROUNDED_RECT.getUniformLocation("roundness");
    }
}

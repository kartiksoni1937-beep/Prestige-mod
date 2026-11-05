package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.VertexElement;

public class VertexLayouts {
    public static final VertexLayout POSITION;
    public static final VertexLayout POSITION_COLOR;
    public static final VertexLayout POSITION_COLOR_UV;
    public static final VertexLayout POSITION_NORMAL_COLOR_UV;
    public static final VertexLayout POSITION_UV_SIZE_COLOR;
    public static final VertexLayout POSITION_UV;
    public static final VertexLayout POSITION_UV_SIZE;

    static {
        long[] glTypes = new long[]{5126L, 5126L, 5121L, 5121L};
        POSITION = new VertexLayout(new VertexElement[]{new VertexElement(3, (int)glTypes[0], 4, false)});
        POSITION_COLOR = new VertexLayout(
            new VertexElement[]{new VertexElement(3, (int)glTypes[1], 4, false), new VertexElement(4, (int)glTypes[3], 1, true)}
        );
        POSITION_COLOR_UV = new VertexLayout(
            new VertexElement[]{
                new VertexElement(3, (int)glTypes[1], 4, false),
                new VertexElement(4, (int)glTypes[2], 1, true),
                new VertexElement(2, (int)glTypes[1], 4, false)
            }
        );
        POSITION_NORMAL_COLOR_UV = new VertexLayout(
            new VertexElement[]{
                new VertexElement(3, (int)glTypes[1], 4, false),
                new VertexElement(3, (int)glTypes[1], 4, false),
                new VertexElement(4, (int)glTypes[2], 1, true),
                new VertexElement(2, (int)glTypes[1], 4, false)
            }
        );
        POSITION_UV_SIZE_COLOR = new VertexLayout(
            new VertexElement[]{
                new VertexElement(3, (int)glTypes[1], 4, false),
                new VertexElement(2, (int)glTypes[1], 4, false),
                new VertexElement(2, (int)glTypes[1], 4, false),
                new VertexElement(4, (int)glTypes[2], 1, true)
            }
        );
        POSITION_UV = new VertexLayout(
            new VertexElement[]{new VertexElement(3, (int)glTypes[1], 4, false), new VertexElement(2, (int)glTypes[1], 4, false)}
        );
        POSITION_UV_SIZE = new VertexLayout(
            new VertexElement[]{
                new VertexElement(3, (int)glTypes[1], 4, false),
                new VertexElement(2, (int)glTypes[1], 4, false),
                new VertexElement(2, (int)glTypes[1], 4, false)
            }
        );
    }
}

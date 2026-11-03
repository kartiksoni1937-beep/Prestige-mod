package dev.zprestige.prestige.world;

import dev.zprestige.prestige.render.GlPrimitiveSwitchMap;
import dev.zprestige.prestige.type.GlPrimitive;

public interface MeshBuilder {
    String NO_OFFSET_MESSAGE = "generateIndices called with no offset being present and indices in this buffer : don't know where to begin generating..?";

    MeshBuilder begin();

    MeshBuilder putFloats(float[] attributeValues);

    MeshBuilder putVertex(float x, float y, float z);

    MeshBuilder putTransformedVertex(org.joml.Matrix4f transform, float x, float y, float z);

    MeshBuilder putColor(int argbColor);

    MeshBuilder end();

    MeshBuilder putIndices(int[] indexValues);

    int getVertexCount();

    int getIndexCount();

    MeshBuilder resetBaseVertex();

    void ensureIndexCapacity(int byteCount);

    void ensureVertexCapacity(int byteCount);

    int getBaseVertex();

    default MeshBuilder generateIndices(GlPrimitive primitive) {
        if (!GlPrimitiveSwitchMap.ASSERTIONS_DISABLED && this.getBaseVertex() == 0 && this.getIndexCount() != 0) {
            throw new AssertionError(NO_OFFSET_MESSAGE);
        } else {
            int shapeVertexCount = this.getVertexCount() - this.getBaseVertex();
            switch (GlPrimitiveSwitchMap.SWITCH_MAP[primitive.ordinal()]) {
                case 1:
                    this.ensureIndexCapacity(shapeVertexCount / 3 * 3 * 4);

                    for (int triangleStart = 0; triangleStart < shapeVertexCount; triangleStart += 3) {
                        this.putIndices(new int[]{triangleStart, triangleStart + 1, triangleStart + 2});
                    }

                    break;
                case 2:
                    this.ensureIndexCapacity(shapeVertexCount / 4 * 6 * 4);

                    for (int quadStart = 0; quadStart < shapeVertexCount; quadStart += 4) {
                        this.putIndices(new int[]{quadStart, quadStart + 1, quadStart + 2, quadStart + 2, quadStart + 3, quadStart});
                    }

                    break;
                case 3:
                    this.ensureIndexCapacity((shapeVertexCount - 2) * 3 * 4);

                    for (int fanVertex = 1; fanVertex < shapeVertexCount - 1; fanVertex++) {
                        this.putIndices(new int[]{0, fanVertex, fanVertex + 1});
                    }

                    break;
                case 4:
                    this.ensureIndexCapacity((shapeVertexCount - 1) * 2 * 4);

                    for (int lineStripVertex = 0; lineStripVertex < shapeVertexCount - 1; lineStripVertex++) {
                        this.putIndices(new int[]{lineStripVertex, lineStripVertex + 1});
                    }

                    break;
                case 5:
                    int stripQuadCount = shapeVertexCount / 2 - 1;
                    this.ensureIndexCapacity(stripQuadCount * 6 * 4);

                    for (int stripQuad = 0; stripQuad < stripQuadCount; stripQuad++) {
                        int stripVertex = stripQuad * 2;
                        this.putIndices(new int[]{stripVertex, stripVertex + 1, stripVertex + 3, stripVertex + 3, stripVertex + 2, stripVertex});
                    }

                    break;
                case 6:
                    this.ensureIndexCapacity(shapeVertexCount / 2 * 2 * 4);

                    for (int lineStart = 0; lineStart < shapeVertexCount; lineStart += 2) {
                        this.putIndices(new int[]{lineStart, lineStart + 1});
                    }

                    break;
                default:
                    throw new MatchException(null, null);
            }

            return this;
        }
    }

    MeshBuilder setBaseVertex(int firstVertex);

    default MeshBuilder beginShape() {
        return this.setBaseVertex(this.getVertexCount());
    }
}

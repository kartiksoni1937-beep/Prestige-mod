package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.VertexAttributeBinding;
import dev.zprestige.prestige.data.VertexElement;
import java.util.Arrays;
import java.util.Objects;

public class VertexLayout {
    public final VertexElement[] elements;
    public final int stride;
    private final int[] offsets;
    private static final String INVALID_INDEX_MESSAGE = "Invalid element index: ";

    public VertexLayout(VertexElement[] layoutElements) {
        this.elements = layoutElements;
        this.offsets = new int[layoutElements.length];
        if (0 < layoutElements.length) {
            int offset = 0;
            for (int i = 0; i < layoutElements.length; i++) {
                this.offsets[i] = offset;
                offset += layoutElements[i].componentCount() * layoutElements[i].componentSize();
            }

            this.stride = offset;
        } else {
            this.stride = 0;
        }
    }

    public VertexAttributeBinding createBinding(GlBuffer buffer, int index) {
        if (index >= 0 && index < this.elements.length) {
            VertexElement element = this.elements[index];
            int offset = this.offsets[index];
            return new VertexAttributeBinding(buffer, element, this.stride, offset);
        } else {
            throw new IllegalArgumentException(INVALID_INDEX_MESSAGE + index);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            VertexLayout that = (VertexLayout)other;
            return this.stride == that.stride && Arrays.equals((Object[])this.elements, (Object[])that.elements) && Arrays.equals(this.offsets, that.offsets);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Objects.hash(this.stride) + Arrays.hashCode((Object[])this.elements)) + Arrays.hashCode(this.offsets);
    }
}
